import Foundation
import Observation
import SwiftData

@MainActor
@Observable
final class AppState {

    // MARK: - 로그인 상태

    var isLoggedIn = false
    var isLoggingIn = false
    var loginError: String?

    // MARK: - 차량

    var vehicles: [VehicleListItem] = []
    var selectedVin: String?

    // MARK: - 서비스

    private(set) var service: BydVehicleService?

    // MARK: - 폴링

    var currentStatus: VehicleStatus?
    var isPolling = false
    var pollError: String?

    private var pollingTask: Task<Void, Never>?
    private var sessionDetector: SessionDetector?

    // MARK: - Keychain 키

    private enum Keys {
        static let userId    = "byd.userId"
        static let signToken = "byd.signToken"
        static let encryToken = "byd.encryToken"
        static let username  = "byd.username"
        static let password  = "byd.password"
        static let vin       = "byd.vin"
        static let region    = "byd.region"
    }

    // MARK: - Init

    init() {
        restoreSession()
    }

    // MARK: - 세션 복원

    private func restoreSession() {
        let region = KeychainHelper.load(forKey: Keys.region) ?? "KR"
        guard let svc = try? BydVehicleService(config: BydConfig.fromRegion(region)),
              let uid   = KeychainHelper.load(forKey: Keys.userId),
              let sign  = KeychainHelper.load(forKey: Keys.signToken),
              let encry = KeychainHelper.load(forKey: Keys.encryToken),
              !uid.isEmpty, !sign.isEmpty else { return }

        let username = KeychainHelper.load(forKey: Keys.username) ?? ""
        let password = KeychainHelper.load(forKey: Keys.password) ?? ""

        Task {
            await svc.restoreSession(userId: uid, signToken: sign, encryToken: encry)
            await svc.setCredentials(username: username, password: password)
        }

        service = svc
        selectedVin = KeychainHelper.load(forKey: Keys.vin)
        isLoggedIn = true
    }

    // MARK: - 로그인

    func login(username: String, password: String, region: String) async {
        isLoggingIn = true
        loginError = nil

        do {
            let svc = try BydVehicleService(config: BydConfig.fromRegion(region))
            await svc.setCredentials(username: username, password: password)
            _ = try await svc.login(username: username, password: password)

            // 로그인 성공 — 발급된 토큰을 Keychain에 저장
            if let uid   = await svc.userId,
               let sign  = await svc.signToken,
               let encry = await svc.encryToken {
                KeychainHelper.save(uid,   forKey: Keys.userId)
                KeychainHelper.save(sign,  forKey: Keys.signToken)
                KeychainHelper.save(encry, forKey: Keys.encryToken)
            }
            KeychainHelper.save(username, forKey: Keys.username)
            KeychainHelper.save(password, forKey: Keys.password)
            KeychainHelper.save(region,   forKey: Keys.region)

            service = svc
            isLoggedIn = true

            // 차량 목록 조회
            vehicles = try await svc.fetchVehicleList()

            // 차량이 1대면 자동 선택
            if selectedVin == nil, let first = vehicles.first {
                selectVin(first.vin)
            }
        } catch {
            loginError = error.localizedDescription
        }

        isLoggingIn = false
    }

    // MARK: - 차량 선택

    func selectVin(_ vin: String) {
        selectedVin = vin
        KeychainHelper.save(vin, forKey: Keys.vin)
    }

    // MARK: - 로그아웃

    func logout() {
        stopPolling()
        service = nil
        isLoggedIn = false
        vehicles = []
        selectedVin = nil
        loginError = nil
        currentStatus = nil

        KeychainHelper.delete(forKey: Keys.userId)
        KeychainHelper.delete(forKey: Keys.signToken)
        KeychainHelper.delete(forKey: Keys.encryToken)
        KeychainHelper.delete(forKey: Keys.username)
        KeychainHelper.delete(forKey: Keys.password)
        KeychainHelper.delete(forKey: Keys.vin)
    }

    // MARK: - 폴링

    func startPolling(modelContext: ModelContext, pollingInterval: Int = 5,
                      electricityRate: Double, batteryCapacityKwh: Double) {
        guard let svc = service, let vin = selectedVin else { return }
        // 기존 폴링이 있으면 취소 후 재시작 (VIN 변경 등)
        pollingTask?.cancel()
        pollingTask = nil

        sessionDetector = SessionDetector(modelContext: modelContext,
                                          electricityRate: electricityRate,
                                          batteryCapacityKwh: batteryCapacityKwh)
        pollingTask = Task {
            // 시작 즉시 1회 폴링
            await doPoll(service: svc, vin: vin, modelContext: modelContext)
            while !Task.isCancelled {
                let interval = adaptiveInterval(defaultMinutes: pollingInterval)
                try? await Task.sleep(for: .seconds(interval))
                guard !Task.isCancelled else { break }
                await doPoll(service: svc, vin: vin, modelContext: modelContext)
            }
        }
    }

    private func adaptiveInterval(defaultMinutes: Int) -> TimeInterval {
        guard let status = currentStatus else { return TimeInterval(defaultMinutes * 60) }
        if status.isDriving  { return 60  }  // 주행 중: 1분
        if status.isCharging { return 120 }  // 충전 중: 2분
        return TimeInterval(defaultMinutes * 60)
    }

    func stopPolling() {
        pollingTask?.cancel()
        pollingTask = nil
        sessionDetector = nil
        isPolling = false
    }

    func pollNow(modelContext: ModelContext) async {
        guard let svc = service, let vin = selectedVin else { return }
        await doPoll(service: svc, vin: vin, modelContext: modelContext)
    }

    private func doPoll(service: BydVehicleService, vin: String, modelContext: ModelContext) async {
        isPolling = true
        do {
            let status = try await service.fetchVehicleStatus(vin: vin)
            currentStatus = status
            pollError = nil
            // 배터리 값이 0이면 API 파싱 실패로 간주 — 그래프/세션에 기록하지 않음
            if status.batteryPercentage > 0 {
                sessionDetector?.process(status: status, at: Date())
                try? modelContext.save()
            }
            saveWidgetSnapshot(status: status, modelContext: modelContext)
        } catch {
            pollError = error.localizedDescription
        }
        isPolling = false
    }

    private func saveWidgetSnapshot(status: VehicleStatus, modelContext: ModelContext) {
        // 이번 달 충전 비용 합산
        let cal = Calendar.current
        let descriptor = FetchDescriptor<ChargingSession>()
        let allSessions = (try? modelContext.fetch(descriptor)) ?? []
        let monthCost = allSessions
            .filter { cal.isDate($0.startTime, equalTo: Date(), toGranularity: .month) }
            .reduce(0) { $0 + $1.estimatedCostKrw }

        let snapshot = WidgetSnapshot(
            batteryPercent:  status.batteryPercentage,
            isCharging:      status.isCharging,
            isDriving:       status.isDriving,
            drivingRangeKm:  status.drivingRange,
            instantPowerKw:  status.instantPowerKw,
            monthCostKrw:    monthCost,
            lastUpdated:     Date()
        )
        WidgetDataStore.save(snapshot)
    }
}

