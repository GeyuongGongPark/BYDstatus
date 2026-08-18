package com.ggpark.bydstats.api

data class BydConfig(
    val baseURL: String,
    val countryCode: String,
    val language: String,
    val timeZone: String,
) {
    companion object {
        fun fromRegion(region: String): BydConfig {
            val r = region.uppercase().trim()
            return when (r) {
                "KR" -> BydConfig("https://dilinkappoversea-kr-ali.byd.auto", "KR", "ko", "Asia/Seoul")
                "EU" -> BydConfig("https://dilinkappoversea-eu.byd.auto", "GB", "en", "Europe/London")
                "JP" -> BydConfig("https://dilinkappoversea-jp.byd.auto", "JP", "ja", "Asia/Tokyo")
                "SG" -> BydConfig("https://dilinkappoversea-sg.byd.auto", "SG", "en", "Asia/Singapore")
                "AU" -> BydConfig("https://dilinkappoversea-au.byd.auto", "AU", "en", "Australia/Sydney")
                "BR" -> BydConfig("https://dilinkappoversea-br.byd.auto", "BR", "pt", "America/Sao_Paulo")
                "MX" -> BydConfig("https://dilinkappoversea-mx.byd.auto", "MX", "es", "America/Mexico_City")
                "NO" -> BydConfig("https://dilinkappoversea-no.byd.auto", "NO", "no", "Europe/Oslo")
                "UZ" -> BydConfig("https://dilinkappoversea-uz.byd.auto", "UZ", "en", "Asia/Tashkent")
                "KZ" -> BydConfig("https://dilinkappoversea-kz.byd.auto", "KZ", "en", "Asia/Almaty")
                "IN" -> BydConfig("https://dilinkappoversea-in.byd.auto", "IN", "en", "Asia/Kolkata")
                "ID" -> BydConfig("https://dilinkappoversea-id.byd.auto", "ID", "in", "Asia/Jakarta")
                "VN" -> BydConfig("https://dilinkappoversea-vn.byd.auto", "VN", "vi", "Asia/Ho_Chi_Minh")
                "SA" -> BydConfig("https://dilinkappoversea-sa.byd.auto", "SA", "ar", "Asia/Riyadh")
                "OM" -> BydConfig("https://dilinkappoversea-om.byd.auto", "OM", "ar", "Asia/Muscat")
                else -> BydConfig("https://dilinkappoversea-${r.lowercase()}.byd.auto", r, "en", "UTC")
            }
        }
    }
}
