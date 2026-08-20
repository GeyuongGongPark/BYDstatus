const { Pool } = require('pg');

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

async function initDB() {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS device_tokens (
      id            SERIAL PRIMARY KEY,
      token         TEXT        NOT NULL UNIQUE,
      platform      VARCHAR(10) NOT NULL,
      registered_at TIMESTAMPTZ DEFAULT NOW(),
      last_seen     TIMESTAMPTZ DEFAULT NOW()
    )
  `);
  console.log('[db] device_tokens table ready');
}

async function registerToken(token, platform) {
  await pool.query(`
    INSERT INTO device_tokens (token, platform, last_seen)
    VALUES ($1, $2, NOW())
    ON CONFLICT (token) DO UPDATE SET last_seen = NOW(), platform = $2
  `, [token, platform]);
}

async function unregisterToken(token) {
  await pool.query('DELETE FROM device_tokens WHERE token = $1', [token]);
}

async function getTokensByPlatform(platform) {
  const res = await pool.query(
    'SELECT token FROM device_tokens WHERE platform = $1',
    [platform],
  );
  return res.rows.map(r => r.token);
}

async function cleanOldTokens(days = 90) {
  const res = await pool.query(
    `DELETE FROM device_tokens WHERE last_seen < NOW() - INTERVAL '${parseInt(days)} days'`,
  );
  console.log(`[db] cleaned ${res.rowCount} stale tokens (>${days}d)`);
}

module.exports = { initDB, registerToken, unregisterToken, getTokensByPlatform, cleanOldTokens };
