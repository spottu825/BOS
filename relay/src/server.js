import http from 'node:http';
import crypto from 'node:crypto';
import { WebSocketServer } from 'ws';

const PORT = Number(process.env.PORT || 8787);
const PAIR_CODE_TTL_MS = 5 * 60 * 1000;
const MAX_MESSAGE_BYTES = 256 * 1024;

const clients = new Map(); // deviceId -> { ws, role, name, paired: Set<deviceId> }
const pairCodes = new Map(); // code -> { phoneId, expiresAt }
const pairings = new Map(); // deviceId -> Set<deviceId>

function json(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(data),
    'access-control-allow-origin': '*'
  });
  res.end(data);
}

function randomId(prefix) {
  return `${prefix}_${crypto.randomBytes(12).toString('base64url')}`;
}

function makePairCode() {
  return String(crypto.randomInt(100000, 999999));
}

function addPairing(a, b) {
  if (!pairings.has(a)) pairings.set(a, new Set());
  if (!pairings.has(b)) pairings.set(b, new Set());
  pairings.get(a).add(b);
  pairings.get(b).add(a);
}

function isPaired(a, b) {
  return pairings.get(a)?.has(b) === true;
}

function send(ws, type, payload = {}) {
  if (ws.readyState === ws.OPEN) ws.send(JSON.stringify({ type, ...payload }));
}

function broadcastPresence(deviceId) {
  const paired = pairings.get(deviceId) || new Set();
  for (const otherId of paired) {
    const other = clients.get(otherId);
    if (other) send(other.ws, 'presence', { deviceId, online: clients.has(deviceId) });
  }
}

const server = http.createServer((req, res) => {
  if (req.method === 'GET' && req.url === '/health') {
    return json(res, 200, { ok: true, clients: clients.size, pairs: pairings.size, pairCodes: pairCodes.size });
  }
  if (req.method === 'OPTIONS') {
    res.writeHead(204, {
      'access-control-allow-origin': '*',
      'access-control-allow-methods': 'GET,POST,OPTIONS',
      'access-control-allow-headers': 'content-type'
    });
    return res.end();
  }
  json(res, 404, { error: 'not_found' });
});

const wss = new WebSocketServer({ server, path: '/ws', maxPayload: MAX_MESSAGE_BYTES });

wss.on('connection', (ws) => {
  let deviceId = null;

  ws.on('message', (raw) => {
    let msg;
    try {
      msg = JSON.parse(String(raw));
    } catch {
      return send(ws, 'error', { error: 'bad_json' });
    }

    if (msg.type === 'hello') {
      deviceId = String(msg.deviceId || randomId(msg.role === 'phone' ? 'phone' : 'device'));
      clients.set(deviceId, {
        ws,
        role: msg.role === 'phone' ? 'phone' : 'receiver',
        name: String(msg.name || 'BOS device').slice(0, 80)
      });
      send(ws, 'hello_ok', { deviceId, paired: [...(pairings.get(deviceId) || [])] });
      broadcastPresence(deviceId);
      return;
    }

    if (!deviceId || !clients.has(deviceId)) return send(ws, 'error', { error: 'not_registered' });

    if (msg.type === 'create_pair_code') {
      const code = makePairCode();
      pairCodes.set(code, { phoneId: deviceId, expiresAt: Date.now() + PAIR_CODE_TTL_MS });
      send(ws, 'pair_code', { code, expiresInSeconds: PAIR_CODE_TTL_MS / 1000 });
      return;
    }

    if (msg.type === 'pair_with_code') {
      const code = String(msg.code || '').replace(/\D/g, '');
      const record = pairCodes.get(code);
      if (!record || record.expiresAt < Date.now()) {
        pairCodes.delete(code);
        return send(ws, 'pair_failed', { reason: 'code_expired_or_invalid' });
      }
      const phoneId = record.phoneId;
      pairCodes.delete(code);
      addPairing(deviceId, phoneId);
      send(ws, 'pair_ok', { deviceId: phoneId });
      const phone = clients.get(phoneId);
      if (phone) send(phone.ws, 'pair_ok', { deviceId });
      broadcastPresence(deviceId);
      broadcastPresence(phoneId);
      return;
    }

    if (msg.type === 'signal' || msg.type === 'control' || msg.type === 'stream_meta') {
      const to = String(msg.to || '');
      if (!isPaired(deviceId, to)) return send(ws, 'error', { error: 'not_paired', to });
      const target = clients.get(to);
      if (!target) return send(ws, 'delivery_failed', { to, reason: 'offline' });
      send(target.ws, msg.type, { from: deviceId, payload: msg.payload ?? null });
      return;
    }

    send(ws, 'error', { error: 'unknown_type', type: msg.type });
  });

  ws.on('close', () => {
    if (deviceId) {
      clients.delete(deviceId);
      broadcastPresence(deviceId);
    }
  });
});

setInterval(() => {
  const now = Date.now();
  for (const [code, record] of pairCodes) {
    if (record.expiresAt < now) pairCodes.delete(code);
  }
}, 30_000).unref();

server.listen(PORT, () => {
  console.log(`BOS relay listening on :${PORT}`);
});
