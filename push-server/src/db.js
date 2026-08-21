const { Pool } = require('pg');

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function initDB() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS device_tokens (
      id            SERIAL PRIMARY KEY,
      token         TEXT        NOT NULL UNIQUE,
      platform      VARCHAR(10) NOT NULL,
      sandbox       BOOLEAN     NOT NULL DEFAULT false,
      registered_at TIMESTAMPTZ DEFAULT NOW(),
      last_seen     TIMESTAMPTZ DEFAULT NOW()
    )
  `);
  await pool.query(`
    ALTER TABLE device_tokens ADD COLUMN IF NOT EXISTS sandbox BOOLEAN NOT NULL DEFAULT false
  `);
  await pool.query(`
    CREATE TABLE IF NOT EXISTS user_sessions (
      user_id     TEXT PRIMARY KEY,
      sign_token  TEXT        NOT NULL,
      encry_token TEXT        NOT NULL,
      broker_host TEXT        NOT NULL,
      broker_port INT         NOT NULL DEFAULT 8883,
      vin         TEXT,
      updated_at  TIMESTAMPTZ DEFAULT NOW()
    )
  `);
  console.log('[db] tables ready');
}

async function upsertSession(userId, signToken, encryToken, brokerHost, brokerPort, vin) {
  await pool.query(`
    INSERT INTO user_sessions (user_id, sign_token, encry_token, broker_host, broker_port, vin, updated_at)
    VALUES ($1, $2, $3, $4, $5, $6, NOW())
    ON CONFLICT (user_id) DO UPDATE SET
      sign_token  = $2,
      encry_token = $3,
      broker_host = $4,
      broker_port = $5,
      vin         = COALESCE($6, user_sessions.vin),
      updated_at  = NOW()
  `, [userId, signToken, encryToken, brokerHost, brokerPort, vin]);
}

async function updateSessionTokens(userId, signToken, encryToken) {
  await pool.query(`
    UPDATE user_sessions SET sign_token = $2, encry_token = $3, updated_at = NOW()
    WHERE user_id = $1
  `, [userId, signToken, encryToken]);
}

async function getAllSessions() {
  const res = await pool.query('SELECT * FROM user_sessions');
  return res.rows;
}

async function registerToken(token, platform, sandbox = false) {
  await pool.query(`
    INSERT INTO device_tokens (token, platform, sandbox, last_seen)
    VALUES ($1, $2, $3, NOW())
    ON CONFLICT (token) DO UPDATE SET last_seen = NOW(), platform = $2, sandbox = $3
  `, [token, platform, sandbox]);
}

async function unregisterToken(token) {
  await pool.query('DELETE FROM device_tokens WHERE token = $1', [token]);
}

async function getIosTokens(sandbox = false) {
  const res = await pool.query(
    'SELECT token FROM device_tokens WHERE platform = $1 AND sandbox = $2',
    ['ios', sandbox],
  );
  return res.rows.map(r => r.token);
}

async function getAndroidTokens() {
  const res = await pool.query(
    'SELECT token FROM device_tokens WHERE platform = $1',
    ['android'],
  );
  return res.rows.map(r => r.token);
}

async function cleanOldTokens(days = 90) {
  const res = await pool.query(
    `DELETE FROM device_tokens WHERE last_seen < NOW() - INTERVAL '${parseInt(days)} days'`,
  );
  console.log(`[db] cleaned ${res.rowCount} stale tokens (>${days}d)`);
}

module.exports = {
  initDB,
  registerToken, unregisterToken, getIosTokens, getAndroidTokens, cleanOldTokens,
  upsertSession, updateSessionTokens, getAllSessions,
};
