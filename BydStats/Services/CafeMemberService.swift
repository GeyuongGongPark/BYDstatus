import Foundation

private let cafeApiKey = "byd_210388bcac24d65d3146de0f8dde377480f6f02a1783e609"
private let cacheValidSeconds: TimeInterval = 86400 // 24시간

enum CafeMemberStatus: Equatable {
    case notChecked
    case verified(grade: String)
    case unqualified(grade: String)
}

private enum CacheKeys {
    static let nick        = "cafe.nick"
    static let grade       = "cafe.grade"
    static let qualified   = "cafe.isQualified"
    static let checkedAt   = "cafe.checkedAt"
}

struct CafeMemberService {

    // 저장된 닉네임 (지역ll닉네임ll차종)
    static var savedNick: String? {
        UserDefaults.standard.string(forKey: CacheKeys.nick)
    }

    // 캐시에서 현재 상태 복원 (24시간 이내면 재사용)
    static func cachedStatus() -> CafeMemberStatus? {
        let ud = UserDefaults.standard
        guard let checkedAt = ud.object(forKey: CacheKeys.checkedAt) as? Date,
              Date().timeIntervalSince(checkedAt) < cacheValidSeconds,
              let grade = ud.string(forKey: CacheKeys.grade) else { return nil }
        let qualified = ud.bool(forKey: CacheKeys.qualified)
        return qualified ? .verified(grade: grade) : .unqualified(grade: grade)
    }

    // 캐시 무효화 (닉네임 변경 시)
    static func clearCache() {
        let ud = UserDefaults.standard
        ud.removeObject(forKey: CacheKeys.grade)
        ud.removeObject(forKey: CacheKeys.qualified)
        ud.removeObject(forKey: CacheKeys.checkedAt)
    }

    // API 호출
    static func verify(region: String, nickname: String, carModel: String) async throws -> CafeMemberStatus {
        let nick = "\(region)ll\(nickname)ll\(carModel)"
        guard let encoded = nick.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed),
              let url = URL(string: "https://byd.cseini.co.kr/cafe/member?nick=\(encoded)") else {
            throw URLError(.badURL)
        }

        var req = URLRequest(url: url)
        req.setValue(cafeApiKey, forHTTPHeaderField: "X-Cafe-Key")

        let (data, response) = try await URLSession.shared.data(for: req)
        guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
            throw URLError(.badServerResponse)
        }

        let json = try JSONDecoder().decode(CafeMemberResponse.self, from: data)

        // 캐시 저장
        let ud = UserDefaults.standard
        ud.set(nick,               forKey: CacheKeys.nick)
        ud.set(json.grade,         forKey: CacheKeys.grade)
        ud.set(json.isRegularOrAbove, forKey: CacheKeys.qualified)
        ud.set(Date(),             forKey: CacheKeys.checkedAt)

        return json.isRegularOrAbove
            ? .verified(grade: json.grade)
            : .unqualified(grade: json.grade)
    }
}

private struct CafeMemberResponse: Decodable {
    let found: Bool
    let isRegularOrAbove: Bool
    let grade: String
    let checkedAt: Double
}
