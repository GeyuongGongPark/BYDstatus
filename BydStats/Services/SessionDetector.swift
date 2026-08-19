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
        let oneHour: TimeInterval = 3600

        // 주행 세션
        let drivingDesc = FetchDescriptor<DrivingSession>(
            predicate: #Predicate { $0.endTime == nil }
        )
        if let orphans = try? modelContext.fetch(drivingDesc) {
            for session in orphans {
                if now.timeIntervalSince(session.startTime) < oneHour {
                    activeDrivingSession = session   // 최근 세션: 계속 추적
                } else {
                    session.endTime = now            // 오래된 세션: 지금 시각으로 강제 종료
                }
            }
        }

        // 충전 세션
        let chargingDesc = FetchDescriptor<ChargingSession>(
            predicate: #Predicate { $0.endTime == nil }
        )
        if let orphans = try? modelContext.fetch(chargingDesc) {
            for session in orphans {
                if now.timeIntervalSince(session.startTime) < oneHour {
                    activeChargingSession = session
                } else {
                    session.endTime = now
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
            }
            activeChargingSession?.endSoc = status.batteryPercentage
        } else if let session = activeChargingSession {
            session.endTime = timestamp
            session.endSoc = status.batteryPercentage
            let socDelta = Double(max(0, session.endSoc - session.startSoc))
            session.energyKwh = socDelta * batteryCapacityKwh / 100.0
            session.durationMinutes = Int(timestamp.timeIntervalSince(session.startTime) / 60)
            // 충전 시작 시간 기준 시간대 요금 적용
            session.estimatedCostKrw = session.energyKwh * getRateAt(session.startTime)
            activeChargingSession = nil
        }
    }

    private func handleDrivingSession(status: VehicleStatus, timestamp: Date) {
        if status.isDriving {
            if activeDrivingSession == nil {
                let session = DrivingSession(startTime: timestamp, startSoc: status.batteryPercentage)
                if status.totalMileage > 0 { session.startOdometer = status.totalMileage }
                modelContext.insert(session)
                activeDrivingSession = session
                locationTracker?.startTracking()
            }
            activeDrivingSession?.endSoc = status.batteryPercentage
        } else if let session = activeDrivingSession {
            let duration = timestamp.timeIntervalSince(session.startTime)
            // 2분 미만이면 의미 없는 세션으로 간주하고 삭제
            let gpsDistanceKm = locationTracker?.stopTracking() ?? 0

            guard duration >= 120 else {
                modelContext.delete(session)
                activeDrivingSession = nil
                return
            }

            session.endTime = timestamp

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

            activeDrivingSession = nil
        }
    }
}
