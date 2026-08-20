import Foundation

/// 서버에 device token을 등록/삭제하는 유틸리티
enum PushRegistrar {

    private static let serverURL = "https://bydstatus-production.up.railway.app"

    /// Info.plist의 PushAPIKey 값 (Secrets.xcconfig → $(PUSH_API_KEY))
    private static var apiKey: String {
        Bundle.main.object(forInfoDictionaryKey: "PushAPIKey") as? String ?? ""
    }

    static func register(tokenData: Data) {
        let token = tokenData.map { String(format: "%02x", $0) }.joined()
        #if DEBUG
        let sandbox = "1"
        #else
        let sandbox = "0"
        #endif
        send(method: "POST", path: "/api/register", body: ["token": token, "platform": "ios", "sandbox": sandbox])
    }

    static func unregister(tokenData: Data) {
        let token = tokenData.map { String(format: "%02x", $0) }.joined()
        send(method: "DELETE", path: "/api/unregister", body: ["token": token])
    }

    private static func send(method: String, path: String, body: [String: String]) {
        guard let url = URL(string: serverURL + path) else { return }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(apiKey)", forHTTPHeaderField: "Authorization")
        req.httpBody = try? JSONEncoder().encode(body)
        URLSession.shared.dataTask(with: req) { _, res, err in
            if let err {
                print("[PushRegistrar] network error: \(err.localizedDescription)")
            } else if let http = res as? HTTPURLResponse {
                print("[PushRegistrar] \(method) \(path) -> HTTP \(http.statusCode)")
            }
        }.resume()
    }
}
