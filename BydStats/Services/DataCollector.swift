import Foundation
import SwiftData

@MainActor
final class DataCollector: ObservableObject {
    @Published var isRunning = false
    @Published var lastError: Error?
    @Published var currentStatus: VehicleStatus?

    private var pollingTask: Task<Void, Never>?
    private let modelContext: ModelContext
    private var sessionDetector: SessionDetector?

    let service: BydVehicleService
    var vin: String?

    init(modelContext: ModelContext, service: BydVehicleService) {
        self.modelContext = modelContext
        self.service = service
    }

    func start(vin: String, pollingIntervalMinutes: Int = 5, electricityRate: Double, batteryCapacityKwh: Double) {
        guard !isRunning else { return }
        self.vin = vin
        isRunning = true
        sessionDetector = SessionDetector(
            modelContext: modelContext,
            electricityRate: electricityRate,
            batteryCapacityKwh: batteryCapacityKwh
        )

        pollingTask = Task {
            while !Task.isCancelled {
                await poll()
                try? await Task.sleep(for: .seconds(pollingIntervalMinutes * 60))
            }
        }
    }

    func stop() {
        pollingTask?.cancel()
        pollingTask = nil
        isRunning = false
    }

    private func poll() async {
        guard let vin else { return }
        do {
            let status = try await service.fetchVehicleStatus(vin: vin)
            currentStatus = status
            sessionDetector?.process(status: status, at: Date())
            try? modelContext.save()
            lastError = nil
        } catch {
            lastError = error
        }
    }
}
