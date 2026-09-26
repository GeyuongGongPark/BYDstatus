# fix(polling): iOS 주행 세션 7분 단위 분리 버그 수정

## 문제
앱 백그라운드→포그라운드 전환 시 DashboardView의 `.task(id:)` modifier가 재실행되면서
`startPolling`이 중복 호출됨. 매 호출마다 새 SessionDetector가 생성되어 기존 주행 세션
컨텍스트가 단절 → 7분 단위 세션 분리.

로그 증거 (09-24):
- 21:09:18: vehicleRealTimeRequest 1개 (첫 시작)
- 21:15:16/17: vehicleRealTimeRequest 2개 (두 번째 startPolling 호출)
- 21:45:57/59/59: vehicleRealTimeRequest 3개 (세 번째 호출!)
- polling 간격 5분 (isDriving=false로 잘못 판정됨)

## 수정 방향
1. `startPolling`: pollingTask가 이미 실행 중이면 skip
2. `startPolling`: sessionDetector 재사용 (`??` 연산자)
3. `startPolling` 내 Task에 `defer { pollingTask = nil }` 추가
4. `selectVin`: VIN 변경 시 stopPolling() 먼저 호출

## 체크리스트
- [ ] `AppState.swift`: startPolling 중복 실행 방지
- [ ] `AppState.swift`: selectVin에서 stopPolling() 먼저 호출
- [ ] 커밋: `fix(polling): prevent duplicate polling tasks on foreground restore (iOS)`
- [ ] RELEASE_NOTES.md 업데이트 (v0.6.9? 또는 v0.6.8 버그 추가)
- [ ] tasks/lessons.md 업데이트
