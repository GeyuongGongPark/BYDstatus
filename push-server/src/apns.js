const http2 = require('http2');
const jwt   = require('jsonwebtoken');
const { unregisterToken } = require('./db');

const APNS_HOST = 'https://api.push.apple.com';

function buildKey() {
  return process.env.APNS_KEY_P8
    .replace(/\\\\n/g, '\n')
    .replace(/\\n/g, '\n');
}

function makeJwt() {
  return jwt.sign(
    { iss: process.env.APNS_TEAM_ID },
    buildKey(),
    {
      algorithm: 'ES256',
      keyid: process.env.APNS_KEY_ID,
      expiresIn: '50m',
    }
  );
}

/**
 * 단일 token에 silent push 전송.
 */
function sendOne(token, jwtToken) {
  return new Promise((resolve) => {
    const client = http2.connect(APNS_HOST);
    client.on('error', (err) => {
      console.error(`[apns] http2 connect error: ${err.message}`);
      resolve({ ok: false, reason: err.message });
    });

    const body = JSON.stringify({ aps: { 'content-available': 1 } });
    const req = client.request({
      ':method':        'POST',
      ':path':          `/3/device/${token}`,
      'authorization':  `bearer ${jwtToken}`,
      'apns-push-type': 'background',
      'apns-priority':  '5',
      'apns-topic':     process.env.APNS_BUNDLE_ID,
      'content-type':   'application/json',
      'content-length': Buffer.byteLength(body),
    });

    let status;
    req.on('response', (headers) => { status = headers[':status']; });

    let data = '';
    req.setEncoding('utf8');
    req.on('data', (chunk) => { data += chunk; });
    req.on('end', () => {
      client.close();
      if (status === 200) {
        resolve({ ok: true });
      } else {
        let reason = 'Unknown';
        try { reason = JSON.parse(data).reason; } catch (_) {}
        resolve({ ok: false, reason, status });
      }
    });

    req.write(body);
    req.end();
  });
}

/**
 * iOS silent push (content-available: 1)
 * - apns-push-type: background
 * - apns-priority: 5  (silent push는 반드시 5, 10이면 iOS가 무시)
 */
async function sendApns(tokens) {
  if (!tokens.length) return;

  let jwtToken;
  try {
    jwtToken = makeJwt();
  } catch (err) {
    console.error(`[apns] JWT 생성 실패: ${err.message}`);
    return;
  }

  let ok = 0, fail = 0;
  for (const token of tokens) {
    const result = await sendOne(token, jwtToken);
    if (result.ok) {
      ok++;
    } else {
      fail++;
      console.error(`[apns] error token=${token.slice(-8)} status=${result.status} reason=${result.reason}`);
      if (result.reason === 'Unregistered') {
        await unregisterToken(token);
        console.log(`[apns] removed stale token ${token.slice(-8)}`);
      }
    }
  }
  console.log(`[apns] sent total=${tokens.length} ok=${ok} fail=${fail}`);
}

module.exports = { sendApns };
