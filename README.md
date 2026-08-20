# BYD Stats

BYD 공식 앱에 없는 배터리 이력, 충전 세션, 주행 효율, 월별 비용 분석을 제공하는 iOS · Android 통계 앱.

**현재 버전: v0.4.0**

---

## 기능

| 기능 | iOS | Android |
|------|:---:|:-------:|
| 대시보드 (배터리 % · 오늘 요약 · 월간 통계) | ✓ | ✓ |
| 배터리 이력 그래프 (충전/주행/주차 구간, 기간 필터) | ✓ | ✓ |
| 충전 세션 자동 감지 (충전량 · 비용 · 시간) | ✓ | ✓ |
| 주행 세션 분석 (소비 kWh · GPS/ODO 거리 · 전비) | ✓ | ✓ |
| 홈 화면 위젯 | ✓ | ✓ |
| GPS 트래킹 | ✓ | ✓ |
| 어댑티브 폴링 (주행 1분 / 충전 2분 / 주차 설정값) | ✓ | ✓ |
| 라이브 액티비티 (Dynamic Island · 잠금화면) | ✓ | — |
| 백그라운드 수집 (앱 종료 시에도 수집) | ✓ (BGAppRefresh + Silent Push) | ✓ (Foreground Service + FCM) |
| 앱 로그 / 진단 (공유 가능) | — | ✓ |

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

| | iOS | Android |
|--|-----|---------|
| 최소 버전 | iOS 17 | Android 8.0 (API 26) |
| 계정 | BYD 공식 앱 계정 | BYD 공식 앱 계정 |
| 빌드 도구 | Xcode 16+ | — (APK 직접 설치) |

## 설치

### iOS
```bash
brew install xcodegen

git clone https://github.com/GeyuongGongPark/BYDstatus.git
cd BYDstatus
xcodegen generate
open BydStats.xcodeproj
```
Xcode에서 시뮬레이터 또는 실기기를 선택하고 빌드.

### Android
[GitHub Releases](https://github.com/GeyuongGongPark/BYDstatus/releases)에서 최신 APK를 다운로드하여 설치.

> 기기 설정 → 보안 → "알 수 없는 앱 설치" 허용 필요

## 기술 스택

### iOS
- **UI** — SwiftUI · Swift Charts
- **데이터** — SwiftData
- **백그라운드** — BGAppRefreshTask · ActivityKit · Silent Push (APNS)
- **위치** — CoreLocation

### Android
- **UI** — Jetpack Compose · Material3
- **데이터** — Room · DataStore
- **백그라운드** — Foreground Service · FCM Silent Push
- **위젯** — Glance AppWidget
- **네트워크** — Ktor
- **위치** — FusedLocationProviderClient
- **공유 로직** — Kotlin Multiplatform (KMP)

## 설정 항목

| 설정 | 기본값 |
|------|--------|
| BYD 계정 (이메일/비밀번호) | — |
| 차종 선택 | — |
| 전기요금 단가 | 180 원/kWh |
| 폴링 간격 (주차 중) | 5분 |

## 데이터 소스

BYD 공식 API를 통해 실시간 차량 데이터를 수집합니다. 차량 GPS 오류(6051)로 인해 위치는 기기 GPS를 사용합니다.

## 면책 사항

BYD Stats는 BYD와 무관한 비공식 오픈 소스 프로젝트입니다. BYD API 정책 변경으로 동작하지 않을 수 있습니다.

## 라이선스

MIT
