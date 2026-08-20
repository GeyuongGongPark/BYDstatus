const apn = require('apn');
const { unregisterToken } = require('./db');

let provider = null;

function getProvider() {
  if (!provider) {
    provider = new apn.Provider({
      token: {
        key:    process.env.APNS_KEY_P8.replace(/\\n/g, '\n'),
        keyId:  process.env.APNS_KEY_ID,
        teamId: process.env.APNS_TEAM_ID,
      },
      production: true,   // TestFlight/App Store는 production APNS 환경
    });
  }
  return provider;
}

/**
 * iOS silent push (content-available: 1)
 * - apns-push-type: background
 * - apns-priority: 5  (silent push는 반드시 5, 10이면 iOS가 무시)
 */
async function sendApns(tokens) {
  if (!tokens.length) return;
  const p = getProvider();

  const notif = new apn.Notification();
  notif.topic           = process.env.APNS_BUNDLE_ID;
  notif.priority        = 5;
  notif.pushType        = 'background';
  notif.contentAvailable = 1;

  const result = await p.send(notif, tokens);
  console.log(`[apns] sent total=${tokens.length} ok=${result.sent.length} fail=${result.failed.length}`);

  for (const f of result.failed) {
    console.error(`[apns] error token=${f.device.slice(-8)} reason=${f.response?.reason ?? f.error}`);
    // Unregistered: 앱 삭제 → DB에서 제거
    if (f.response?.reason === 'Unregistered') {
      await unregisterToken(f.device);
      console.log(`[apns] removed stale token ${f.device.slice(-8)}`);
    }
  }
}

module.exports = { sendApns };
