const { ApnsClient, Notification } = require('apns2');
const { unregisterToken } = require('./db');

let client = null;

function getClient() {
  if (!client) {
    client = new ApnsClient({
      team:       process.env.APNS_TEAM_ID,
      keyId:      process.env.APNS_KEY_ID,
      signingKey: process.env.APNS_KEY_P8.replace(/\\n/g, '\n'),
      defaultTopic: process.env.APNS_BUNDLE_ID,
      requestTimeout: 10000,
    });
  }
  return client;
}

/**
 * iOS silent push (content-available: 1)
 * - apns-push-type: background
 * - apns-priority: 5  (silent push는 반드시 5, 10이면 iOS가 무시)
 */
async function sendApns(tokens) {
  if (!tokens.length) return;
  const c = getClient();
  let ok = 0, fail = 0;

  for (const token of tokens) {
    try {
      const notif = new Notification(token, {
        aps: { 'content-available': 1 },
      });
      notif.priority  = 5;
      notif.pushType  = 'background';

      await c.send(notif);
      ok++;
    } catch (err) {
      fail++;
      const status = err.response?.statusCode ?? err.statusCode;
      console.error(`[apns] error token=${token.slice(-8)} status=${status} reason=${err.reason ?? err.message}`);

      // 410 Unregistered: 앱 삭제 → DB에서 제거
      if (status === 410) {
        await unregisterToken(token);
        console.log(`[apns] removed stale token ${token.slice(-8)}`);
      }
    }
  }
  console.log(`[apns] sent total=${tokens.length} ok=${ok} fail=${fail}`);
}

module.exports = { sendApns };
