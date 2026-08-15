import Foundation
import SwiftData

final class SessionDetector {
    private var activeChargingSession: ChargingSession?
    private var activeDrivingSession: DrivingSession?
    private var lastDrivingPollTime: Date?

    private let modelContext: ModelContext
    private let electricityRate: Double
    private let batteryCapacityKwh: Double

    init(modelContext: ModelContext, electricityRate: Double, batteryCapacityKwh: Double) {
        self.modelContext = modelContext
        self.electricityRate = electricityRate
        self.batteryCapacityKwh = batteryCapacityKwh
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
            hvacOn: status.isClimateOn
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
            let socDelta = Double(max(0, session.endSoc - session.startSoc))
            session.energyKwh = socDelta * batteryCapacityKwh / 100.0
            session.durationMinutes = Int(timestamp.timeIntervalSince(session.startTime) / 60)
            session.estimatedCostKrw = session.energyKwh * electricityRate
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
                lastDrivingPollTime = timestamp
            } else {
                // 폴링마다 순간 전력 × 시간(h) 적분으로 에너지 누적
                if let lastTime = lastDrivingPollTime {
                    let intervalHours = timestamp.timeIntervalSince(lastTime) / 3600.0
                    let powerKw = abs(status.instantPowerKw)
                    if powerKw > 0 {
                        activeDrivingSession?.energyKwh += powerKw * intervalHours
                    }
                }
                lastDrivingPollTime = timestamp
            }
            activeDrivingSession?.endSoc = status.batteryPercentage
        } else if let session = activeDrivingSession {
            let duration = timestamp.timeIntervalSince(session.startTime)
            // 2분 미만이면 의미 없는 세션으로 간주하고 삭제
            guard duration >= 120 else {
                modelContext.delete(session)
                activeDrivingSession = nil
                lastDrivingPollTime = nil
                return
            }

            session.endTime = timestamp

            // 누적 에너지가 없으면 SOC 기반으로 폴백
            if session.energyKwh == 0 {
                let socDelta = Double(max(0, session.startSoc - session.endSoc))
                session.energyKwh = socDelta * batteryCapacityKwh / 100.0
            }

            // 종료 ODO - 시작 ODO 로 주행 거리 계산
            if status.totalMileage > 0 { session.endOdometer = status.totalMileage }
            if let startOdo = session.startOdometer,
               let endOdo = session.endOdometer,
               endOdo > startOdo {
                let dist = endOdo - startOdo
                session.distanceKm = dist
                if session.energyKwh > 0 { session.efficiencyKmPerKwh = dist / session.energyKwh }
            }
            activeDrivingSession = nil
            lastDrivingPollTime = nil
        }
    }
}
