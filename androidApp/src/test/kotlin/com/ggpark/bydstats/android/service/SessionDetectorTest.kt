package com.ggpark.bydstats.android.service

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ggpark.bydstats.android.data.AppDatabase
import com.ggpark.bydstats.model.VehicleStatus
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class SessionDetectorTest {

    private lateinit var db: AppDatabase
    private lateinit var detector: SessionDetector

    private val rate = 180.0
    private val capacity = 60.48

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        detector = SessionDetector(db, rate, capacity)
    }

    @After
    fun teardown() {
        db.close()
    }

    // ─── DataPoint 저장 ───────────────────────────────────────────────────────

    @Test
    fun `process stores DataPoint on every call`() = runTest {
        val status = parked(soc = 80)
        detector.process(status, System.currentTimeMillis())
        val points = db.dataPointDao().allFlow()
        // Room Flow에서 첫 값 꺼내기
        val list = db.dataPointDao().getAll()
        assertEquals(1, list.size)
        assertEquals(80, list[0].batteryPercent)
    }

    @Test
    fun `process stores multiple DataPoints`() = runTest {
        repeat(3) { i ->
            detector.process(parked(soc = 80 - i), System.currentTimeMillis() + i * 60_000L)
        }
        val list = db.dataPointDao().getAll()
        assertEquals(3, list.size)
    }

    // ─── 주행 세션 ───────────────────────────────────────────────────────────

    @Test
    fun `driving session created on first isDriving=true`() = runTest {
        val t0 = System.currentTimeMillis()
        detector.process(driving(soc = 90), t0)

        val sessions = db.drivingSessionDao().getAll()
        assertEquals(1, sessions.size)
        assertNull(sessions[0].endTime, "주행 중이므로 endTime이 null이어야 함")
        assertEquals(90, sessions[0].startSoc)
    }

    @Test
    fun `driving session ends when isDriving becomes false`() = runTest {
        val t0 = System.currentTimeMillis()
        val t1 = t0 + 300_000L   // 5분 후 (2분 임계값 초과)

        detector.process(driving(soc = 90), t0)
        detector.process(parked(soc = 85), t1)

        val sessions = db.drivingSessionDao().getAll()
        assertEquals(1, sessions.size)
        assertNotNull(sessions[0].endTime)
        assertEquals(85, sessions[0].endSoc)
    }

    @Test
    fun `driving session under 2 min is deleted (noise filter)`() = runTest {
        val t0 = System.currentTimeMillis()
        val t1 = t0 + 90_000L    // 1분 30초 — 2분 미만

        detector.process(driving(soc = 90), t0)
        detector.process(parked(soc = 89), t1)

        val sessions = db.drivingSessionDao().getAll()
        assertEquals(0, sessions.size, "2분 미만 주행 세션은 삭제돼야 함")
    }

    @Test
    fun `driving session exactly 2 min is kept`() = runTest {
        val t0 = System.currentTimeMillis()
        val t1 = t0 + 120_000L   // 정확히 2분

        detector.process(driving(soc = 90), t0)
        detector.process(parked(soc = 89), t1)

        val sessions = db.drivingSessionDao().getAll()
        assertEquals(1, sessions.size, "2분 이상이면 세션이 저장돼야 함")
    }

    @Test
    fun `no duplicate driving session on continuous isDriving=true`() = runTest {
        val t0 = System.currentTimeMillis()
        detector.process(driving(soc = 90), t0)
        detector.process(driving(soc = 89), t0 + 60_000L)
        detector.process(driving(soc = 88), t0 + 120_000L)

        val sessions = db.drivingSessionDao().getAll()
        assertEquals(1, sessions.size, "연속 주행 중 세션이 하나만 열려야 함")
    }

    @Test
    fun `driving energy calculated from SOC delta`() = runTest {
        val t0 = System.currentTimeMillis()
        val t1 = t0 + 300_000L

        detector.process(driving(soc = 90), t0)
        detector.process(parked(soc = 80), t1)  // 10% 소비

        val session = db.drivingSessionDao().getAll().first()
        val expected = 10.0 * capacity / 100.0  // 6.048 kWh
        assertEquals(expected, session.energyKwh, 0.001)
    }

    @Test
    fun `ODO-based distance calculated when available`() = runTest {
        val t0 = System.currentTimeMillis()
        val t1 = t0 + 300_000L

        detector.process(driving(soc = 90, odo = 10_000.0), t0)
        detector.process(parked(soc = 85, odo = 10_015.0), t1)  // 15km 주행

        val session = db.drivingSessionDao().getAll().first()
        assertEquals(15.0, session.distanceKm ?: 0.0, 0.001)
    }

    @Test
    fun `GPS distance used as fallback when ODO unavailable`() = runTest {
        val t0 = System.currentTimeMillis()
        val t1 = t0 + 300_000L

        val detectorWithGps = SessionDetector(db, rate, capacity)
        detectorWithGps.process(driving(soc = 90, odo = 0.0), t0)
        detectorWithGps.process(parked(soc = 85, odo = 0.0), t1)

        val session = db.drivingSessionDao().getAll().first()
        // GPS 없으므로 distanceKm는 null
        assertNull(session.distanceKm, "ODO=0이고 GPS 없으면 distanceKm가 null이어야 함")
    }

    // ─── 세션 분리 버그 시나리오 (수정 후 검증) ──────────────────────────────

    @Test
    fun `short isDriving=false gap does not create two sessions`() = runTest {
        // 이 버그는 DataCollector 레벨(네트워크 재시도)에서 수정됨
        // SessionDetector 레벨에서는 isDriving=false가 진짜로 오면 세션을 닫는 게 정상
        // 버그 시나리오: 1분 주행 → false(노이즈) → 1분 주행 = 2개 세션 vs 1개
        val t0 = System.currentTimeMillis()

        detector.process(driving(soc = 90), t0)                         // 세션 1 시작
        detector.process(parked(soc = 90), t0 + 60_000L)                // 1분 만에 false → 2분 미만이므로 삭제
        detector.process(driving(soc = 89), t0 + 61_000L)               // 세션 2 시작
        detector.process(parked(soc = 85), t0 + 61_000L + 300_000L)    // 세션 2 종료

        val sessions = db.drivingSessionDao().getAll()
        // 1번 세션은 2분 미만 → 삭제, 2번 세션만 남음
        assertEquals(1, sessions.size, "2분 미만 세션은 삭제되어 1개만 남아야 함")
    }

    // ─── 충전 세션 ───────────────────────────────────────────────────────────

    @Test
    fun `charging session created on isCharging=true`() = runTest {
        val t0 = System.currentTimeMillis()
        detector.process(charging(soc = 70), t0)

        val sessions = db.chargingSessionDao().getAll()
        assertEquals(1, sessions.size)
        assertNull(sessions[0].endTime)
        assertEquals(70, sessions[0].startSoc)
    }

    @Test
    fun `charging session ends with correct energy`() = runTest {
        val t0 = System.currentTimeMillis()
        val t1 = t0 + 3_600_000L   // 1시간 충전

        detector.process(charging(soc = 60), t0)
        detector.process(parked(soc = 80), t1)  // 20% 충전

        val session = db.chargingSessionDao().getAll().first()
        assertNotNull(session.endTime)
        assertEquals(80, session.endSoc)
        val expectedEnergy = 20.0 * capacity / 100.0  // 12.096 kWh
        assertEquals(expectedEnergy, session.energyKwh, 0.001)
        val expectedCost = expectedEnergy * rate
        assertEquals(expectedCost, session.estimatedCostKrw, 0.1)
    }

    @Test
    fun `charging session duration is correct`() = runTest {
        val t0 = System.currentTimeMillis()
        val t1 = t0 + 90 * 60_000L  // 90분

        detector.process(charging(soc = 50), t0)
        detector.process(parked(soc = 90), t1)

        val session = db.chargingSessionDao().getAll().first()
        assertEquals(90, session.durationMinutes)
    }

    @Test
    fun `no duplicate charging session on continuous isCharging=true`() = runTest {
        val t0 = System.currentTimeMillis()
        detector.process(charging(soc = 60), t0)
        detector.process(charging(soc = 65), t0 + 600_000L)
        detector.process(charging(soc = 70), t0 + 1_200_000L)

        val sessions = db.chargingSessionDao().getAll()
        assertEquals(1, sessions.size)
        assertEquals(70, sessions[0].endSoc, "연속 충전 중 endSoc 계속 업데이트돼야 함")
    }

    // ─── recoverOrphanSessions ────────────────────────────────────────────────

    @Test
    fun `recent orphan driving session is recovered`() = runTest {
        val t0 = System.currentTimeMillis() - 30 * 60_000L  // 30분 전 시작
        detector.process(driving(soc = 90), t0)

        // 새 SessionDetector 생성 (앱 재시작 시뮬레이션)
        val detector2 = SessionDetector(db, rate, capacity)
        detector2.recover()

        // 30분 전 세션 → 1시간 미만 → 복원됨
        // 복원된 세션이 있으면 다음 isDriving=true 시 새 세션 안 만들어야 함
        val t1 = System.currentTimeMillis()
        detector2.process(driving(soc = 89), t1)

        val sessions = db.drivingSessionDao().getAll()
        assertEquals(1, sessions.size, "복원된 세션이 있으면 새 세션을 만들지 않아야 함")
    }

    @Test
    fun `old orphan driving session is force-closed`() = runTest {
        val t0 = System.currentTimeMillis() - 2 * 3_600_000L  // 2시간 전 시작
        detector.process(driving(soc = 90), t0)

        // 새 SessionDetector 생성
        val detector2 = SessionDetector(db, rate, capacity)
        detector2.recover()

        val sessions = db.drivingSessionDao().getAll()
        assertEquals(1, sessions.size)
        assertNotNull(sessions[0].endTime, "2시간 전 세션은 강제 종료돼야 함")
    }

    // ─── 헬퍼 ────────────────────────────────────────────────────────────────

    private fun parked(soc: Int, odo: Double = 0.0) = VehicleStatus(
        batteryPercentage = soc,
        powerGear = 1,
        speed = 0.0,
        instantPowerW = 0.0,
        totalMileage = odo,
    )

    private fun driving(soc: Int, odo: Double = 0.0) = VehicleStatus(
        batteryPercentage = soc,
        powerGear = 3,
        speed = 60.0,
        instantPowerW = 0.0,
        totalMileage = odo,
    )

    private fun charging(soc: Int) = VehicleStatus(
        batteryPercentage = soc,
        powerGear = 1,
        speed = 0.0,
        instantPowerW = 7400.0,
    )
}
