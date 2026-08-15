import Foundation

struct LogEntry: Identifiable, Sendable {
    let id = UUID()
    let timestamp: Date
    let tag: String
    let message: String

    private static let formatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MM-dd HH:mm:ss.SSS"
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

    nonisolated(unsafe) static let logFileURL: URL = {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir.appendingPathComponent("bydstats.log")
    }()

    private(set) var entries: [LogEntry] = []

    private init() {}

    func add(_ message: String, tag: String = "App") {
        let entry = LogEntry(timestamp: Date(), tag: tag, message: message)
        entries.append(entry)
        if entries.count > 500 { entries.removeFirst(entries.count - 500) }
        let line = entry.formatted + "\n"
        Task.detached(priority: .background) {
            guard let data = line.data(using: .utf8) else { return }
            let url = LogManager.logFileURL
            if FileManager.default.fileExists(atPath: url.path),
               let handle = try? FileHandle(forWritingTo: url) {
                handle.seekToEndOfFile()
                handle.write(data)
                try? handle.close()
            } else {
                try? data.write(to: url)
            }
        }
    }

    func clear() {
        entries.removeAll()
        try? FileManager.default.removeItem(at: Self.logFileURL)
    }
}
