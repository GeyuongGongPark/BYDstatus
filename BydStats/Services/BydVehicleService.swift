import Foundation

/// BYD 차량 상태 조회 API 클라이언트
/// BydAutoLock의 BydVehicleService를 BydStats용으로 포팅
actor BydVehicleService {

    private let config: BydConfig
    private let codec: BangcleCodec
    private let session: URLSession

    private(set) var userId: String?
    private(set) var signToken: String?
    private(set) var encryToken: String?
    private var accountImeiMD5 = "00000000000000000000000000000000"

    var onSessionUpdated: ((String, String, String) -> Void)?
    var onSessionExpired: (() -> Void)?

    private var storedUsername: String?
    private var storedPassword: String?
    private var isRelogging = false

    private let deviceProfile: [String: String] = [
        "ostype": "and",
        "imei": "BANGCLE01234",
        "mac": "00:00:00:00:00:00",
        "model": "POCO F1",
        "sdk": "35",
        "mod": "Xiaomi",
        "mobileBrand": "XIAOMI",
        "mobileModel": "POCO F1",
        "deviceType": "0",
        "networkType": "wifi",
        "osType": "15",
        "osVersion": "35",
        "appInnerVersion": "322",
        "appVersion": "3.2.2"
    ]

    var isLoggedIn: Bool { signToken != nil && !(signToken?.isEmpty ?? true) }

    init(config: BydConfig) throws {
        self.config = config
        self.codec = try BangcleCodec()
        let sessionConfig = URLSessionConfiguration.default
        sessionConfig.timeoutIntervalForRequest = 120
        sessionConfig.timeoutIntervalForResource = 120
        self.session = URLSession(configuration: sessionConfig)
    }

    func setCredentials(username: String, password: String) {
        storedUsername = username
        storedPassword = password
        if !username.isEmpty {
            accountImeiMD5 = CryptoUtils.md5Hex(username)
        }
    }

    func restoreSession(userId: String, signToken: String, encryToken: String) {
        self.userId = userId
        self.signToken = signToken
        self.encryToken = encryToken
    }

    // MARK: - JSON Helpers

    private func toSortedJSON(_ map: [(key: String, value: Any?)]) -> String {
        var parts = [String]()
        for (key, value) in map {
            let valStr: String
            if let v = value as? String {
                let escaped = v.replacingOccurrences(of: "\\", with: "\\\\")
                              .replacingOccurrences(of: "\"", with: "\\\"")
                valStr = "\"\(escaped)\""
            } else if let v = value {
                valStr = "\(v)"
            } else {
                valStr = "null"
            }
            parts.append("\"\(key)\":\(valStr)")
        }
        return "{\(parts.joined(separator: ","))}"
    }

    private func buildInnerBase(vin: String? = nil, requestSerial: String? = nil) -> [(key: String, value: Any?)] {
        var map: [(key: String, value: Any?)] = [
            ("deviceType",    deviceProfile["deviceType"] ?? ""),
            ("imeiMD5",       accountImeiMD5),
            ("networkType",   deviceProfile["networkType"] ?? ""),
            ("random",        String(CryptoUtils.md5Hex("\(Double.random(in: 0...1))").prefix(16))),
            ("timeStamp",     "\(Int64(Date().timeIntervalSince1970 * 1000))"),
            ("version",       deviceProfile["appInnerVersion"] ?? "")
        ]
        if let v = vin           { map.append(("vin", v)) }
        if let r = requestSerial { map.append(("requestSerial", r)) }
        return map
    }

    // MARK: - Authenticated Request

    private func postTokenSecure(endpoint: String, innerMap: [(key: String, value: Any?)], vin: String?) async throws -> [String: Any] {
        guard let uid = userId, let signTok = signToken, let encTok = encryToken else {
            throw BydError.notLoggedIn
        }

        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let reqTimestamp = "\(nowMs)"
        let innerJson = toSortedJSON(innerMap)
        let encryData = try CryptoUtils.aesEncryptHex(innerJson, keyHex: CryptoUtils.md5Hex(encTok))

        var signFields = [String: String]()
        for (k, v) in innerMap { signFields[k] = "\(v ?? "null")" }
        signFields["countryCode"]  = config.countryCode
        signFields["identifier"]   = uid
        signFields["imeiMD5"]      = accountImeiMD5
        signFields["language"]     = config.language
        signFields["reqTimestamp"] = reqTimestamp
        let sign = CryptoUtils.sha1Mixed(
            CryptoUtils.buildSignString(signFields, password: CryptoUtils.md5Hex(signTok))
        )

        var outerMap: [(key: String, value: Any?)] = [
            ("countryCode",  config.countryCode),
            ("encryData",    encryData),
            ("identifier",   uid),
            ("imeiMD5",      accountImeiMD5),
            ("language",     config.language),
            ("reqTimestamp", reqTimestamp),
            ("sign",         sign),
            ("ostype",       deviceProfile["ostype"]),
            ("imei",         deviceProfile["imei"]),
            ("mac",          deviceProfile["mac"]),
            ("model",        deviceProfile["model"]),
            ("sdk",          deviceProfile["sdk"]),
            ("mod",          deviceProfile["mod"]),
            ("serviceTime",  reqTimestamp)
        ]
        let outerJsonNoCheck = toSortedJSON(outerMap)
        let checkcode = CryptoUtils.computeCheckcode(outerJsonNoCheck)
        outerMap.append(("checkcode", checkcode))
        let finalOuterJson = toSortedJSON(outerMap)

        let encodedRequest = try codec.encodeEnvelope(finalOuterJson)
        let body = try JSONSerialization.data(withJSONObject: ["request": encodedRequest])
        guard let url = URL(string: config.baseURL + endpoint) else { throw BydError.invalidResponse }

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        req.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        req.setValue("okhttp/4.12.0", forHTTPHeaderField: "User-Agent")
        req.httpBody = body

        let (data, _) = try await session.data(for: req)
        guard let bodyJson = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let encodedResponse = bodyJson["response"] as? String else {
            throw BydError.invalidResponse
        }
        var decoded = try codec.decodeEnvelope(encodedResponse).trimmingCharacters(in: .whitespaces)
        if decoded.hasPrefix("F{") || decoded.hasPrefix("F[") { decoded = String(decoded.dropFirst()) }

        guard let decodedData = decoded.data(using: .utf8),
              let outerResp = try JSONSerialization.jsonObject(with: decodedData) as? [String: Any] else {
            throw BydError.invalidResponse
        }
        let resCode = outerResp["code"] as? String ?? "0"
        print("[BydAPI] \(endpoint) → code=\(resCode) msg=\(outerResp["message"] ?? "-")")

        if resCode != "0" {
            if ["1002", "1005", "1010"].contains(resCode) {
                print("[BydAPI] 세션 만료 코드 \(resCode), 재로그인 시도")
                return try await silentReLogin(endpoint: endpoint, innerMap: innerMap, vin: vin)
            }
            throw BydError.serverError(outerResp["message"] as? String ?? "Unknown", resCode)
        }

        let respondData = outerResp["respondData"] as? String ?? ""
        if respondData.isEmpty { return outerResp }

        let innerText: String
        do {
            innerText = try CryptoUtils.aesDecryptUTF8(respondData, keyHex: CryptoUtils.md5Hex(encTok))
        } catch {
            print("[BydAPI] 응답 복호화 실패 — 재로그인 후 재시도")
            return try await silentReLogin(endpoint: endpoint, innerMap: innerMap, vin: vin)
        }
        guard let innerData = innerText.data(using: .utf8) else { throw BydError.invalidResponse }
        if innerText.hasPrefix("[") {
            guard let arr = try JSONSerialization.jsonObject(with: innerData) as? [[String: Any]] else {
                throw BydError.invalidResponse
            }
            return ["list": arr]
        }
        guard let result = try JSONSerialization.jsonObject(with: innerData) as? [String: Any] else {
            throw BydError.invalidResponse
        }
        return result
    }

    private func silentReLogin(endpoint: String, innerMap: [(key: String, value: Any?)], vin: String?) async throws -> [String: Any] {
        guard !isRelogging else { throw BydError.sessionExpired }
        guard let user = storedUsername, let pwd = storedPassword, !user.isEmpty else {
            onSessionExpired?()
            throw BydError.sessionExpired
        }
        isRelogging = true
        defer { isRelogging = false }
        _ = try await login(username: user, password: pwd)
        return try await postTokenSecure(endpoint: endpoint, innerMap: innerMap, vin: vin)
    }

    // MARK: - Login

    func login(username: String, password: String) async throws -> String {
        let derivedImeiMD5 = CryptoUtils.md5Hex(username)
        accountImeiMD5 = derivedImeiMD5
        let nowMs = Int64(Date().timeIntervalSince1970 * 1000)
        let reqTimestamp = "\(nowMs)"
        let randomHex = String(CryptoUtils.md5Hex("\(Double.random(in: 0...1))").prefix(32))

        let innerMap: [(key: String, value: Any?)] = [
            ("agreeStatus",     "0"),
            ("agreementType",   "[1,2]"),
            ("appInnerVersion", deviceProfile["appInnerVersion"] ?? ""),
            ("appVersion",      deviceProfile["appVersion"] ?? ""),
            ("deviceName",      "\(deviceProfile["mobileBrand"] ?? "")\(deviceProfile["mobileModel"] ?? "")"),
            ("deviceType",      deviceProfile["deviceType"] ?? ""),
            ("imeiMD5",         derivedImeiMD5),
            ("isAuto",          "1"),
            ("mobileBrand",     deviceProfile["mobileBrand"] ?? ""),
            ("mobileModel",     deviceProfile["mobileModel"] ?? ""),
            ("networkType",     deviceProfile["networkType"] ?? ""),
            ("osType",          deviceProfile["osType"] ?? ""),
            ("osVersion",       deviceProfile["osVersion"] ?? ""),
            ("random",          randomHex),
            ("softType",        "0"),
            ("timeStamp",       reqTimestamp),
            ("timeZone",        config.timeZone)
        ]

        let innerJson = toSortedJSON(innerMap)
        let loginKey = CryptoUtils.pwdLoginKey(password)
        let encryData = try CryptoUtils.aesEncryptHex(innerJson, keyHex: loginKey)

        var signFields = [String: String]()
        for (k, v) in innerMap { signFields[k] = "\(v ?? "null")" }
        signFields["appName"]        = "pyBYD+0.1.dev2+ge0a1f5e27"
        signFields["countryCode"]    = config.countryCode
        signFields["functionType"]   = "pwdLogin"
        signFields["identifier"]     = username
        signFields["identifierType"] = "0"
        signFields["language"]       = config.language
        signFields["reqTimestamp"]   = reqTimestamp
        let sign = CryptoUtils.sha1Mixed(
            CryptoUtils.buildSignString(signFields, password: CryptoUtils.md5Hex(password))
        )

        var outerMap: [(key: String, value: Any?)] = [
            ("appName",        "pyBYD+0.1.dev2+ge0a1f5e27"),
            ("countryCode",    config.countryCode),
            ("encryData",      encryData),
            ("functionType",   "pwdLogin"),
            ("identifier",     username),
            ("identifierType", "0"),
            ("imeiMD5",        derivedImeiMD5),
            ("isAuto",         "1"),
            ("language",       config.language),
            ("reqTimestamp",   reqTimestamp),
            ("sign",           sign),
            ("signKey",        password),
            ("ostype",         deviceProfile["ostype"]),
            ("imei",           deviceProfile["imei"]),
            ("mac",            deviceProfile["mac"]),
            ("model",          deviceProfile["model"]),
            ("sdk",            deviceProfile["sdk"]),
            ("mod",            deviceProfile["mod"]),
            ("serviceTime",    reqTimestamp)
        ]
        let outerJsonNoCheck = toSortedJSON(outerMap)
        let checkcode = CryptoUtils.computeCheckcode(outerJsonNoCheck)
        outerMap.append(("checkcode", checkcode))
        let finalOuterJson = toSortedJSON(outerMap)

        let encodedRequest = try codec.encodeEnvelope(finalOuterJson)
        let body = try JSONSerialization.data(withJSONObject: ["request": encodedRequest])
        guard let url = URL(string: config.baseURL + "/app/account/login") else { throw BydError.invalidResponse }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
        request.setValue("identity", forHTTPHeaderField: "Accept-Encoding")
        request.setValue("okhttp/4.12.0", forHTTPHeaderField: "User-Agent")
        request.httpBody = body

        let (data, _) = try await session.data(for: request)
        guard let bodyJson = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let encodedResponse = bodyJson["response"] as? String else {
            throw BydError.invalidResponse
        }
        var decoded = try codec.decodeEnvelope(encodedResponse).trimmingCharacters(in: .whitespaces)
        if decoded.hasPrefix("F{") { decoded = String(decoded.dropFirst()) }

        guard let decodedData = decoded.data(using: .utf8),
              let outerResp = try JSONSerialization.jsonObject(with: decodedData) as? [String: Any] else {
            throw BydError.invalidResponse
        }
        let resCode = outerResp["code"] as? String ?? "0"
        guard resCode == "0" else {
            throw BydError.serverError(outerResp["message"] as? String ?? "Login failed", resCode)
        }

        guard let respondData = outerResp["respondData"] as? String else { throw BydError.invalidResponse }
        let innerText = try CryptoUtils.aesDecryptUTF8(respondData, keyHex: loginKey)
        guard let innerData = innerText.data(using: .utf8),
              let innerResp = try JSONSerialization.jsonObject(with: innerData) as? [String: Any],
              let token = innerResp["token"] as? [String: Any] else {
            throw BydError.invalidResponse
        }

        guard let uid   = token["userId"]    as? String,
              let sign  = token["signToken"]  as? String,
              let encry = token["encryToken"] as? String else {
            throw BydError.invalidResponse
        }
        userId     = uid
        signToken  = sign
        encryToken = encry

        storedUsername = username
        storedPassword = password

        onSessionUpdated?(uid, sign, encry)
        return uid
    }

    // MARK: - Vehicle List

    func fetchVehicleList() async throws -> [VehicleListItem] {
        let result = try await postTokenSecure(endpoint: "/app/account/getAllListByUserId",
                                               innerMap: buildInnerBase(), vin: nil)
        let list = result["list"] as? [[String: Any]] ?? []
        return list.compactMap { item -> VehicleListItem? in
            guard let vin = item["vin"] as? String else { return nil }
            let model = item["modelName"] as? String
                       ?? item["model"] as? String
                       ?? item["seriesName"] as? String
                       ?? "차량"
            return VehicleListItem(id: vin, vin: vin, modelName: model)
        }
    }

    // MARK: - Vehicle Realtime Status

    func fetchVehicleStatus(vin: String) async throws -> VehicleStatus {
        var inner = buildInnerBase(vin: vin)
        inner.append(("energyType", "0"))
        inner.append(("tboxVersion", "3"))

        let triggerResult = try await postTokenSecure(
            endpoint: "/vehicleInfo/vehicle/vehicleRealTimeRequest",
            innerMap: inner, vin: vin
        )
        let serial = triggerResult["requestSerial"] as? String
        print("[BydAPI] vehicleRealTimeRequest 응답: serial=\(serial ?? "nil") keys=\(triggerResult.keys.sorted())")

        guard let serial = serial, !serial.isEmpty else {
            let msg = triggerResult["message"] as? String ?? "차량 tbox 응답 없음"
            throw BydError.serverError(msg, "1008")
        }

        try await Task.sleep(nanoseconds: 1_500_000_000)

        var result: [String: Any]? = nil
        for attempt in 1...5 {
            if attempt > 1 {
                try await Task.sleep(nanoseconds: 2_000_000_000)
            }
            var pollInner = buildInnerBase(vin: vin, requestSerial: serial)
            pollInner.append(("energyType", "0"))
            pollInner.append(("tboxVersion", "3"))
            do {
                let pollResult = try await postTokenSecure(
                    endpoint: "/vehicleInfo/vehicle/vehicleRealTimeResult",
                    innerMap: pollInner, vin: vin
                )
                print("[BydAPI] vehicleRealTimeResult (시도 \(attempt)): keys=\(pollResult.keys.sorted()) soc=\(pollResult["soc"] ?? "nil") mileageEV=\(pollResult["mileageEV"] ?? "nil")")
                result = pollResult
                break
            } catch BydError.serverError(let msg, let code) where code == "3002" {
                print("[BydAPI] 차량 상태 조회 처리 중 (시도 \(attempt)/5) code=3002 msg=\(msg)")
                if attempt == 5 { throw BydError.controlTimeout }
            } catch {
                print("[BydAPI] vehicleRealTimeResult 오류 (시도 \(attempt)): \(error)")
                throw error
            }
        }
        guard let result = result else { throw BydError.invalidResponse }

        var status = VehicleStatus()
        status.batteryPercentage   = (result["soc"] as? Int) ?? (result["elecPercent"] as? Int) ?? 0
        status.drivingRange        = (result["mileageEV"] as? Double) ?? (result["enduranceMileage"] as? Double) ?? 0.0
        status.powerGear           = result["powerGear"]    as? Int    ?? -1
        status.epb                 = result["epb"]          as? Int    ?? -1
        status.speed               = result["speed"]        as? Double ?? 0.0
        status.instantPowerW       = result["gl"]           as? Double ?? 0.0
        status.totalMileage        = result["totalMileage"] as? Double ?? 0.0

        let lf = result["leftFrontDoorLock"]  as? Int ?? 0
        let rf = result["rightFrontDoorLock"] as? Int ?? 0
        let lr = result["leftRearDoorLock"]   as? Int ?? 0
        let rr = result["rightRearDoorLock"]  as? Int ?? 0
        let hasAny = lf != 0 || rf != 0 || lr != 0 || rr != 0
        status.isLocked = hasAny && (lf == 2 && rf == 2 && lr == 2 && rr == 2)

        let rawTemp = (result["interiorTemp"] as? Double) ?? (result["tempInCar"] as? Double) ?? 0.0
        status.interiorTemperature = (rawTemp > -40 && rawTemp < 100) ? rawTemp : 0.0

        if let hvacResult = try? await fetchHvacStatusRaw(vin: vin) {
            status.isClimateOn = (hvacResult["status"] as? Int ?? 0) == 1
            if status.interiorTemperature == 0.0 {
                let hvacTemp = hvacResult["tempInCar"] as? Double ?? 0.0
                if hvacTemp != 0.0 && hvacTemp != -129.0 { status.interiorTemperature = hvacTemp }
            }
        }
        return status
    }

    // MARK: - Charging Status

    func fetchChargingStatus(vin: String) async throws -> ChargingStatus {
        let result = try await postTokenSecure(
            endpoint: "/control/smartCharge/homePage",
            innerMap: buildInnerBase(vin: vin), vin: vin
        )
        return ChargingStatus(
            isCharging:        (result["chargingState"] as? Int ?? 0) == 1,
            isConnected:       (result["connectState"]  as? Int ?? 0) >= 1,
            batteryPercentage: (result["soc"] as? Int) ?? (result["elecPercent"] as? Int) ?? 0,
            remainingHours:    result["fullHour"]   as? Int    ?? -1,
            remainingMinutes:  result["fullMinute"] as? Int    ?? -1,
            chargeRate:        result["rate"]       as? Double ?? 0.0
        )
    }

    // MARK: - HVAC

    func fetchHvacStatusRaw(vin: String) async throws -> [String: Any] {
        let result = try await postTokenSecure(
            endpoint: "/control/getStatusNow",
            innerMap: buildInnerBase(vin: vin), vin: vin
        )
        return (result["statusNow"] as? [String: Any]) ?? result
    }

    func fetchHvacStatus(vin: String) async throws -> HvacStatus {
        let target = try await fetchHvacStatusRaw(vin: vin)
        return HvacStatus(
            isAcOn:               (target["status"] as? Int ?? 0) == 1,
            interiorTemperature:  target["tempInCar"]          as? Double ?? 0.0,
            exteriorTemperature:  target["tempOutCar"]         as? Double ?? 0.0,
            targetTemperature:    target["mainSettingTempNew"] as? Double ?? 0.0,
            windLevel:            target["windPosition"]       as? Int    ?? 0,
            cycleMode:            target["cycleChoice"]        as? Int    ?? 0,
            airConditioningMode:  target["airConditioningMode"] as? Int   ?? 0
        )
    }

    // MARK: - Energy Consumption

    func fetchEnergyConsumption(vin: String) async throws -> EnergyConsumptionData {
        let result = try await postTokenSecure(
            endpoint: "/vehicleInfo/vehicle/getEnergyConsumption",
            innerMap: buildInnerBase(vin: vin), vin: vin
        )

        // selfGraph: 7일 롤링 소비 그래프 (날짜별 kWh/100km)
        let selfGraph = result["selfGraph"] as? [[String: Any]] ?? []
        let dailyConsumption: [DailyEnergyConsumption] = selfGraph.compactMap { item in
            guard let date = item["date"] as? String,
                  let value = item["value"] as? Double else { return nil }
            return DailyEnergyConsumption(date: date, kwhPer100km: value)
        }

        // cumulativeEnergyConsumption: 라이프타임 평균 전비
        let cumulative = result["cumulativeEnergyConsumption"] as? [String: Any] ?? [:]
        let lifetimeAvgKwhPer100km = cumulative["energyConsumption"] as? Double ?? 0.0
        let lifetimeMileageKm      = cumulative["mileage"]           as? Double ?? 0.0

        // nearestEnergyConsumption: 최근 50km
        let nearest = result["nearestEnergyConsumption"] as? [String: Any] ?? [:]
        let recent50kmKwhPer100km  = nearest["energyConsumption"] as? Double ?? 0.0

        return EnergyConsumptionData(
            dailyConsumption: dailyConsumption,
            lifetimeAvgKwhPer100km: lifetimeAvgKwhPer100km,
            lifetimeMileageKm: lifetimeMileageKm,
            recent50kmKwhPer100km: recent50kmKwhPer100km
        )
    }
}

// MARK: - Error Types

enum BydError: LocalizedError {
    case notLoggedIn
    case sessionExpired
    case invalidResponse
    case serverError(String, String)
    case controlTimeout
    case networkError(Error)

    var errorDescription: String? {
        switch self {
        case .notLoggedIn:               return "로그인이 필요합니다"
        case .sessionExpired:            return "세션이 만료되었습니다"
        case .invalidResponse:           return "잘못된 응답 형식"
        case .serverError(_, "1008"):    return "차량이 응답하지 않습니다 (절전 모드일 수 있음)"
        case .serverError(let m, let c): return "서버 오류: \(m) (\(c))"
        case .controlTimeout:            return "요청 시간 초과"
        case .networkError(let e):       return "네트워크 오류: \(e.localizedDescription)"
        }
    }
}
