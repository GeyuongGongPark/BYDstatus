# fix(polling): 폴링 중복 실행으로 세션 쪼개짐/삭제 수정 ✅ v0.6.9

## 체크리스트 (Android 추가)
- [x] `PollingService.kt`: ACTION_RESTART 추가, ACTION_START에 중복 guard
- [x] `PollingService.kt`: restart()가 ACTION_RESTART 사용하도록 변경
- [x] `androidApp/build.gradle.kts`: versionCode 9→10, versionName 0.6.8→0.6.9
- [x] RELEASE_NOTES.md: iOS·Android 통합 설명으로 수정
- [x] tasks/lessons.md 업데이트

---

# fix(polling): iOS 주행 세션 7분 단위 분리 버그 수정 (iOS) ✅ v0.6.9 (first commit)

## 문제
앱 백그라운드→포그라운드 전환 시 DashboardView의 `.task(id:)` modifier가 재실행되면서
`startPolling`이 중복 호출됨. 매 호출마다 새 SessionDetector가 생성되어 기존 주행 세션
컨텍스트가 단절 → 7분 단위 세션 분리.

로그 증거 (09-24):
- 21:09:18: vehicleRealTimeRequest 1개 (첫 시작)
- 21:15:16/17: vehicleRealTimeRequest 2개 (두 번째 startPolling 호출)
- 21:45:57/59/59: vehicleRealTimeRequest 3개 (세 번째 호출!)
- polling 간격 5분 (isDriving=false로 잘못 판정됨)

## 체크리스트
- [x] `AppState.swift`: startPolling 중복 실행 방지 (pollingTask nil 체크)
- [x] `AppState.swift`: SessionDetector nil일 때만 새로 생성 (컨텍스트 유지)
- [x] `AppState.swift`: Task에 defer { pollingTask = nil } 추가
- [x] `AppState.swift`: selectVin에서 stopPolling() 먼저 호출
- [x] 커밋: `fix(polling): prevent duplicate polling tasks on foreground restore (iOS)`
- [x] RELEASE_NOTES.md v0.6.9 추가
- [x] tasks/lessons.md 업데이트

---

# fix(isDriving): 회생제동 후 speed=0일 때 충전 세션 오기록 방지 ✅ v0.6.8

## 문제
v0.6.7 수정(`instantPowerW > 0 → isDriving=false`)이 회생제동 케이스도 잡음.
주행 중 감속하면서 speed=0이 되는 순간 gl이 양수(회생제동)이면 isDriving=false → 충전 세션 시작.
실제 로그: 10:37 주행 중 → 10:38 speed=0 + gl=+7907W → 충전 세션 시작 → 10:40 다시 주행.

## 체크리스트
- [x] `shared/Models.kt`: `reportedDriving: Boolean? = null` 필드 추가
- [x] `shared/ChargingResolve.kt`: `withDrivingResolved()` 함수 추가
- [x] `DataCollector.kt`: withChargingResolved 이전에 withDrivingResolved 호출
- [x] `BydStats/Models/VehicleStatus.swift`: `resolvedDriving: Bool? = nil` 추가
- [x] `AppState.swift`: withDrivingResolved 호출 (iOS)
- [x] 커밋: `fix(isDriving): treat powerGear=3+charging as not driving (iOS·Android)`
- [x] RELEASE_NOTES.md v0.6.8 추가
- [x] tasks/lessons.md 업데이트
