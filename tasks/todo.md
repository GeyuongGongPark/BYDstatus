# feat(play-store): Google Play 스토어 제출 대응

## 문제
- `AppUpdate.kt` / `startUpdateDownload()` : GitHub APK 직접 다운로드·설치 → Play 정책 위반
- `REQUEST_INSTALL_PACKAGES` 권한 → Play 정책 금지
- `PrefKeys.PASSWORD` DataStore 평문 저장 → 보안 정책 위반 위험

## 체크리스트

### [1] APK 자체 업데이트 기능 제거
- [x] `AppUpdate.kt` 파일 삭제
- [x] `AppUpdateDialog.kt` 파일 삭제
- [x] `AppUpdateTest.kt` 파일 삭제
- [x] `res/xml/file_paths.xml` 파일 삭제 (APK 캐시용)
- [x] `AndroidManifest.xml`: `REQUEST_INSTALL_PACKAGES` 권한 제거
- [x] `AndroidManifest.xml`: FileProvider 제거
- [x] `AppViewModel.kt`: import AppRelease, AppUpdate 제거
- [x] `AppViewModel.kt`: PrefKeys.LAST_UPDATE_CHECK, SKIPPED_UPDATE 제거
- [x] `AppViewModel.kt`: UpdateUiState 클래스 제거
- [x] `AppViewModel.kt`: _updateState, updateState, downloadJob 필드 제거
- [x] `AppViewModel.kt`: checkForUpdate() 함수 제거
- [x] `AppViewModel.kt`: startUpdateDownload() 함수 제거
- [x] `AppViewModel.kt`: dismissUpdate() 함수 제거
- [x] `AppViewModel.kt`: clearAlreadyLatest() 함수 제거
- [x] `AppViewModel.kt`: init에서 checkForUpdate(force=false) 호출 제거
- [x] `MainNavHost.kt`: updateState 구독 및 AppUpdateDialog 렌더링 제거
- [x] `SettingsScreen.kt`: updateState 구독 및 "앱 업데이트" 섹션 제거
- [x] 컴파일 검증 (`compileReleaseKotlin` BUILD SUCCESSFUL)

### [2] 비밀번호 암호화 저장
- [x] `libs.versions.toml` + `build.gradle.kts`: `security-crypto:1.0.0` 의존성 추가
- [x] `SecureStorage.kt` 생성 — EncryptedSharedPreferences 래퍼 (AES256-GCM)
- [x] `AppViewModel.kt`: `loadSettings()` username/password → SecureStorage에서 읽기
- [x] `AppViewModel.kt`: `saveCredentials()` → SecureStorage에 쓰기
- [x] `AppViewModel.kt`: `logout()` → `SecureStorage.clear()` 추가
- [x] `AppViewModel.kt`: `PrefKeys.USERNAME`, `PrefKeys.PASSWORD` DataStore 키 제거
- [x] `PollingService.kt`: username/password → SecureStorage에서 읽기
- [x] 컴파일 검증 (`compileReleaseKotlin` BUILD SUCCESSFUL)

### [3] 빌드 검증
- [ ] `./gradlew :androidApp:assembleRelease` 성공 확인

---

## 검토
(작업 완료 후 작성)

---

# chore: bump version to 0.6.12 (iOS·Android)

## 체크리스트
- [x] `BydStats/Resources/Info.plist`: 0.6.11 → 0.6.12, CFBundleVersion 16 → 17
- [x] `BydStatsWidget/Info.plist`: 동일
- [x] `androidApp/build.gradle.kts`: versionName "0.6.10" → "0.6.12", versionCode 11 → 12
- [x] `RELEASE_NOTES.md`: v0.6.12 섹션 추가
- [x] 커밋 & 푸시

---

# fix(android): 태그 버전 suffix(_hotfix 등) 인식 오류 수정 ✅

## 문제
`v0.6.12_hotfix` 태그로 배포 시 앱 내 업데이트 안내가 미표시.
`normalizeVersion`이 `_` 구분자를 처리하지 않아 `12_hotfix` → `toIntOrNull()` = null → 0 으로 인식.

## 체크리스트
- [x] `AppUpdate.kt`: `substringBefore("_")` 추가
- [x] 커밋 & 푸시

---

# fix(polling): soc=0 연속 발생 시 에러 메시지 표시 (iOS·Android)

## 체크리스트
- [x] `AppState.swift`: `socZeroCount` 카운터 추가, 3회 연속 시 `pollError` 설정
- [x] `DataCollector.kt`: 동일하게 카운터 추가, 3회 연속 시 `_error` 설정
- [x] 커밋 & 푸시

---

# fix(android): PUSH_API_KEY CI 누락 수정 + 세션 복원 시 토큰 등록

## 체크리스트
- [x] `release.yml`: Build APK step에 `PUSH_API_KEY` 추가
- [x] `androidApp/build.gradle.kts`: `PUSH_API_KEY` 환경변수 폴백 추가
- [x] `AppViewModel.loadSettings()`: 세션 복원 완료 후 `registerFcmToken()` 호출
- [x] 커밋 & 푸시

---

# feat(demo): 목업 데모 모드 추가 (App Store 심사 대응)

## 체크리스트
- [x] `VehicleStatus.swift`: `static var demo` 추가
- [x] `AppState.swift`: `isDemoMode`, `enterDemoMode()`, `exitDemoMode()` 추가
- [x] `DashboardView.swift`: "데모로 보기" 버튼 등
- [x] `SettingsView.swift`: 데모 모드 시 "데모 종료" 버튼

---

# fix(bgrefresh): BGAppRefresh + 포그라운드 폴링 동시 실행 세션 중복 방지 ✅ v0.6.10

## 체크리스트
- [x] `AppState.doPoll`: `lastForegroundPollDate` 기록
- [x] `BackgroundTaskManager.handleRefresh`: 5분 이내 skip
- [x] iOS/Android 버전 bump

---

# fix(isDriving): 회생제동 후 speed=0일 때 충전 세션 오기록 방지 ✅ v0.6.8

## 체크리스트
- [x] `shared/Models.kt`: `reportedDriving` 추가
- [x] `shared/ChargingResolve.kt`: `withDrivingResolved()` 추가
- [x] iOS/Android 모두 적용
