/**
 * BYD MQTT 구독 관리
 *
 * - user_id별 MQTT 연결 1개 유지
 * - 페이로드: AES-128-CBC(IV=0), key=MD5(encry_token)
 * - 이벤트 수신 시 등록된 onPush 콜백으로 즉시 푸시 트리거
 */

const mqtt   = require('mqtt');
const crypto = require('crypto');

// user_id → mqtt.Client
const _clients = new Map();

let _onPush = null;

/** 푸시 트리거 콜백 등록 */
function setOnPush(cb) {
  _onPush = cb;
}

/** MD5 hex (소문자) */
function md5(str) {
  return crypto.createHash('md5').update(str).digest('hex');
}

/** AES-128-CBC decrypt (IV = 0x00*16) */
function aesDecrypt(cipherHex, keyHex) {
  const key = Buffer.from(keyHex, 'hex');
  const iv  = Buffer.alloc(16, 0);
  const ct  = Buffer.from(cipherHex, 'hex');
  const decipher = crypto.createDecipheriv('aes-128-cbc', key, iv);
  decipher.setAutoPadding(true);
  return Buffer.concat([decipher.update(ct), decipher.final()]).toString('utf8');
}

/** MQTT 연결 비밀번호 생성 */
function buildPassword(signToken, clientId, userId) {
  const ts   = Math.floor(Date.now() / 1000).toString();
  const base = `${signToken}${clientId}${userId}${ts}`;
  return `${ts}${md5(base)}`;
}

/** 단일 세션에 대한 MQTT 연결 시작/재시작 */
function connect(session) {
  const { user_id, sign_token, encry_token, broker_host, broker_port, client_id } = session;

  // 기존 연결 종료
  disconnect(user_id);

  const clientId = client_id || `oversea_${md5(user_id)}`;
  const password = buildPassword(sign_token, clientId, user_id);
  const topic    = `oversea/res/${user_id}`;
  const decryptKey = md5(encry_token); // MD5(encry_token) = AES 키

  console.log(`[mqtt] connecting user=${user_id.slice(-6)} host=${broker_host}:${broker_port} clientId=${clientId}`);

  const client = mqtt.connect(`mqtts://${broker_host}:${broker_port}`, {
    clientId,
    username:           user_id,
    password,
    protocolVersion:    5,     // BYD EMQ 브로커는 MQTTv5 사용
    rejectUnauthorized: false, // BYD 브로커 self-signed 대응
    reconnectPeriod:    30_000,
    connectTimeout:     15_000,
    keepalive:          120,
  });

  client.on('connect', () => {
    console.log(`[mqtt] connected user=${user_id.slice(-6)} topic=${topic}`);
    client.subscribe(topic, { qos: 0 });
  });

  client.on('message', (_topic, payload) => {
    try {
      const raw = payload.toString('ascii').replace(/\s/g, '');
      const plain = aesDecrypt(raw, decryptKey);
      const msg   = JSON.parse(plain);
      const event = msg.event || '';
      console.log(`[mqtt] event=${event} user=${user_id.slice(-6)}`);
      if (_onPush) _onPush(user_id, event, msg);
    } catch (err) {
      console.warn(`[mqtt] decode failed user=${user_id.slice(-6)}: ${err.message}`);
    }
  });

  client.on('error', (err) => {
    console.error(`[mqtt] error user=${user_id.slice(-6)}: ${err.message} code=${err.code} reasonCode=${err.reasonCode}`);
  });

  client.on('close', () => {
    console.log(`[mqtt] disconnected user=${user_id.slice(-6)}`);
  });

  _clients.set(user_id, client);
}

/** 특정 user_id 연결 종료 */
function disconnect(userId) {
  const existing = _clients.get(userId);
  if (existing) {
    existing.end(true);
    _clients.delete(userId);
  }
}

/** 세션 토큰 갱신 — 재연결 필요 (MQTT 인증 갱신) */
function reconnect(session) {
  connect(session);
}

/** 서버 시작 시 저장된 세션 전체 연결 */
function connectAll(sessions) {
  for (const session of sessions) {
    connect(session);
  }
}

module.exports = { setOnPush, connect, disconnect, reconnect, connectAll };
