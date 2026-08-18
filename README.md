# BYD Stats

BYD 공식 앱에 없는 배터리 이력, 충전 세션, 주행 효율, 월별 비용 분석을 제공하는 iOS 통계 앱.

**현재 버전: v0.3.1**

---

## 기능

| 기능 | 설명 |
|------|------|
| 대시보드 | 배터리 % · 오늘 요약 · 이번 달 충전 비용/충전량/주행거리/소비 |
| 배터리 이력 그래프 | 시간대별 배터리 % 변화, 충전/주행/주차 구간 색상 구분, 날짜 직접 선택, 최고·최저·평균 통계 |
| 충전 세션 트래킹 | 자동 감지 · 충전량(kWh) · 충전 시간 · 추정 비용 · 월간 필터 · 요약 카드 |
| 주행 세션 분석 | SOC 기반 소비(kWh) · GPS/ODO 거리 · 전비(km/kWh) · 월간 필터 · 일별 바차트 |
| 라이브 액티비티 | 주행·충전 중 Dynamic Island + 잠금화면 실시간 표시 |
| 홈 화면 위젯 | Small · Medium 두 가지 크기, 15분마다 자동 갱신 |
| 어댑티브 폴링 | 주행 중 1분 / 충전 중 2분 / 주차 중 설정값 간격으로 자동 조절 |
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
- Xcode 16 이상 (직접 빌드 시)

## 기술 스택

- **UI** — SwiftUI
- **데이터 저장** — SwiftData
- **차트** — Swift Charts
- **라이브 액티비티** — ActivityKit
- **백그라운드** — BGAppRefreshTask
- **위치** — CoreLocation

## 빌드 방법

```bash
# 의존성: xcodegen
brew install xcodegen

git clone https://github.com/GeyuongGongPark/BYDstatus.git
cd BYDstatus
xcodegen generate
open BydStats.xcodeproj
```

Xcode에서 시뮬레이터 또는 실기기를 선택하고 빌드.

## 설정 항목

| 설정 | 기본값 |
|------|--------|
| BYD 계정 (이메일/비밀번호) | — |
| 차종 선택 | — |
| 전기요금 단가 | 180 원/kWh |
| 폴링 간격 | 5분 |
| GPS 트래킹 | 켜짐 |

## 데이터 소스

BYD 공식 API를 통해 실시간 차량 데이터를 수집합니다. 차량 GPS 에러(6051)로 인해 위치는 iOS CoreLocation을 사용합니다.

## 면책 사항

BYD Stats는 BYD와 무관한 비공식 오픈 소스 프로젝트입니다. BYD API 정책 변경으로 동작하지 않을 수 있습니다.

## 라이선스

MIT
