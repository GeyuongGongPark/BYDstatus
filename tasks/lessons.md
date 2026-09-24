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

## AOS 충전 목록은 진행 중 세션을 숨기지 말 것
- `endTime == null` 필터는 “기록이 없다”와 구분되지 않는다. iOS는 미완료를 보여 준다.
