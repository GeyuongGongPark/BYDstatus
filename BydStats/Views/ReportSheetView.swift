import SwiftUI

private let portalURL = "https://geyuonggongpark-production.up.railway.app/api/reports"

private let carOptions = ["BYD Atto 3", "BYD Seal", "BYD Dolphin", "BYD Sealion 7", "기타"]

struct ReportSheetView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(LogManager.self) private var logManager

    @State private var title = ""
    @State private var car = ""
    @State private var bodyText = ""
    @State private var isSubmitting = false
    @State private var submitError: String?
    @State private var didSubmit = false

    private let appVersion: String = {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? ""
        return "\(v) (\(b))"
    }()

    var body: some View {
        NavigationStack {
            Form {
                Section("제보 정보") {
                    TextField("제목 (필수)", text: $title)

                    Picker("차종 (필수)", selection: $car) {
                        Text("선택하세요").tag("")
                        ForEach(carOptions, id: \.self) { option in
                            Text(option).tag(option)
                        }
                    }
                }

                Section {
                    TextEditor(text: $bodyText)
                        .frame(minHeight: 120)
                        .font(.system(size: 13))
                } header: {
                    Text("본문")
                } footer: {
                    Text("최근 로그가 자동으로 첨부됩니다.")
                        .font(.caption)
                }

                if let error = submitError {
                    Section {
                        Text(error)
                            .foregroundStyle(.red)
                            .font(.caption)
                    }
                }
            }
            .navigationTitle("버그 제보")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("제출") {
                        Task { await submit() }
                    }
                    .disabled(title.isEmpty || car.isEmpty || isSubmitting)
                    .overlay {
                        if isSubmitting {
                            ProgressView().scaleEffect(0.8)
                        }
                    }
                }
            }
            .onAppear { prefillBody() }
            .alert("제보 완료", isPresented: $didSubmit) {
                Button("확인") { dismiss() }
            } message: {
                Text("제보해 주셔서 감사합니다!")
            }
        }
    }

    private func prefillBody() {
        let recentLogs = logManager.entries.suffix(80)
            .map(\.formatted)
            .joined(separator: "\n")
        bodyText = "앱 버전: \(appVersion)\n플랫폼: iOS\n\n--- 최근 로그 ---\n\(recentLogs)"
    }

    private func submit() async {
        isSubmitting = true
        submitError = nil
        defer { isSubmitting = false }

        var fileData: String? = nil
        let logURL = LogManager.logFileURL
        if FileManager.default.fileExists(atPath: logURL.path),
           let raw = try? Data(contentsOf: logURL) {
            fileData = "data:text/plain;base64," + raw.base64EncodedString()
        }

        let payload: [String: Any?] = [
            "title":     title.trimmingCharacters(in: .whitespaces),
            "app":       "BYD Status",
            "platform":  "iOS",
            "car":       car,
            "body":      bodyText.trimmingCharacters(in: .whitespaces).isEmpty ? "-" : bodyText.trimmingCharacters(in: .whitespaces),
            "file_name": fileData != nil ? "bydstats.log" : nil,
            "file_type": fileData != nil ? "text/plain" : nil,
            "file_data": fileData,
        ]

        guard let url = URL(string: portalURL) else { return }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let jsonBody = payload.compactMapValues { $0 }
        guard let httpBody = try? JSONSerialization.data(withJSONObject: jsonBody) else { return }
        req.httpBody = httpBody

        do {
            let (_, response) = try await URLSession.shared.data(for: req)
            if let http = response as? HTTPURLResponse, http.statusCode == 201 {
                didSubmit = true
            } else {
                submitError = "제출에 실패했습니다. 잠시 후 다시 시도해주세요."
            }
        } catch {
            submitError = "네트워크 오류: \(error.localizedDescription)"
        }
    }
}
