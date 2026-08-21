package com.ggpark.bydstats.api

import com.ggpark.bydstats.crypto.BangcleCodec
import com.ggpark.bydstats.crypto.CryptoUtils
import com.ggpark.bydstats.model.*
import com.ggpark.bydstats.util.currentTimeMillis
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*

// BYD API는 숫자를 JSON number 또는 JSON string으로 혼재해서 반환 — 양쪽 모두 파싱
internal fun JsonObject.jsonInt(vararg keys: String): Int {
    for (k in keys) {
        val prim = this[k]?.jsonPrimitive ?: continue
        prim.intOrNull?.let { return it }
        prim.content.toIntOrNull()?.let { return it }
    }
    return 0
}

internal fun JsonObject.jsonDouble(vararg keys: String): Double {
    for (k in keys) {
        val prim = this[k]?.jsonPrimitive ?: continue
        prim.doubleOrNull?.let { return it }
        prim.content.toDoubleOrNull()?.let { return it }
    }
    return 0.0
}

internal fun parseVehicleStatus(r: JsonObject): VehicleStatus {
    val lf = r.jsonInt("leftFrontDoorLock")
    val rf = r.jsonInt("rightFrontDoorLock")
    val lr = r.jsonInt("leftRearDoorLock")
    val rr = r.jsonInt("rightRearDoorLock")
    val hasAny = lf != 0 || rf != 0 || lr != 0 || rr != 0
    val rawTemp = r.jsonDouble("interiorTemp", "tempInCar")
    return VehicleStatus(
        batteryPercentage   = r.jsonInt("soc", "elecPercent"),
        drivingRange        = r.jsonDouble("mileageEV", "enduranceMileage"),
        powerGear           = r["powerGear"]?.jsonPrimitive?.let { it.intOrNull ?: it.content.toIntOrNull() } ?: -1,
        epb                 = r["epb"]?.jsonPrimitive?.let { it.intOrNull ?: it.content.toIntOrNull() } ?: -1,
        speed               = r.jsonDouble("speed"),
        instantPowerW       = r.jsonDouble("gl"),
        totalMileage        = r.jsonDouble("totalMileage"),
        isLocked            = hasAny && (lf == 2 && rf == 2 && lr == 2 && rr == 2),
        interiorTemperature = if (rawTemp > -40 && rawTemp < 100) rawTemp else 0.0,
    )
}

sealed class BydError : Exception() {
    object NotLoggedIn : BydError()
    object SessionExpired : BydError()
    object InvalidResponse : BydError()
    object ControlTimeout : BydError()
    data class ServerError(val msg: String, val code: String) : BydError()
    data class NetworkError(val rootCause: Throwable) : BydError()
}

