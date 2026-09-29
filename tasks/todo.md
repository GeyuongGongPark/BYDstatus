# chore: bump version to 0.6.12 (iOS·Android)

## 체크리스트
- [x] `BydStats/Resources/Info.plist`: 0.6.11 → 0.6.12, CFBundleVersion 16 → 17
- [x] `BydStatsWidget/Info.plist`: 동일
- [x] `androidApp/build.gradle.kts`: versionName "0.6.10" → "0.6.12", versionCode 11 → 12
- [x] `RELEASE_NOTES.md`: v0.6.12 섹션 추가
- [x] 커밋 & 푸시

---

# fix(polling): soc=0 연속 발생 시 에러 메시지 표시 (iOS·Android)

## 문제
soc=0이 연속으로 발생하면 조용히 skip되어 사용자가 원인을 알 수 없음.

## 체크리스트
- [x] `AppState.swift`: `socZeroCount` 카운터 추가, 3회 연속 시 `pollError` 설정, 정상 응답 시 리셋
- [x] `DataCollector.kt`: 동일하게 카운터 추가, 3회 연속 시 `_error` 설정, 정상 응답 시 리셋
- [ ] 커밋 & 푸시

---

# fix(android): PUSH_API_KEY CI 누락 수정 + 세션 복원 시 토큰 등록

## 문제
- `release.yml` Build APK step에 `PUSH_API_KEY` 환경변수가 누락 → CI 빌드 APK에 빈 문자열 → 서버 401 → 조용히 실패
- `build.gradle.kts`가 `local.properties`만 참조, 환경변수 미지원
- `loadSettings()` 세션 복원 시 `registerFcmToken()` 미호출 → 앱 재시작 후 토큰 갱신 안됨

## 체크리스트
- [x] `release.yml`: Build APK step에 `PUSH_API_KEY: ${{ secrets.PUSH_API_KEY }}` 추가
- [x] `androidApp/build.gradle.kts`: `PUSH_API_KEY` 환경변수 폴백 추가
- [x] `AppViewModel.loadSettings()`: 세션 복원 완료 후 `registerFcmToken()` 호출
- [x] 커밋 & 푸시

---

# chore(ios): bump iOS to v0.6.11, 커밋 & 푸시

## 체크리스트
- [x] `BydStats/Resources/Info.plist`: 0.6.10 → 0.6.11, CFBundleVersion 15 → 16
- [x] `BydStatsWidget/Info.plist`: 동일
- [x] `RELEASE_NOTES.md`: v0.6.11 섹션 추가 (데모 모드)
- [ ] 커밋
- [ ] 푸시

---

# feat(demo): 목업 데모 모드 추가 (App Store 심사 대응)

## 목적
로그인 없이 앱 UI·기능을 체험할 수 있는 데모 모드.
App Store 심사관이 BYD 계정 없이 앱을 검토할 수 있도록.

## 체크리스트
- [x] `VehicleStatus.swift`: `static var demo` 추가 (배터리 72%, 충전 중 7.2 kW)
- [x] `AppState.swift`: `isDemoMode`, `enterDemoMode()`, `exitDemoMode()` 추가
- [x] `DashboardView.swift`: "데모로 보기" 버튼 (비로그인 화면)
- [x] `DashboardView.swift`: 데모 모드 시 폴링 skip
- [x] `DashboardView.swift`: 오늘/이달 카드 가짜 수치 오버라이드
- [x] `DashboardView.swift`: 가짜 최근 충전 3개 카드
- [x] `SettingsView.swift`: 데모 모드 시 "데모 종료" 버튼
- [ ] 빌드 검증 (Xcode)
- [ ] 커밋

---

# fix(bgrefresh): BGAppRefresh + 포그라운드 폴링 동시 실행 세션 중복 방지 ✅ v0.6.10

## 문제
v0.6.9에서 `guard pollingTask == nil` 으로 포그라운드 Task 중복은 막았지만,
BGAppRefresh(`BackgroundTaskManager.handleRefresh`)는 별개 코드 경로라 guard 없이 독립 실행됨.
포그라운드 폴링 Task + BGAppRefresh가 거의 동시에 깨어나면 두 `SessionDetector` 인스턴스가
같은 SwiftData 컨텍스트에 동시 DataPoint 삽입 → 중복 DrivingSession 생성.

## 체크리스트
- [x] `AppState.doPoll`: 성공 시 `UserDefaults["lastForegroundPollDate"] = Date()` 기록
- [x] `BackgroundTaskManager.handleRefresh`: lastForegroundPollDate < 5분이면 세션 처리 skip
- [x] RELEASE_NOTES.md v0.6.10 추가
- [x] `androidApp/build.gradle.kts`: versionCode 10→11, versionName "0.6.9"→"0.6.10"
- [x] iOS Info.plist 버전 bump (v0.6.9→v0.6.10, CFBundleVersion 14→15)

---

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
