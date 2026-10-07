import Foundation
import SwiftData

final class SessionDetector {
    private var activeChargingSession: ChargingSession?
    private var activeDrivingSession: DrivingSession?

    private let modelContext: ModelContext
    private let getRateAt: (Date) -> Double
    private let batteryCapacityKwh: Double
    private let locationTracker: LocationTracker?

    init(modelContext: ModelContext, getRateAt: @escaping (Date) -> Double, batteryCapacityKwh: Double, gpsEnabled: Bool = true) {
        self.modelContext = modelContext
        self.getRateAt = getRateAt
        self.batteryCapacityKwh = batteryCapacityKwh
        self.locationTracker = gpsEnabled ? LocationTracker() : nil
        recoverOrphanSessions()
    }

    /// 앱 재시작 시 endTime == nil인 미완료 세션을 복원하거나 강제 종료
    private func recoverOrphanSessions() {
        let now = Date()
        // 주행은 최대 12시간, 충전은 최대 24시간까지 복원
        // (1시간 기준은 장거리 주행/완속 충전 세션을 BGAppRefreshTask가 force-end하는 버그를 유발)
        let maxDrivingDuration: TimeInterval  = 12 * 3600
        let maxChargingDuration: TimeInterval = 24 * 3600

        // 주행 세션
        let drivingDesc = FetchDescriptor<DrivingSession>(
            predicate: #Predicate { $0.endTime == nil }
        )
        if let orphans = try? modelContext.fetch(drivingDesc) {
            for session in orphans {
                if now.timeIntervalSince(session.startTime) < maxDrivingDuration {
                    activeDrivingSession = session   // 최근 세션: 계속 추적
                    LogManager.log("recover: driving session restored startSoc=\(session.startSoc) elapsed=\(Int(now.timeIntervalSince(session.startTime)/60))min", tag: "SessionDetector")
                } else {
                    session.endTime = now            // 오래된 세션: 지금 시각으로 강제 종료
                    LogManager.log("recover: driving session force-ended startSoc=\(session.startSoc) elapsed=\(Int(now.timeIntervalSince(session.startTime)/60))min", tag: "SessionDetector")
                }
            }
        }

        // 충전 세션
        let chargingDesc = FetchDescriptor<ChargingSession>(
            predicate: #Predicate { $0.endTime == nil }
        )
        if let orphans = try? modelContext.fetch(chargingDesc) {
            for session in orphans {
                if now.timeIntervalSince(session.startTime) < maxChargingDuration {
                    activeChargingSession = session
                    LogManager.log("recover: charging session restored startSoc=\(session.startSoc) elapsed=\(Int(now.timeIntervalSince(session.startTime)/60))min", tag: "SessionDetector")
                } else {
                    session.endTime = now
                    LogManager.log("recover: charging session force-ended startSoc=\(session.startSoc) elapsed=\(Int(now.timeIntervalSince(session.startTime)/60))min", tag: "SessionDetector")
                }
            }
        }
    }

    func process(status: VehicleStatus, at timestamp: Date) {
        // 데이터 포인트 저장
        let point = DataPoint(
            timestamp: timestamp,
            batteryPercent: status.batteryPercentage,
            isCharging: status.isCharging,
            isDriving: status.isDriving,
            chargingPowerKw: status.isCharging ? status.instantPowerKw : nil,
            hvacOn: status.isClimateOn,
            drivingRangeKm: status.drivingRange > 0 ? status.drivingRange : nil
        )
        modelContext.insert(point)

        handleChargingSession(status: status, timestamp: timestamp)
        handleDrivingSession(status: status, timestamp: timestamp)
    }

    private func handleChargingSession(status: VehicleStatus, timestamp: Date) {
        if status.isCharging {
            if activeChargingSession == nil {
                let session = ChargingSession(startTime: timestamp, startSoc: status.batteryPercentage)
                modelContext.insert(session)
                activeChargingSession = session
                LogManager.log("charging session started: startSoc=\(status.batteryPercentage)", tag: "SessionDetector")
            }
            activeChargingSession?.endSoc = status.batteryPercentage
        } else if let session = activeChargingSession {
            // BGAppRefresh 시점이 아닌 마지막 실제 isCharging=true DataPoint의 시간/SOC로 종료
            // (앱 suspended 중 충전이 끝나면 recover 후 현재 시간으로 잘못 기록되는 버그 방지)
            let lastChargingDp = lastChargingPoint(after: session.startTime)
            let actualEndTime = lastChargingDp?.timestamp ?? timestamp
            let actualEndSoc  = lastChargingDp?.batteryPercent ?? status.batteryPercentage

            session.endTime = actualEndTime
            session.endSoc = actualEndSoc
            let socDelta = Double(max(0, session.endSoc - session.startSoc))
            session.energyKwh = socDelta * batteryCapacityKwh / 100.0
            session.durationMinutes = Int(actualEndTime.timeIntervalSince(session.startTime) / 60)
            // 충전 시작 시간 기준 시간대 요금 적용
            session.estimatedCostKrw = session.energyKwh * getRateAt(session.startTime)
            LogManager.log("charging session ended: startSoc=\(session.startSoc) endSoc=\(session.endSoc) energy=\(String(format: "%.2f", session.energyKwh))kWh duration=\(session.durationMinutes)min actualEnd=\(actualEndTime)", tag: "SessionDetector")
            activeChargingSession = nil
        }
    }

