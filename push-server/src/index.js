const express = require('express');
const cron    = require('node-cron');
const { initDB, registerToken, unregisterToken, getIosTokens, getAndroidTokens, cleanOldTokens,
        upsertSession, updateSessionTokens, getAllSessions } = require('./db');
const { sendApns } = require('./apns');
const { sendFcm  } = require('./fcm');
const mqttMgr     = require('./mqtt');

// ─── 환경변수 검증 ────────────────────────────────────────────────────────────

const REQUIRED = ['DATABASE_URL', 'API_KEY', 'APNS_TEAM_ID', 'APNS_KEY_ID', 'APNS_KEY_P8', 'APNS_BUNDLE_ID'];
for (const key of REQUIRED) {
  if (!process.env[key]) {
    console.error(`[startup] Missing required env var: ${key}`);
    process.exit(1);
  }
}

// ─── Express ─────────────────────────────────────────────────────────────────

const app = express();
app.use(express.json());

const auth = (req, res, next) => {
  const key = req.headers['authorization']?.replace('Bearer ', '');
  if (key !== process.env.API_KEY) return res.status(401).json({ error: 'Unauthorized' });
  next();
};

app.get('/health', (_, res) => res.json({ ok: true, ts: new Date().toISOString() }));

/** 앱 → 서버: device token 등록/갱신 */
app.post('/api/register', auth, async (req, res) => {
  const { token, platform, sandbox } = req.body;
  if (!token || !['ios', 'android'].includes(platform)) {
    return res.status(400).json({ error: 'token and platform(ios|android) required' });
  }
  const isSandbox = sandbox === '1' || sandbox === true;
  await registerToken(token, platform, isSandbox);
  console.log(`[api] registered token=${token.slice(-8)} platform=${platform} sandbox=${isSandbox}`);
  res.json({ ok: true });
});

/** 앱 → 서버: token 삭제 (로그아웃 시) */
app.delete('/api/unregister', auth, async (req, res) => {
  const { token } = req.body;
  if (!token) return res.status(400).json({ error: 'token required' });
  await unregisterToken(token);
  console.log(`[api] unregistered token=${token.slice(-8)}`);
  res.json({ ok: true });
});

/** 앱 → 서버: BYD 세션 등록 (로그인 후) */
app.post('/api/session/register', auth, async (req, res) => {
  const { user_id, sign_token, encry_token, broker_host, broker_port, vin } = req.body;
  if (!user_id || !sign_token || !encry_token || !broker_host) {
    return res.status(400).json({ error: 'user_id, sign_token, encry_token, broker_host required' });
  }
  await upsertSession(user_id, sign_token, encry_token, broker_host, broker_port || 8883, vin || null);
  mqttMgr.connect({ user_id, sign_token, encry_token, broker_host, broker_port: broker_port || 8883 });
  console.log(`[api] session registered user=${user_id.slice(-6)}`);
  res.json({ ok: true });
});

/** 앱 → 서버: BYD 세션 토큰 갱신 (onSessionUpdated 시) */
app.put('/api/session/update', auth, async (req, res) => {
  const { user_id, sign_token, encry_token } = req.body;
  if (!user_id || !sign_token || !encry_token) {
    return res.status(400).json({ error: 'user_id, sign_token, encry_token required' });
  }
  await updateSessionTokens(user_id, sign_token, encry_token);
  // DB에서 전체 세션 읽어 재연결 (broker_host 필요)
  const sessions = await getAllSessions();
  const session  = sessions.find(s => s.user_id === user_id);
  if (session) mqttMgr.reconnect(session);
  console.log(`[api] session updated user=${user_id.slice(-6)}`);
  res.json({ ok: true });
});

// ─── MQTT 이벤트 → 즉시 Push ─────────────────────────────────────────────────

mqttMgr.setOnPush(async (userId, event, _msg) => {
  console.log(`[mqtt] push triggered by event=${event} user=${userId.slice(-6)}`);
  await sendPushToAll();
});

// ─── Push 전송 ────────────────────────────────────────────────────────────────

async function sendPushToAll() {
  const ts = new Date().toISOString();
  console.log(`[cron] push start at ${ts}`);
  try {
    const [iosProd, iosSandbox, androidTokens] = await Promise.all([
      getIosTokens(false),
      getIosTokens(true),
      getAndroidTokens(),
    ]);
    await Promise.all([
      sendApns(iosProd, false),
      sendApns(iosSandbox, true),
      sendFcm(androidTokens),
    ]);
  } catch (err) {
    console.error('[cron] push error:', err.message);
  }
}

// ─── Cron ─────────────────────────────────────────────────────────────────────

// 5분마다 push
cron.schedule('*/5 * * * *', sendPushToAll);

// 매주 일요일 자정 — 90일 이상 미갱신 token 정리
cron.schedule('0 0 * * 0', () => cleanOldTokens(90));

// ─── 서버 시작 ────────────────────────────────────────────────────────────────

const PORT = process.env.PORT || 3000;

initDB().then(async () => {
  // 저장된 세션으로 MQTT 연결 복구
  const sessions = await getAllSessions();
  if (sessions.length > 0) {
    console.log(`[startup] restoring ${sessions.length} MQTT session(s)`);
    mqttMgr.connectAll(sessions);
  }

  app.listen(PORT, () => {
    console.log(`[server] listening on port ${PORT}`);
    sendPushToAll();
  });
}).catch(err => {
  console.error('[startup] DB init failed:', err.message);
  process.exit(1);
});
