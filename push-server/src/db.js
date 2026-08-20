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
  // 기존 테이블에 sandbox 컬럼 없으면 추가
  await pool.query(`
    ALTER TABLE device_tokens ADD COLUMN IF NOT EXISTS sandbox BOOLEAN NOT NULL DEFAULT false
  `);
  console.log('[db] device_tokens table ready');
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

module.exports = { initDB, registerToken, unregisterToken, getIosTokens, getAndroidTokens, cleanOldTokens };
