require('dotenv').config();
const express = require('express');
const cron    = require('node-cron');
const { initDB, registerToken, unregisterToken, getTokensByPlatform, cleanOldTokens } = require('./db');
const { sendApns } = require('./apns');
const { sendFcm  } = require('./fcm');

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
  const { token, platform } = req.body;
  if (!token || !['ios', 'android'].includes(platform)) {
    return res.status(400).json({ error: 'token and platform(ios|android) required' });
  }
  await registerToken(token, platform);
  console.log(`[api] registered token=${token.slice(-8)} platform=${platform}`);
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

// ─── Push 전송 ────────────────────────────────────────────────────────────────

async function sendPushToAll() {
  const ts = new Date().toISOString();
  console.log(`[cron] push start at ${ts}`);
  try {
    const [iosTokens, androidTokens] = await Promise.all([
      getTokensByPlatform('ios'),
      getTokensByPlatform('android'),
    ]);
    await Promise.all([
      sendApns(iosTokens),
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

initDB().then(() => {
  app.listen(PORT, () => {
    console.log(`[server] listening on port ${PORT}`);
    // 시작 직후 1회 즉시 전송 (서버 재시작 후 빠른 복구)
    sendPushToAll();
  });
}).catch(err => {
  console.error('[startup] DB init failed:', err.message);
  process.exit(1);
});
