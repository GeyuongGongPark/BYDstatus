import Foundation
import SwiftData
import UniformTypeIdentifiers

// MARK: - 공통 JSON 스키마 (Android 호환)

struct ExportPayload: Codable {
    let version: Int
    let exportedAt: Int64
    let dataPoints: [ExDataPoint]
    let chargingSessions: [ExChargingSession]
    let drivingSessions: [ExDrivingSession]
}

struct ExDataPoint: Codable {
    let timestamp: Int64
    let batteryPercent: Int
    let isCharging: Bool
    let isDriving: Bool
    let chargingPowerKw: Double?
    let hvacOn: Bool
    let drivingRangeKm: Double?
}

struct ExChargingSession: Codable {
    let startTime: Int64
    let endTime: Int64?
    let startSoc: Int
    let endSoc: Int
    let energyKwh: Double
    let durationMinutes: Int
    let estimatedCostKrw: Double
    let latitude: Double?
    let longitude: Double?
}

struct ExDrivingSession: Codable {
    let startTime: Int64
    let endTime: Int64?
    let startSoc: Int
    let endSoc: Int
    let energyKwh: Double
    let distanceKm: Double?
    let efficiencyKmPerKwh: Double?
    let startOdometer: Double?
    let endOdometer: Double?
}

struct ImportResult {
    let dataPoints: Int
    let chargingSessions: Int
    let drivingSessions: Int
}

// MARK: - FileDocument (fileExporter용)

struct BydStatsExportDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    let data: Data

    init(data: Data) { self.data = data }
    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

// MARK: - DataExporter

enum DataExporter {

    static func export(context: ModelContext) throws -> Data {
        let dataPoints = try context.fetch(FetchDescriptor<DataPoint>())
        let charging   = try context.fetch(FetchDescriptor<ChargingSession>())
        let driving    = try context.fetch(FetchDescriptor<DrivingSession>())

        let payload = ExportPayload(
            version: 1,
            exportedAt: Int64(Date().timeIntervalSince1970 * 1000),
            dataPoints: dataPoints.map {
                ExDataPoint(
                    timestamp:      Int64($0.timestamp.timeIntervalSince1970 * 1000),
                    batteryPercent: $0.batteryPercent,
                    isCharging:     $0.isCharging,
                    isDriving:      $0.isDriving,
                    chargingPowerKw: $0.chargingPowerKw,
                    hvacOn:         $0.hvacOn,
                    drivingRangeKm: $0.drivingRangeKm
                )
            },
            chargingSessions: charging.map {
                ExChargingSession(
                    startTime:        Int64($0.startTime.timeIntervalSince1970 * 1000),
                    endTime:          $0.endTime.map { Int64($0.timeIntervalSince1970 * 1000) },
                    startSoc:         $0.startSoc,
                    endSoc:           $0.endSoc,
                    energyKwh:        $0.energyKwh,
                    durationMinutes:  $0.durationMinutes,
                    estimatedCostKrw: $0.estimatedCostKrw,
                    latitude:         $0.latitude,
                    longitude:        $0.longitude
                )
            },
            drivingSessions: driving.map {
                ExDrivingSession(
                    startTime:          Int64($0.startTime.timeIntervalSince1970 * 1000),
                    endTime:            $0.endTime.map { Int64($0.timeIntervalSince1970 * 1000) },
                    startSoc:           $0.startSoc,
                    endSoc:             $0.endSoc,
                    energyKwh:          $0.energyKwh,
                    distanceKm:         $0.distanceKm,
                    efficiencyKmPerKwh: $0.efficiencyKmPerKwh,
                    startOdometer:      $0.startOdometer,
                    endOdometer:        $0.endOdometer
                )
            }
        )

        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return try encoder.encode(payload)
    }

    @discardableResult
    static func importData(from data: Data, context: ModelContext) throws -> ImportResult {
        let payload = try JSONDecoder().decode(ExportPayload.self, from: data)

        let existingDP = Set(
            (try context.fetch(FetchDescriptor<DataPoint>()))
                .map { Int64($0.timestamp.timeIntervalSince1970 * 1000) }
        )
        let existingCS = Set(
            (try context.fetch(FetchDescriptor<ChargingSession>()))
                .map { Int64($0.startTime.timeIntervalSince1970 * 1000) }
        )
        let existingDS = Set(
            (try context.fetch(FetchDescriptor<DrivingSession>()))
                .map { Int64($0.startTime.timeIntervalSince1970 * 1000) }
        )

        var dpCount = 0, csCount = 0, dsCount = 0

        for dp in payload.dataPoints {
            guard !existingDP.contains(dp.timestamp) else { continue }
            context.insert(DataPoint(
                timestamp:      Date(timeIntervalSince1970: Double(dp.timestamp) / 1000),
                batteryPercent: dp.batteryPercent,
                isCharging:     dp.isCharging,
                isDriving:      dp.isDriving,
                chargingPowerKw: dp.chargingPowerKw,
                hvacOn:         dp.hvacOn,
                drivingRangeKm: dp.drivingRangeKm
            ))
            dpCount += 1
        }

        for cs in payload.chargingSessions {
            guard !existingCS.contains(cs.startTime) else { continue }
            let session = ChargingSession(
                startTime: Date(timeIntervalSince1970: Double(cs.startTime) / 1000),
                startSoc:  cs.startSoc
            )
            session.endTime          = cs.endTime.map { Date(timeIntervalSince1970: Double($0) / 1000) }
            session.endSoc           = cs.endSoc
            session.energyKwh        = cs.energyKwh
            session.durationMinutes  = cs.durationMinutes
            session.estimatedCostKrw = cs.estimatedCostKrw
            session.latitude         = cs.latitude
            session.longitude        = cs.longitude
            context.insert(session)
            csCount += 1
        }

        for ds in payload.drivingSessions {
            guard !existingDS.contains(ds.startTime) else { continue }
            let session = DrivingSession(
                startTime: Date(timeIntervalSince1970: Double(ds.startTime) / 1000),
                startSoc:  ds.startSoc
            )
            session.endTime            = ds.endTime.map { Date(timeIntervalSince1970: Double($0) / 1000) }
            session.endSoc             = ds.endSoc
            session.energyKwh          = ds.energyKwh
            session.distanceKm         = ds.distanceKm
            session.efficiencyKmPerKwh = ds.efficiencyKmPerKwh
            session.startOdometer      = ds.startOdometer
            session.endOdometer        = ds.endOdometer
            context.insert(session)
            dsCount += 1
        }

        try context.save()
        return ImportResult(dataPoints: dpCount, chargingSessions: csCount, drivingSessions: dsCount)
    }
}
