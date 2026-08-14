import Foundation

struct LogEntry: Identifiable, Sendable {
    let id = UUID()
    let timestamp: Date
    let tag: String
    let message: String

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "HH:mm:ss.SSS"
        return f
    }()

    var formatted: String {
        "[\(Self.formatter.string(from: timestamp))] [\(tag)] \(message)"
    }
}

@MainActor
@Observable
final class LogManager {
    static let shared = LogManager()
    private init() {}

    private(set) var entries: [LogEntry] = []
    var isEnabled: Bool = false

    func add(_ message: String, tag: String = "App") {
        guard isEnabled else { return }
        entries.append(LogEntry(timestamp: Date(), tag: tag, message: message))
        if entries.count > 500 {
            entries.removeFirst(entries.count - 500)
        }
    }

    func clear() {
        entries.removeAll()
    }

    var exportText: String {
        entries.map(\.formatted).joined(separator: "\n")
    }
}
