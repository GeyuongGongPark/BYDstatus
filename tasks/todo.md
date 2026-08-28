# AOS 충전 세션 기록 누락 수정

## 원인
폴링은 살아 있어도 `isCharging`이 `gl > 0`일 때만 true. 야간 완속은 gl=0으로 와서 DataPoint만 쌓이고 세션이 안 열림. `chargingState` API는 파서만 있고 폴링에서 미사용.

## 체크리스트
- [x] `VehicleStatus`에 보조 충전 플래그 + 판정 함수 (`gl` / `chargingState` / SOC)
- [x] `fetchChargingStatus` 파싱을 jsonInt로 통일, 주차 중 폴링에서 호출
- [x] `poll ok` 로그에 gl / isCharging / apiCharging 기록
- [x] 미완료 세션도 충전 탭에 표시
- [x] recover 강제종료 시 에너지·요금 계산
- [x] 단위 테스트 + 실행 (`:shared:testDebugUnitTest` `:androidApp:testDebugUnitTest` BUILD SUCCESSFUL)

## 범위 밖 (후속)
- FCM 기동 시 재등록, pollNow FGS 기동 — 이번 제보(새벽 폴링 정상)와 무관

## 검토
- 주차 중 폴링마다 `smartCharge/homePage` 1회 추가. 충전 API 실패 시 SOC 상승/유지를 보조 신호로 사용.
- 충전 완료 후 API가 false면 세션 종료. API 실패(null)이고 SOC가 그대로면 sticky라 100%에서 세션이 길게 남을 수 있음 — 앱 로그 `apiCharging`로 확인.
- 실차 야간 완속은 기기에서 `charging session started` / 배터리 이력 초록 구간으로 검증 필요.
