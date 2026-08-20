import Foundation

/// 서버에 device token을 등록/삭제하는 유틸리티
enum PushRegistrar {

    private static let serverURL = "https://your-railway-url.railway.app" // 배포 후 교체
    private static let apiKey    = "YOUR_API_KEY"                          // 환경변수와 동일한 값

    static func register(tokenData: Data) {
        let token = tokenData.map { String(format: "%02x", $0) }.joined()
        send(method: "POST", path: "/api/register", body: ["token": token, "platform": "ios"])
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
                print("[PushRegistrar] error: \(err.localizedDescription)")
            } else if let http = res as? HTTPURLResponse, http.statusCode != 200 {
                print("[PushRegistrar] status \(http.statusCode)")
            }
        }.resume()
    }
}
