# 교훈

## 충전 세션은 전력(gl)만으로 판정하지 말 것
- 야간 완속은 차량이 잠기면 `gl=0`이어도 SOC가 오른다. `isCharging = gl > 0`이면 폴링이 살아 있어도 세션이 안 열린다.
- 충전 여부 공식 신호(`chargingState`)가 있으면 폴링에 넣고, 없거나 실패할 때만 SOC 변화를 보조로 쓴다.
- `poll ok` 로그에 판정 입력(gl, isCharging, apiCharging)을 남겨야 다음 제보를 로그로 가를 수 있다.

## apiIsCharging=false는 "충전 아님 확정"이 아니다
- BYD API는 완속 충전 중에도 `isCharging=false`를 반환하는 경우가 있다.
- 기존 로직은 `apiIsCharging==false`이면 `socUp`만 반환했는데, 5분 폴링 간격 내 SOC 변화가 없으면 `socUp=false` → 세션 시작 불가 / 직후 종료.
- 수정: `apiIsCharging` 값에 무관하게 SOC 상승 → 충전 시작, 이전 충전 중 + SOC 비하락 → 충전 유지로 통일.
- API false는 "불확실"로 취급하고 SOC 패턴으로만 판단. API true/gl>0만 확실한 양성 신호.

## instantPowerW>0 → isDriving=false는 회생제동 케이스도 잡는다
- 충전기 연결 판정을 위해 `instantPowerW > 0 → isDriving=false`를 추가하면, 주행 중 감속 시 speed=0이 되는 순간 gl이 양수(회생제동)이어도 isDriving=false → 충전 세션 오기록.
- 충전기 연결과 회생제동 구분: "이전 폴링에서 주행 중 + speed=0 + gl>0"이면 회생제동.
- 수정: `withDrivingResolved(previous)` 함수로 isDriving을 withChargingResolved 이전에 보정.
- withDrivingResolved는 withChargingResolved 반드시 이전에 호출해야 한다. 순서가 바뀌면 효과 없음.

## 충전 중 powerGear=3이어도 isDriving=true로 판정하지 말 것
- BYD 차량은 완속·급속충전 중에도 powerGear=3을 유지한다.
- `isDriving = powerGear == 3 || speed > 0`이면 충전기에 꽂혀 있어도 isDriving=true → 충전 세션 생성 불가.
- 올바른 우선순위: speed>0 → 주행, instantPowerW>0 → 충전(isDriving=false), 나머지 → powerGear==3 판단.
- 이 패턴은 iOS·Android 공통 적용 필요.

## 폴링 중복 실행은 세션을 쪼개거나 삭제한다 (iOS · Android 공통)
- iOS: SwiftUI `.task(id:)` modifier가 포그라운드 복귀 시마다 재실행 → `startPolling` 중복 호출 → 새 `SessionDetector` → 세션 분리
- Android: `PollingService.onStartCommand(ACTION_START)`가 앱 초기화 중 여러 번 호출 + `startCollecting()`이 비동기라 guard 없음 → 여러 DataCollector 동시 생성 → 직전 짧은 주행 세션 삭제
- iOS 방어: `startPolling`에서 `pollingTask != nil`이면 skip. `SessionDetector` 재사용.
- Android 방어: `ACTION_START`에서 `collectingJob.isActive`이면 skip. 설정 변경은 `ACTION_RESTART`로 분리.
- 로그 진단: iOS에서 `vehicleRealTimeRequest`가 쌍으로 나오면 복수 Task 실행 중. Android에서 "PollingService 시작"이 연속 여러 번이면 중복.

## AOS 충전 목록은 진행 중 세션을 숨기지 말 것
- `endTime == null` 필터는 “기록이 없다”와 구분되지 않는다. iOS는 미완료를 보여 준다.

## todo.md는 구현 전에 작성하고, 완료 시 즉시 표시할 것
- CLAUDE.md 규칙: 계획 먼저 → 검토 → 구현 → 완료 표시 순서
- 반복적으로 어기는 패턴: 구현을 먼저 시작하고 todo.md를 나중에(또는 사용자 지적 후) 업데이트
- 올바른 순서: (1) tasks/todo.md에 체크리스트 작성 → (2) 계획 검토 → (3) 구현 → (4) 각 항목 완료 즉시 `[x]` 표시
- Auto mode여도 예외 없음. 코드 수정 전 반드시 todo.md 먼저.

## isPolling/로딩 플래그는 defer로 해제할 것
- early return(guard, if-return 등)이 있는 함수에서 시작 시 플래그를 세우고 끝에 해제하면, early return 시 플래그가 해제되지 않아 무한 스피너가 발생한다.
- iOS: `isPolling = true` 후 `guard batteryPercentage > 0 else { return }` → `isPolling = false` 미실행.
- Android: `soc=0` → return → `_currentStatus = null` 유지 → CircularProgressIndicator 무한.
- 수정: iOS는 `defer { isPolling = false }`, Android는 null 상태일 때 에러 메시지 설정으로 로딩 탈출.
- **원칙**: 폴링/로딩 플래그는 항상 `defer`로 해제. early return 경로를 모두 점검할 것.

## 앱 코드와 CI(release.yml)는 항상 함께 업데이트할 것
- 새 환경변수(BuildConfig 필드, Info.plist 키 등)를 앱에 추가할 때 `.github/workflows/release.yml`에도 해당 secret 주입을 반드시 같은 커밋에 추가해야 한다.
- 누락되면 CI 빌드 APK에 빈 값이 들어가 조용히 실패함 — 사용자도, 서버도 오류를 인식하지 못함.
- 실제 사례: `PushRegistrar.kt` 추가 시 `PUSH_API_KEY`를 `release.yml`에 누락 → 모든 CI 빌드 APK의 토큰 등록이 8월부터 전부 실패.
- todo.md 체크리스트에 항상 포함: `[ ] release.yml에 관련 secret 추가`
