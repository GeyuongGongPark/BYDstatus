import Foundation
import SwiftData

final class SessionDetector {
    private var activeChargingSession: ChargingSession?
    private var activeDrivingSession: DrivingSession?

    private let modelContext: ModelContext
    private let electricityRate: Double
    private let batteryCapacityKwh: Double

    init(modelContext: ModelContext, electricityRate: Double, batteryCapacityKwh: Double) {
        self.modelContext = modelContext
        self.electricityRate = electricityRate
        self.batteryCapacityKwh = batteryCapacityKwh
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
                if status.totalMileage > 0 {
                    session.startOdometer = status.totalMileage
                }
                modelContext.insert(session)
                activeDrivingSession = session
            }
            activeDrivingSession?.endSoc = status.batteryPercentage
        } else if let session = activeDrivingSession {
            session.endTime = timestamp
            let socDelta = Double(max(0, session.startSoc - session.endSoc))
            session.energyKwh = socDelta * batteryCapacityKwh / 100.0

            // 종료 ODO - 시작 ODO 로 주행 거리 계산
            if status.totalMileage > 0 {
                session.endOdometer = status.totalMileage
            }
            if let startOdo = session.startOdometer,
               let endOdo = session.endOdometer,
               endOdo > startOdo {
                let dist = endOdo - startOdo
                session.distanceKm = dist
                if session.energyKwh > 0 {
                    session.efficiencyKmPerKwh = dist / session.energyKwh
                }
            }
            activeDrivingSession = nil
        }
    }
}
