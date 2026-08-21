# BYD Stats — Push Server 구현 플랜

## 목표
iOS BGAppRefresh 한계 극복을 위해 서버에서 5분마다 Silent Push를 전송,
iOS/Android 앱이 백그라운드에서 깨어나 BYD API를 폴링하도록 함.

---

## 트래픽 분석 (100명 기준)

| 항목 | 수치 |
|------|------|
| 폴링 간격 | 5분 |
| 일일 push 횟수 | 100명 × 288회 = 28,800회 |
| 월 push 횟수 | ~864,000회 |
| APNS/FCM 비용 | 무료 |
| payload 크기 | iOS ~100B / Android ~100B |
| 서버 컴퓨팅 | 5분마다 DB 조회 + HTTP 요청 ~10ms |

→ Railway 컨테이너 최소 사양으로 충분 (예상 $3-5/월)

---

## 아키텍처

```
Railway (별도 프로젝트)
├── push-server (Node.js + Express)
│   ├── POST /api/register     ← 앱 → 서버 token 등록
│   ├── DELETE /api/unregister ← 앱 → 서버 token 삭제
│   └── GET  /health
├── node-cron (*/5 * * * *)
│   ├── iOS tokens  → APNS HTTP/2 (content-available: 1)
│   └── Android tokens → FCM Data Message
└── PostgreSQL (Railway add-on)
    └── device_tokens 테이블
```

---

## DB 스키마

```sql
CREATE TABLE device_tokens (
  id           SERIAL PRIMARY KEY,
  token        TEXT        NOT NULL UNIQUE,
  platform     VARCHAR(10) NOT NULL,  -- 'ios' | 'android'
  registered_at TIMESTAMPTZ DEFAULT NOW(),
  last_seen    TIMESTAMPTZ DEFAULT NOW()
);
```

- UNIQUE(token): 재등록 시 upsert 처리
- last_seen: 90일 이상 미갱신 token 자동 정리 (주간 cron)

---

## 서버 구현

### 패키지

```json
{
  "dependencies": {
    "express": "^4.x",
    "pg": "^8.x",
    "node-cron": "^3.x",
    "apns2": "^9.x",
    "firebase-admin": "^12.x"
  }
}
```

### API 인증
- 앱과 서버 간 공유 `API_KEY` (환경변수)
- 모든 요청: `Authorization: Bearer <API_KEY>` 헤더 필수

### APNS Silent Push payload
```json
{
  "aps": { "content-available": 1 }
}
```
- `apns-push-type: background`
- `apns-priority: 5` (silent 필수 — 10이면 iOS가 무시)

### FCM Data Message
```json
{
  "data": { "type": "poll" },
  "android": { "priority": "high" }
}
```
- notification 키 없음 (data-only = silent)
- priority: high → Foreground Service 없어도 앱 깨울 수 있음

---

## 환경변수 (Railway)

```
DATABASE_URL        = (Railway PostgreSQL 자동 주입)
API_KEY             = <앱과 공유하는 비밀키>

# APNS
APNS_KEY_P8         = <.p8 파일 내용 (개행 \n 처리)>
APNS_KEY_ID         = <Key ID, Apple Developer에서 확인>
APNS_TEAM_ID        = <Team ID, Apple Developer에서 확인>
APNS_BUNDLE_ID      = com.ggpark.BydStats

# FCM
FCM_SERVICE_ACCOUNT = <firebase service account JSON (문자열)>
```

---

## iOS 앱 변경사항

### 1. Info.plist / project.yml
```yaml
UIBackgroundModes:
  - remote-notification
```

### 2. AppDelegate (UIApplicationDelegateAdaptor)
```swift
// device token 발급 → 서버 등록
func application(_ app: UIApplication,
                 didRegisterForRemoteNotificationsWithDeviceToken token: Data) {
    let tokenStr = token.map { String(format: "%02x", $0) }.joined()
    PushRegistrar.register(token: tokenStr)
}

// silent push 수신 → 폴링
func application(_ app: UIApplication,
                 didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                 fetchCompletionHandler handler: @escaping (UIBackgroundFetchResult) -> Void) {
    Task {
        await appState.pollNow(modelContext: ...)
        handler(.newData)
    }
}
```

