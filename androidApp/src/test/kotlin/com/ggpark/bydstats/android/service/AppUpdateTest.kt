package com.ggpark.bydstats.android.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AppUpdateTest {

    @Test fun `normalize strips v prefix and debug suffix`() {
        assertEquals("0.6.3", AppUpdate.normalizeVersion("v0.6.3"))
        assertEquals("0.6.3", AppUpdate.normalizeVersion("0.6.3-debug"))
        assertEquals("0.6.3", AppUpdate.normalizeVersion("v0.6.3-debug"))
    }

    @Test fun `0_6_3 is newer than 0_6_2`() {
        assertTrue(AppUpdate.isNewer("0.6.3", "0.6.2"))
        assertTrue(AppUpdate.isNewer("v0.6.3", "0.6.2-debug"))
    }

    @Test fun `same version is not newer`() {
        assertFalse(AppUpdate.isNewer("0.6.3", "0.6.3"))
        assertFalse(AppUpdate.isNewer("v0.6.3", "0.6.3-debug"))
    }

    @Test fun `older latest is not newer`() {
        assertFalse(AppUpdate.isNewer("0.6.1", "0.6.3"))
    }

    @Test fun `numeric compare not string compare`() {
        assertTrue(AppUpdate.isNewer("0.10.0", "0.9.0"))
    }

    @Test fun `parseRelease reads apk asset`() {
        val json = """
            {
              "tag_name": "v0.6.3",
              "body": "충전 세션 수정",
              "assets": [
                {"name": "BYDStats-v0.6.3.apk", "browser_download_url": "https://example.com/app.apk"}
              ]
            }
        """.trimIndent()
        val release = AppUpdate.parseRelease(json)
        assertNotNull(release)
        assertEquals("0.6.3", release.version)
        assertEquals("https://example.com/app.apk", release.apkUrl)
        assertEquals("충전 세션 수정", release.notes)
    }

    @Test fun `parseRelease returns null without apk`() {
        val json = """{"tag_name":"v0.6.3","assets":[]}"""
        kotlin.test.assertNull(AppUpdate.parseRelease(json))
    }
}