    private func handleDrivingSession(status: VehicleStatus, timestamp: Date) {
        LogManager.log("handleDriving: isDriving=\(status.isDriving) activeSession=\(activeDrivingSession != nil) odo=\(status.totalMileage)", tag: "SessionDetector")
        if status.isDriving {
            if activeDrivingSession == nil {
                let session = DrivingSession(startTime: timestamp, startSoc: status.batteryPercentage)
                if status.totalMileage > 0 { session.startOdometer = status.totalMileage }
                modelContext.insert(session)
                activeDrivingSession = session
                locationTracker?.startTracking()
                LogManager.log("driving session started: startSoc=\(status.batteryPercentage) odo=\(status.totalMileage)", tag: "SessionDetector")
            }
            activeDrivingSession?.endSoc = status.batteryPercentage
        } else if let session = activeDrivingSession {
            let gpsDistanceKm = locationTracker?.stopTracking() ?? 0

            // BGAppRefresh 시점이 아닌 마지막 실제 isDriving=true DataPoint의 시간/SOC로 종료
            // (앱 suspended 중 주행이 끝나면 recover 후 현재 시간으로 잘못 기록되는 버그 방지)
            let lastDrivingDp = lastDrivingPoint(after: session.startTime)
            let actualEndTime = lastDrivingDp?.timestamp ?? timestamp
            let actualEndSoc  = lastDrivingDp?.batteryPercent ?? status.batteryPercentage

            let duration = actualEndTime.timeIntervalSince(session.startTime)
            LogManager.log("driving ended: durationMs=\(Int(duration * 1000)) gpsKm=\(String(format: "%.2f", gpsDistanceKm)) odo=\(status.totalMileage) actualEnd=\(actualEndTime)", tag: "SessionDetector")

            // 2분 미만이면 의미 없는 세션으로 간주하고 삭제
            guard duration >= 120 else {
                LogManager.log("driving session discarded: too short (\(Int(duration * 1000))ms < 120s)", tag: "SessionDetector")
                modelContext.delete(session)
                activeDrivingSession = nil
                return
            }

            session.endTime = actualEndTime
            session.endSoc  = actualEndSoc

            // SOC 기반 소비 계산
            let socDelta = Double(max(0, session.startSoc - session.endSoc))
            session.energyKwh = socDelta * batteryCapacityKwh / 100.0

            // 1순위: ODO(Energy API) 기반 거리
            if status.totalMileage > 0 { session.endOdometer = status.totalMileage }
            if let startOdo = session.startOdometer,
               let endOdo = session.endOdometer,
               endOdo > startOdo {
                let dist = endOdo - startOdo
                session.distanceKm = dist
                if session.energyKwh > 0 { session.efficiencyKmPerKwh = dist / session.energyKwh }
            }

            // 2순위: GPS 거리 (ODO 없을 때)
            if session.distanceKm == nil && gpsDistanceKm > 0.1 {
                session.distanceKm = gpsDistanceKm
                if session.energyKwh > 0 { session.efficiencyKmPerKwh = gpsDistanceKm / session.energyKwh }
            }

            LogManager.log("driving session ended: startSoc=\(session.startSoc) endSoc=\(session.endSoc) energy=\(String(format: "%.2f", session.energyKwh))kWh dist=\(session.distanceKm.map { String(format: "%.1f", $0) } ?? "nil")km", tag: "SessionDetector")
            activeDrivingSession = nil
        }
    }

    /// 세션 시작 이후의 마지막 isDriving=true DataPoint를 조회 (endTime 보정용)
    private func lastDrivingPoint(after startTime: Date) -> DataPoint? {
        var descriptor = FetchDescriptor<DataPoint>(
            predicate: #Predicate { $0.isDriving && $0.timestamp >= startTime },
            sortBy: [SortDescriptor(\.timestamp, order: .reverse)]
        )
        descriptor.fetchLimit = 1
        return (try? modelContext.fetch(descriptor))?.first
    }

    /// 세션 시작 이후의 마지막 isCharging=true DataPoint를 조회 (endTime 보정용)
    private func lastChargingPoint(after startTime: Date) -> DataPoint? {
        var descriptor = FetchDescriptor<DataPoint>(
            predicate: #Predicate { $0.isCharging && $0.timestamp >= startTime },
            sortBy: [SortDescriptor(\.timestamp, order: .reverse)]
        )
        descriptor.fetchLimit = 1
        return (try? modelContext.fetch(descriptor))?.first
    }
}