### 3. 앱 시작 시 remote notification 등록
```swift
UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { _, _ in
    DispatchQueue.main.async { UIApplication.shared.registerForRemoteNotifications() }
}
```

---

## Android 앱 변경사항

### 1. Firebase 프로젝트 생성 → google-services.json 추가

### 2. build.gradle.kts 의존성 추가
```kotlin
implementation(libs.firebase.messaging)
```

### 3. FirebaseMessagingService 구현
```kotlin
class BydFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] == "poll") {
            // PollingService에 즉시 폴링 신호
            PollingService.triggerImmediatePoll(this)
        }
    }
    override fun onNewToken(token: String) {
        PushRegistrar.register(context = this, token = token, platform = "android")
    }
}
```

---

## iOS vs Android 비교

| 항목 | iOS (APNS) | Android (FCM) |
|------|-----------|---------------|
| 배경 실행 보장 | Silent push 수신 시 ~30초 실행 | FCM high priority = 거의 즉시 |
| 신뢰성 | 저전력 모드에서도 동작 | Doze 모드 우회 가능 |
| 설정 필요 | APNs Auth Key | Firebase 프로젝트 |
| 앱 변경 | AppDelegate + UIBackgroundModes | FirebaseMessagingService |
| 기존 Foreground Service | 해당 없음 | 병행 운용 (push = 즉시 폴링 트리거) |

---

## 구현 순서

### Phase 1: 서버 (Railway)
- [ ] Railway 새 프로젝트 생성
- [ ] PostgreSQL add-on 추가
- [ ] Node.js push-server 구현
  - [ ] Express API (register/unregister/health)
  - [ ] APNS 연동 (apns2)
  - [ ] FCM 연동 (firebase-admin)
  - [ ] node-cron 5분 스케줄러
  - [ ] 90일 만료 token 정리 cron
- [ ] 환경변수 설정
- [ ] 배포 및 동작 확인

### Phase 2: iOS 앱
- [ ] UIBackgroundModes remote-notification 추가 (project.yml)
- [ ] AppDelegate 구현 (UIApplicationDelegateAdaptor)
- [ ] PushRegistrar 유틸리티 (서버 token 등록/삭제)
- [ ] BydStatsApp에서 registerForRemoteNotifications 호출
- [ ] silent push 수신 시 pollNow 연동

### Phase 3: Android 앱
- [ ] Firebase 프로젝트 생성 및 google-services.json 추가
- [ ] FCM 의존성 추가
- [ ] BydFirebaseMessagingService 구현
- [ ] PollingService에 triggerImmediatePoll 추가
- [ ] PushRegistrar 구현

### Phase 4: 검증
- [ ] APNS sandbox 환경에서 iOS silent push 수신 확인
- [ ] FCM 테스트 메시지로 Android 폴링 확인
- [ ] 새벽 시간대 DataPoint 실제 적재 확인

---

## 비용 예측

| 항목 | 비용 |
|------|------|
| Railway Node.js 서비스 | ~$2-3/월 |
| Railway PostgreSQL | 무료 (1GB 이하) |
| APNS | 무료 |
| FCM | 무료 |
| **합계** | **$2-3/월** |

---

## 사전 준비 사항 (구현 전 필요)

1. **Apple Developer** → Keys → 새 키 생성 (APNs 체크) → `.p8` 다운로드
   - Key ID 메모
   - Team ID 메모 (Membership 탭)
2. **Firebase Console** → 새 프로젝트 → Android 앱 추가 → `google-services.json` 다운로드
   - 프로젝트 설정 → 서비스 계정 → 새 비공개 키 생성 (서버용 JSON)
3. **API_KEY** 임의 생성 (ex: `openssl rand -hex 32`)
