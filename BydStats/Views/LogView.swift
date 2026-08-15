import SwiftUI
import UIKit

struct LogView: View {
    @Environment(LogManager.self) private var logManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Group {
                if logManager.entries.isEmpty {
                    ContentUnavailableView {
                        Label("로그 없음", systemImage: "doc.text")
                    } description: {
                        Text("새로고침을 실행하면 로그가 수집됩니다.")
                    }
                } else {
                    logList
                }
            }
            .navigationTitle("디버그 로그")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("닫기") { dismiss() }
                }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button { shareLog() } label: {
                        Image(systemName: "square.and.arrow.up")
                    }
                    Button(role: .destructive) {
                        logManager.clear()
                    } label: {
                        Image(systemName: "trash")
                    }
                }
            }
        }
    }

    private func shareLog() {
        let url = LogManager.logFileURL
        guard FileManager.default.fileExists(atPath: url.path) else { return }
        let vc = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
              let root = scene.windows.first?.rootViewController else { return }
        root.present(vc, animated: true)
    }

    private var logList: some View {
        ScrollViewReader { proxy in
            List(logManager.entries) { entry in
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.formatted)
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(rowColor(for: entry))
                        .textSelection(.enabled)
                }
                .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
                .id(entry.id)
            }
            .listStyle(.plain)
            .onChange(of: logManager.entries.count) { _, _ in
                if let last = logManager.entries.last {
                    withAnimation { proxy.scrollTo(last.id, anchor: .bottom) }
                }
            }
            .onAppear {
                if let last = logManager.entries.last {
                    proxy.scrollTo(last.id, anchor: .bottom)
                }
            }
        }
    }

    private func rowColor(for entry: LogEntry) -> Color {
        let msg = entry.message.lowercased()
        if msg.contains("오류") || msg.contains("error") || msg.contains("실패") { return .red }
        if msg.contains("만료") || msg.contains("재로그인") { return .orange }
        if msg.contains("처리 중") { return .secondary }
        return .primary
    }
}