class BydApiClient(
    private val config: BydConfig,
    tableData: ByteArray,
    private val client: HttpClient,
) {
    private val codec = BangcleCodec(tableData)
    private val json = Json { ignoreUnknownKeys = true }

    private val deviceProfile = mapOf(
        "ostype" to "and",
        "imei" to "BANGCLE01234",
        "mac" to "00:00:00:00:00:00",
        "model" to "POCO F1",
        "sdk" to "35",
        "mod" to "Xiaomi",
        "mobileBrand" to "XIAOMI",
        "mobileModel" to "POCO F1",
        "deviceType" to "0",
        "networkType" to "wifi",
        "osType" to "15",
        "osVersion" to "35",
        "appInnerVersion" to "322",
        "appVersion" to "3.2.2",
    )

    var userId: String? = null
        private set
    var signToken: String? = null
        private set
    var encryToken: String? = null
        private set
    private var accountImeiMD5 = "0".repeat(32)
    private var storedUsername: String? = null
    private var storedPassword: String? = null
    private var isRelogging = false

    var onSessionUpdated: ((String, String, String) -> Unit)? = null
    var onSessionExpired: (() -> Unit)? = null

    val isLoggedIn get() = !signToken.isNullOrEmpty()

    fun setCredentials(username: String, password: String) {
        storedUsername = username
        storedPassword = password
        if (username.isNotEmpty()) accountImeiMD5 = CryptoUtils.md5Hex(username)
    }

    fun restoreSession(userId: String, signToken: String, encryToken: String) {
        this.userId = userId
        this.signToken = signToken
        this.encryToken = encryToken
    }

    // MARK: - JSON Helpers

    private fun toSortedJson(map: List<Pair<String, Any?>>): String {
        val parts = map.map { (key, value) ->
            val valStr = when (value) {
                null -> "null"
                is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                else -> "$value"
            }
            "\"$key\":$valStr"
        }
        return "{${parts.joinToString(",")}}"
    }

    private fun buildInnerBase(vin: String? = null, requestSerial: String? = null): List<Pair<String, Any?>> {
        val map = mutableListOf(
            "deviceType" to (deviceProfile["deviceType"] ?: ""),
            "imeiMD5" to accountImeiMD5,
            "networkType" to (deviceProfile["networkType"] ?: ""),
            "random" to CryptoUtils.md5Hex("${kotlin.random.Random.nextDouble()}").take(16),
            "timeStamp" to "${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}",
            "version" to (deviceProfile["appInnerVersion"] ?: ""),
        )
        vin?.let { map.add("vin" to it) }
        requestSerial?.let { map.add("requestSerial" to it) }
        return map
    }

    private fun nowMs() = currentTimeMillis()

    // MARK: - Authenticated Request

    private suspend fun postTokenSecure(
        endpoint: String,
        innerMap: List<Pair<String, Any?>>,
        vin: String?,
    ): JsonObject {
        val uid = userId ?: throw BydError.NotLoggedIn
        val signTok = signToken ?: throw BydError.NotLoggedIn
        val encTok = encryToken ?: throw BydError.NotLoggedIn

        val reqTimestamp = "${nowMs()}"
        val innerJson = toSortedJson(innerMap)
        val encryData = CryptoUtils.aesEncryptHex(innerJson, CryptoUtils.md5Hex(encTok))

        val signFields = mutableMapOf<String, String>()
        innerMap.forEach { (k, v) -> signFields[k] = "${v ?: "null"}" }
        signFields["countryCode"]  = config.countryCode
        signFields["identifier"]   = uid
        signFields["imeiMD5"]      = accountImeiMD5
        signFields["language"]     = config.language
        signFields["reqTimestamp"] = reqTimestamp
        val sign = CryptoUtils.sha1Mixed(
            CryptoUtils.buildSignString(signFields, CryptoUtils.md5Hex(signTok))
        )

        val outerList = mutableListOf(
            "countryCode"  to config.countryCode,
            "encryData"    to encryData,
            "identifier"   to uid,
            "imeiMD5"      to accountImeiMD5,
            "language"     to config.language,
            "reqTimestamp" to reqTimestamp,
            "sign"         to sign,
            "ostype"       to deviceProfile["ostype"],
            "imei"         to deviceProfile["imei"],
            "mac"          to deviceProfile["mac"],
            "model"        to deviceProfile["model"],
            "sdk"          to deviceProfile["sdk"],
            "mod"          to deviceProfile["mod"],
            "serviceTime"  to reqTimestamp,
        )
        val outerJsonNoCheck = toSortedJson(outerList)
        outerList.add("checkcode" to CryptoUtils.computeCheckcode(outerJsonNoCheck))
        val finalOuterJson = toSortedJson(outerList)

        return performRequest(endpoint, finalOuterJson, encTok, endpoint, innerMap, vin)
    }

    private suspend fun performRequest(
        endpoint: String,
        outerJson: String,
        encTok: String,
        retryEndpoint: String,
        retryInnerMap: List<Pair<String, Any?>>,
        retryVin: String?,
    ): JsonObject {
        val encodedRequest = codec.encodeEnvelope(outerJson)
        val bodyPayload = "{\"request\":\"${encodedRequest.replace("\"", "\\\"")}\"}"

        val response = client.post("${config.baseURL}$endpoint") {
            contentType(ContentType.Application.Json)
            headers {
                append("Accept-Encoding", "identity")
                append("User-Agent", "okhttp/4.12.0")
            }
            setBody(bodyPayload)
        }

        val bodyText = response.bodyAsText()
        val bodyJson = json.parseToJsonElement(bodyText).jsonObject
        val encodedResponse = bodyJson["response"]?.jsonPrimitive?.content
            ?: throw BydError.InvalidResponse

        var decoded = codec.decodeEnvelope(encodedResponse).trim()
        if (decoded.startsWith("F{") || decoded.startsWith("F[")) decoded = decoded.drop(1)

        val outerResp = json.parseToJsonElement(decoded).jsonObject
        val resCode = outerResp["code"]?.jsonPrimitive?.content ?: "0"

        if (resCode != "0") {
            if (resCode in listOf("1002", "1005", "1010")) {
                return silentReLogin(retryEndpoint, retryInnerMap, retryVin)
            }
            val msg = outerResp["message"]?.jsonPrimitive?.content ?: "Unknown"
            throw BydError.ServerError(msg, resCode)
        }

        val respondData = outerResp["respondData"]?.jsonPrimitive?.content ?: ""
        if (respondData.isEmpty()) return outerResp

        val innerText = try {
            CryptoUtils.aesDecryptUTF8(respondData, CryptoUtils.md5Hex(encTok))
        } catch (e: Exception) {
            return silentReLogin(retryEndpoint, retryInnerMap, retryVin)
        }

        return if (innerText.startsWith("[")) {
            val arr = json.parseToJsonElement(innerText).jsonArray
            buildJsonObject { put("list", arr) }
        } else {
            json.parseToJsonElement(innerText).jsonObject
        }
    }

    private suspend fun silentReLogin(
        endpoint: String,
        innerMap: List<Pair<String, Any?>>,
        vin: String?,
    ): JsonObject {
        if (isRelogging) throw BydError.SessionExpired
        val user = storedUsername?.takeIf { it.isNotEmpty() } ?: run {
            onSessionExpired?.invoke()
            throw BydError.SessionExpired
        }
        val pwd = storedPassword ?: throw BydError.SessionExpired
        isRelogging = true
        try {
            login(user, pwd)
            return postTokenSecure(endpoint, innerMap, vin)
        } finally {
            isRelogging = false
        }
    }

    // MARK: - Login

    suspend fun login(username: String, password: String): String {
        val derivedImeiMD5 = CryptoUtils.md5Hex(username)
        accountImeiMD5 = derivedImeiMD5
        val reqTimestamp = "${nowMs()}"
        val randomHex = CryptoUtils.md5Hex("${kotlin.random.Random.nextDouble()}").take(32)

        val innerMap = listOf(
            "agreeStatus"     to "0",
            "agreementType"   to "[1,2]",
            "appInnerVersion" to (deviceProfile["appInnerVersion"] ?: ""),
            "appVersion"      to (deviceProfile["appVersion"] ?: ""),
            "deviceName"      to "${deviceProfile["mobileBrand"] ?: ""}${deviceProfile["mobileModel"] ?: ""}",
            "deviceType"      to (deviceProfile["deviceType"] ?: ""),
            "imeiMD5"         to derivedImeiMD5,
            "isAuto"          to "1",
            "mobileBrand"     to (deviceProfile["mobileBrand"] ?: ""),
            "mobileModel"     to (deviceProfile["mobileModel"] ?: ""),
            "networkType"     to (deviceProfile["networkType"] ?: ""),
            "osType"          to (deviceProfile["osType"] ?: ""),
            "osVersion"       to (deviceProfile["osVersion"] ?: ""),
            "random"          to randomHex,
            "softType"        to "0",
            "timeStamp"       to reqTimestamp,
            "timeZone"        to config.timeZone,
        )

        val innerJson = toSortedJson(innerMap)
        val loginKey = CryptoUtils.pwdLoginKey(password)
        val encryData = CryptoUtils.aesEncryptHex(innerJson, loginKey)

        val signFields = mutableMapOf<String, String>()
        innerMap.forEach { (k, v) -> signFields[k] = "${v ?: "null"}" }
        signFields["appName"]        = "pyBYD+0.1.dev2+ge0a1f5e27"
        signFields["countryCode"]    = config.countryCode
        signFields["functionType"]   = "pwdLogin"
        signFields["identifier"]     = username
        signFields["identifierType"] = "0"
        signFields["language"]       = config.language
        signFields["reqTimestamp"]   = reqTimestamp
        val sign = CryptoUtils.sha1Mixed(
            CryptoUtils.buildSignString(signFields, CryptoUtils.md5Hex(password))
        )

        val outerList = mutableListOf(
            "appName"        to "pyBYD+0.1.dev2+ge0a1f5e27",
            "countryCode"    to config.countryCode,
            "encryData"      to encryData,
            "functionType"   to "pwdLogin",
            "identifier"     to username,
            "identifierType" to "0",
            "imeiMD5"        to derivedImeiMD5,
            "isAuto"         to "1",
            "language"       to config.language,
            "reqTimestamp"   to reqTimestamp,
            "sign"           to sign,
            "signKey"        to password,
            "ostype"         to deviceProfile["ostype"],
            "imei"           to deviceProfile["imei"],
            "mac"            to deviceProfile["mac"],
            "model"          to deviceProfile["model"],
            "sdk"            to deviceProfile["sdk"],
            "mod"            to deviceProfile["mod"],
            "serviceTime"    to reqTimestamp,
        )
        val outerJsonNoCheck = toSortedJson(outerList)
        outerList.add("checkcode" to CryptoUtils.computeCheckcode(outerJsonNoCheck))
        val finalOuterJson = toSortedJson(outerList)

        val encodedRequest = codec.encodeEnvelope(finalOuterJson)
        val bodyPayload = "{\"request\":\"${encodedRequest.replace("\"", "\\\"")}\"}"

        val response = client.post("${config.baseURL}/app/account/login") {
            contentType(ContentType.Application.Json)
            headers {
                append("Accept-Encoding", "identity")
                append("User-Agent", "okhttp/4.12.0")
            }
            setBody(bodyPayload)
        }

        val bodyText = response.bodyAsText()
        val bodyJson = json.parseToJsonElement(bodyText).jsonObject
        val encodedResponse = bodyJson["response"]?.jsonPrimitive?.content
            ?: throw BydError.InvalidResponse

        var decoded = codec.decodeEnvelope(encodedResponse).trim()
        if (decoded.startsWith("F{")) decoded = decoded.drop(1)

        val outerResp = json.parseToJsonElement(decoded).jsonObject
        val resCode = outerResp["code"]?.jsonPrimitive?.content ?: "0"
        if (resCode != "0") {
            val msg = outerResp["message"]?.jsonPrimitive?.content ?: "Login failed"
            throw BydError.ServerError(msg, resCode)
        }

        val respondData = outerResp["respondData"]?.jsonPrimitive?.content ?: throw BydError.InvalidResponse
        val innerText = CryptoUtils.aesDecryptUTF8(respondData, loginKey)
        val innerResp = json.parseToJsonElement(innerText).jsonObject
        val token = innerResp["token"]?.jsonObject ?: throw BydError.InvalidResponse

        val uid   = token["userId"]?.jsonPrimitive?.content ?: throw BydError.InvalidResponse
        val sign2 = token["signToken"]?.jsonPrimitive?.content ?: throw BydError.InvalidResponse
        val encry = token["encryToken"]?.jsonPrimitive?.content ?: throw BydError.InvalidResponse

        userId     = uid
        signToken  = sign2
        encryToken = encry
        storedUsername = username
        storedPassword = password

        onSessionUpdated?.invoke(uid, sign2, encry)
        return uid
    }

    // MARK: - Vehicle List

    suspend fun fetchVehicleList(): List<VehicleListItem> {
        val result = postTokenSecure("/app/account/getAllListByUserId", buildInnerBase(), null)
        return result["list"]?.jsonArray?.mapNotNull { el ->
            val obj = el.jsonObject
            val vin = obj["vin"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val model = obj["modelName"]?.jsonPrimitive?.content
                ?: obj["model"]?.jsonPrimitive?.content
                ?: obj["seriesName"]?.jsonPrimitive?.content
                ?: "차량"
            VehicleListItem(vin = vin, modelName = model)
        } ?: emptyList()
    }

    // MARK: - Vehicle Status

    suspend fun fetchVehicleStatus(vin: String): VehicleStatus {
        val inner = buildInnerBase(vin = vin).toMutableList()
        inner.add("energyType" to "0")
        inner.add("tboxVersion" to "3")

        val triggerResult = postTokenSecure("/vehicleInfo/vehicle/vehicleRealTimeRequest", inner, vin)
        val serial = triggerResult["requestSerial"]?.jsonPrimitive?.content
            ?: throw BydError.ServerError("차량 tbox 응답 없음", "1008")

        delay(1500)

        var result: JsonObject? = null
        for (attempt in 1..5) {
            if (attempt > 1) delay(2000)
            val pollInner = buildInnerBase(vin = vin, requestSerial = serial).toMutableList()
            pollInner.add("energyType" to "0")
            pollInner.add("tboxVersion" to "3")
            try {
                val pollResult = postTokenSecure("/vehicleInfo/vehicle/vehicleRealTimeResult", pollInner, vin)
                // soc 또는 elecPercent가 없으면 차량 데이터 미준비 → 재시도
                val hasSoc = pollResult["soc"] != null || pollResult["elecPercent"] != null
                if (!hasSoc) {
                    if (attempt == 5) throw BydError.ControlTimeout
                    continue
                }
                result = pollResult
                break
            } catch (e: BydError.ServerError) {
                if (e.code == "3002" && attempt == 5) throw BydError.ControlTimeout
                if (e.code != "3002") throw e
            } catch (e: BydError.ControlTimeout) {
                throw e
            } catch (e: Exception) {
                // 네트워크 오류(cancelled, timeout 등)는 마지막 시도까지 재시도
                if (attempt == 5) throw e
            }
        }
        return parseVehicleStatus(result ?: throw BydError.InvalidResponse)
    }

    // MARK: - Charging Status

    suspend fun fetchChargingStatus(vin: String): ChargingStatus {
        val r = postTokenSecure("/control/smartCharge/homePage", buildInnerBase(vin = vin), vin)
        return ChargingStatus(
            isCharging        = (r["chargingState"]?.jsonPrimitive?.intOrNull ?: 0) == 1,
            isConnected       = (r["connectState"]?.jsonPrimitive?.intOrNull ?: 0) >= 1,
            batteryPercentage = r["soc"]?.jsonPrimitive?.intOrNull ?: r["elecPercent"]?.jsonPrimitive?.intOrNull ?: 0,
            remainingHours    = r["fullHour"]?.jsonPrimitive?.intOrNull ?: -1,
            remainingMinutes  = r["fullMinute"]?.jsonPrimitive?.intOrNull ?: -1,
            chargeRate        = r["rate"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
        )
    }

    // MARK: - Energy Consumption

    suspend fun fetchEnergyConsumption(vin: String): EnergyConsumptionData {
        val r = postTokenSecure("/vehicleInfo/vehicle/getEnergyConsumption", buildInnerBase(vin = vin), vin)

        val selfGraph = r["selfGraph"]?.jsonArray ?: JsonArray(emptyList())
        val dailyConsumption = selfGraph.mapNotNull { el ->
            val obj = el.jsonObject
            val date = obj["date"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val value = obj["value"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
            DailyEnergyConsumption(date = date, kwhPer100km = value)
        }

        val cumulative = r["cumulativeEnergyConsumption"]?.jsonObject ?: JsonObject(emptyMap())
        val nearest    = r["nearestEnergyConsumption"]?.jsonObject ?: JsonObject(emptyMap())

        return EnergyConsumptionData(
            dailyConsumption         = dailyConsumption,
            lifetimeAvgKwhPer100km   = cumulative["energyConsumption"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            lifetimeMileageKm        = cumulative["mileage"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
            recent50kmKwhPer100km    = nearest["energyConsumption"]?.jsonPrimitive?.doubleOrNull ?: 0.0,
        )
    }

    // MARK: - MQTT Broker

    /** MQTT 브로커 주소 조회 → "host:port" 문자열 반환 */
    suspend fun fetchMqttBroker(): Pair<String, Int> {
        val inner = buildInnerBase()
        val r = postTokenSecure("/app/emqAuth/getEmqBrokerIp", inner, vin = null)
        val raw = r["emqBorker"]?.jsonPrimitive?.content
            ?: r["emqBroker"]?.jsonPrimitive?.content
            ?: throw BydError.ServerError("MQTT 브로커 주소 없음", "MQTT_01")
        val clean = raw.trim()
            .removePrefix("mqtt://").removePrefix("mqtts://")
            .substringBefore("/")
        return if (clean.contains(":")) {
            val host = clean.substringBeforeLast(":")
            val port = clean.substringAfterLast(":").toIntOrNull() ?: 8883
            host to port
        } else {
            clean to 8883
        }
    }
}
