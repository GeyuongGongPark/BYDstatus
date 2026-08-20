const { unregisterToken } = require('./db');

let messaging = null;

function getMessaging() {
  if (!messaging) {
    const admin = require('firebase-admin');
    if (!admin.apps.length) {
      const serviceAccount = JSON.parse(process.env.FCM_SERVICE_ACCOUNT);
      admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
    }
    messaging = admin.messaging();
  }
  return messaging;
}

/**
 * Android silent data message (FCM)
 * - notification 키 없음 (data-only = silent)
 * - android.priority: high → Doze 모드 우회
 */
async function sendFcm(tokens) {
  if (!tokens.length) return;

  // FCM_SERVICE_ACCOUNT가 없으면 Android push는 생략
  if (!process.env.FCM_SERVICE_ACCOUNT) {
    console.log('[fcm] FCM_SERVICE_ACCOUNT not set, skipping');
    return;
  }

  const msg = getMessaging();
  let ok = 0, fail = 0;

  // FCM은 최대 500개 batch 전송 가능
  for (let i = 0; i < tokens.length; i += 500) {
    const batch = tokens.slice(i, i + 500);
    try {
      const res = await msg.sendEachForMulticast({
        tokens: batch,
        data:   { type: 'poll' },
        android: { priority: 'high' },
      });
      ok   += res.successCount;
      fail += res.failureCount;

      // 유효하지 않은 token 정리
      for (let j = 0; j < res.responses.length; j++) {
        const r = res.responses[j];
        if (!r.success) {
          const code = r.error?.code;
          if (code === 'messaging/registration-token-not-registered' ||
              code === 'messaging/invalid-registration-token') {
            await unregisterToken(batch[j]);
            console.log(`[fcm] removed stale token ${batch[j].slice(-8)}`);
          } else {
            console.error(`[fcm] error token=${batch[j].slice(-8)} code=${code}`);
          }
        }
      }
    } catch (err) {
      console.error('[fcm] batch error:', err.message);
    }
  }
  console.log(`[fcm] sent total=${tokens.length} ok=${ok} fail=${fail}`);
}

module.exports = { sendFcm };
