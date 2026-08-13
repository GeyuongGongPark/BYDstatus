# BYDstatus

BYD 공식 앱에 없는 배터리 이력, 충전 세션, 주행 효율, 월별 비용 분석을 제공하는 iOS 통계 앱.

---

## 기능

| 기능 | 설명 |
|------|------|
| 배터리 이력 그래프 | 시간대별 배터리 % 변화, 충전/주행/주차 구간 색상 구분 |
| 충전 세션 트래킹 | 자동 감지 · 충전량(kWh) · 충전 시간 · 추정 비용 · 위치 |
| 주행 세션 분석 | 소비 에너지 · 주행 거리 · 전비(km/kWh) |
| 월별 비용 통계 | 월별 충전량 · 충전 횟수 · 전기 비용 · 전년 동월 비교 |
| 대시보드 | 현재 배터리 % · 오늘 요약 · 이번 달 누적 비용 |
| 백그라운드 수집 | BGAppRefreshTask로 앱이 꺼져 있어도 데이터 수집 |

## 지원 차종

| 차종 | 배터리 용량 |
|------|------------|
| 아토 3 | 60.48 kWh |
| 돌핀 Standard | 44.9 kWh |
| 돌핀 Extended | 60.4 kWh |
| 씰 | 82.56 kWh |
| 씨라이언 7 | 82.56 kWh |
| 씨라이언 6 | 87.23 kWh |

## 요구사항

- iOS 17 이상
- BYD 한국 계정 (공식 앱 로그인 계정)
- Xcode 15 이상 (직접 빌드 시)

## 기술 스택

- **UI** — SwiftUI
- **데이터 저장** — SwiftData
- **차트** — Swift Charts
- **백그라운드** — BGAppRefreshTask
- **위치** — CoreLocation

## 빌드 방법

```bash
# 의존성: xcodegen
brew install xcodegen

git clone https://github.com/GeyuongGongPark/BYDstatus.git
cd BYDstatus
xcodegen generate
open BYDstatus.xcodeproj
```

Xcode에서 시뮬레이터 또는 실기기를 선택하고 빌드.

## 설정 항목

| 설정 | 기본값 |
|------|--------|
| BYD 계정 (이메일/비밀번호) | — |
| 차종 선택 | — |
| 전기요금 단가 | 180 원/kWh |
| 심야 요금 단가 | 120 원/kWh |
| 심야 시간대 | 23:00 ~ 09:00 |
| 폴링 간격 | 5분 |

## 데이터 소스

BYD 공식 API를 통해 실시간 차량 데이터를 수집합니다. 차량 GPS 에러(6051)로 인해 위치는 iOS CoreLocation을 사용합니다.

## 면책 사항

BYDstatus는 BYD와 무관한 비공식 오픈 소스 프로젝트입니다. BYD API 정책 변경으로 동작하지 않을 수 있습니다.

## 라이선스

MIT
