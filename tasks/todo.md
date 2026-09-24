# fix(isDriving): 회생제동 후 speed=0일 때 충전 세션 오기록 방지 (iOS · Android)

## 문제
v0.6.7 수정(`instantPowerW > 0 → isDriving=false`)이 회생제동 케이스도 잡음.
주행 중 감속하면서 speed=0이 되는 순간 gl이 양수(회생제동)이면 isDriving=false → 충전 세션 시작.
실제 로그: 10:37 주행 중 → 10:38 speed=0 + gl=+7907W → 충전 세션 시작 → 10:40 다시 주행.

## 수정 방향
`withDrivingResolved(previous, apiIsCharging)` 함수 추가.
조건: 이전 주행 중 + speed=0 + gl>0 + apiCharging≠true → 회생제동으로 판단 → isDriving=true 유지.

## 체크리스트
- [ ] `shared/Models.kt`: `reportedDriving: Boolean? = null` 필드 추가, isDriving에서 우선 사용
- [ ] `shared/ChargingResolve.kt` (또는 새 파일): `withDrivingResolved()` 함수 추가
- [ ] `DataCollector.kt`: withChargingResolved 이전에 withDrivingResolved 호출
- [ ] `BydStats/Models/VehicleStatus.swift`: `resolvedDriving: Bool? = nil` 추가, isDriving에서 우선 사용
- [ ] `BydVehicleService.swift`: withDrivingResolved 호출 (iOS)
- [ ] 커밋: `fix(isDriving): treat regen braking as driving when speed=0 and gl>0 (iOS·Android)`
- [ ] RELEASE_NOTES.md v0.6.7에 항목 추가
- [ ] tasks/lessons.md 업데이트
