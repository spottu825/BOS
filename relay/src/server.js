import http from 'node:http';
import crypto from 'node:crypto';
import { WebSocketServer } from 'ws';

const PORT = Number(process.env.PORT || 8787);
const PAIR_CODE_TTL_MS = 5 * 60 * 1000;
const MAX_MESSAGE_BYTES = 2 * 1024 * 1024;
const MAX_FRAME_BYTES = 900 * 1024;
const FRAME_TTL_MS = 10_000;
const MAX_CONTROL_QUEUE = 80;

const clients = new Map();
const pairCodes = new Map();
const pairings = new Map();
const phones = new Map(); // publicId -> { deviceId, name, latestFrame, frameAt, updatedAt, frameCount }
const controlQueues = new Map(); // publicId -> [{ action, params, at }]

function baseUrl(req) {
  const proto = req.headers['x-forwarded-proto'] || (req.socket.encrypted ? 'https' : 'http');
  const host = req.headers['x-forwarded-host'] || req.headers.host || `localhost:${PORT}`;
  return `${proto}://${host}`;
}

function json(res, status, body) {
  const data = JSON.stringify(body);
  res.writeHead(status, {
    'content-type': 'application/json',
    'content-length': Buffer.byteLength(data),
    'access-control-allow-origin': '*',
    'cache-control': 'no-store'
  });
  res.end(data);
}

function html(res, body) {
  res.writeHead(200, {
    'content-type': 'text/html; charset=utf-8',
    'content-length': Buffer.byteLength(body),
    'access-control-allow-origin': '*',
    'cache-control': 'no-store'
  });
  res.end(body);
}

function randomId(prefix) {
  return `${prefix}_${crypto.randomBytes(12).toString('base64url')}`;
}

function makePairCode() {
  return String(crypto.randomInt(100000, 999999));
}

function publicIdFor(deviceId) {
  return crypto.createHash('sha256').update(String(deviceId)).digest('base64url').slice(0, 18);
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

function readBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > maxBytes) {
        reject(new Error('too_large'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => resolve(Buffer.concat(chunks)));
    req.on('error', reject);
  });
}

async function readJson(req) {
  const body = await readBody(req, 64 * 1024);
  if (!body.length) return {};
  return JSON.parse(body.toString('utf8'));
}

function viewerHtml(publicId) {
  const id = publicId.replace(/[^a-zA-Z0-9_-]/g, '');
  return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no"><title>BOS global viewer</title><style>body{margin:0;font-family:Arial,sans-serif;background:#101114;color:#fff}#bar{display:flex;flex-wrap:wrap;gap:5px;padding:6px;background:#1c1e24;position:sticky;top:0;z-index:2}button{background:#0b74de;color:#fff;border:0;border-radius:8px;font-weight:700;flex:1 1 72px;padding:8px 4px;font-size:12px}button:disabled{background:#555;color:#aaa}#stage{display:flex;justify-content:center;align-items:flex-start;padding:8px}#screen{display:block;touch-action:none;background:#000;width:auto;max-width:min(96vw,430px);max-height:calc(100vh - 140px);height:auto;object-fit:contain;border:1px solid #333;border-radius:10px}#status{padding:8px 10px;color:#b9bfcc;font-size:13px}.warn{color:#ffd166}</style></head><body><div id="bar"><button onclick="send('back')">Back</button><button onclick="send('home')">Home</button><button onclick="send('recents')">Recents</button><button onclick="send('notifications')">Notif</button><button onclick="send('lock')">Lock</button><button onclick="send('wake')">Wake</button><button onclick="send('volume_up')">Vol+</button><button onclick="send('volume_down')">Vol-</button><button onclick="send('brightness_up')">Bright+</button><button onclick="send('brightness_down')">Bright-</button><button onclick="send('power_menu')">Power</button><button onclick="fullscreen()">Full</button><button disabled>Files later</button><button disabled>Shutdown blocked</button></div><div id="stage"><img id="screen" src="/stream/${id}" alt="BOS screen"></div><div id="status">Global BOS viewer. If blank, phone is offline or not sharing.</div><script>const id=${JSON.stringify(id)},img=document.getElementById('screen'),status=document.getElementById('status');async function send(action,extra){try{const r=await fetch('/control/'+id,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({action,params:extra||{}})});status.textContent=r.ok?action+' sent':action+' failed';}catch(e){status.textContent='control failed: '+e.message;}}function fullscreen(){document.documentElement.requestFullscreen?.();}function p(e){const r=img.getBoundingClientRect();return{x:Math.max(0,Math.min(1,(e.clientX-r.left)/r.width)),y:Math.max(0,Math.min(1,(e.clientY-r.top)/r.height))};}let sx=0,sy=0,scx=0,scy=0,down=false,timer=null,long=false;img.draggable=false;img.addEventListener('error',()=>{status.innerHTML='<span class="warn">Waiting for phone frames...</span>';});img.addEventListener('load',()=>{status.textContent='Connected. Tap preview to tap phone; drag to swipe.';});img.addEventListener('pointerdown',e=>{e.preventDefault();img.setPointerCapture?.(e.pointerId);const q=p(e);sx=q.x;sy=q.y;scx=e.clientX;scy=e.clientY;down=true;long=false;clearTimeout(timer);timer=setTimeout(()=>{if(down){long=true;send('long_press',{x:sx,y:sy});}},650);});img.addEventListener('pointermove',e=>{if(down&&Math.hypot(e.clientX-scx,e.clientY-scy)>=12)clearTimeout(timer);});img.addEventListener('pointerup',e=>{e.preventDefault();if(!down)return;down=false;clearTimeout(timer);if(long)return;const q=p(e),d=Math.hypot(e.clientX-scx,e.clientY-scy);if(d<12)send('tap',{x:q.x,y:q.y});else send('swipe',{x1:sx,y1:sy,x2:q.x,y2:q.y});});img.addEventListener('pointercancel',()=>{down=false;clearTimeout(timer);});</script></body></html>`;
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://bos.local');
    if (req.method === 'OPTIONS') {
      res.writeHead(204, { 'access-control-allow-origin': '*', 'access-control-allow-methods': 'GET,POST,OPTIONS', 'access-control-allow-headers': 'content-type' });
      return res.end();
    }
    if (req.method === 'GET' && url.pathname === '/') {
      return html(res, '<!doctype html><title>BOS relay</title><h1>BOS relay is running</h1><p>Use a BOS phone global viewer URL.</p>');
    }
    if (req.method === 'GET' && url.pathname === '/health') {
      return json(res, 200, { ok: true, phones: phones.size, clients: clients.size, pairs: pairings.size, pairCodes: pairCodes.size });
    }
    if (req.method === 'POST' && url.pathname === '/api/phone/register') {
      const body = await readJson(req);
      const deviceId = String(body.deviceId || randomId('phone')).slice(0, 120);
      const publicId = publicIdFor(deviceId); // permanent for that phone identity on this relay
      const old = phones.get(publicId) || {};
      phones.set(publicId, { ...old, deviceId, name: String(body.name || 'BOS phone').slice(0, 80), updatedAt: Date.now(), frameCount: old.frameCount || 0 });
      if (!controlQueues.has(publicId)) controlQueues.set(publicId, []);
      return json(res, 200, { ok: true, publicId, viewerUrl: `${baseUrl(req)}/view/${publicId}`, permanent: true });
    }
    const frameMatch = url.pathname.match(/^\/api\/phone\/([a-zA-Z0-9_-]+)\/frame$/);
    if (req.method === 'POST' && frameMatch) {
      const publicId = frameMatch[1];
      const phone = phones.get(publicId);
      if (!phone) return json(res, 404, { error: 'phone_not_registered' });
      const frame = await readBody(req, MAX_FRAME_BYTES);
      if (!frame.length) return json(res, 400, { error: 'empty_frame' });
      phone.latestFrame = frame;
      phone.frameAt = Date.now();
      phone.updatedAt = Date.now();
      phone.frameCount = (phone.frameCount || 0) + 1;
      return json(res, 200, { ok: true, frameCount: phone.frameCount });
    }
    const pollMatch = url.pathname.match(/^\/api\/phone\/([a-zA-Z0-9_-]+)\/control$/);
    if (req.method === 'GET' && pollMatch) {
      const publicId = pollMatch[1];
      const queue = controlQueues.get(publicId) || [];
      const controls = queue.splice(0, MAX_CONTROL_QUEUE);
      controlQueues.set(publicId, queue);
      return json(res, 200, { ok: true, controls });
    }
    const viewMatch = url.pathname.match(/^\/view\/([a-zA-Z0-9_-]+)$/);
    if (req.method === 'GET' && viewMatch) return html(res, viewerHtml(viewMatch[1]));
    const controlMatch = url.pathname.match(/^\/control\/([a-zA-Z0-9_-]+)$/);
    if (req.method === 'POST' && controlMatch) {
      const publicId = controlMatch[1];
      if (!phones.has(publicId)) return json(res, 404, { error: 'phone_not_registered' });
      const body = await readJson(req);
      const action = String(body.action || '').slice(0, 40);
      const params = typeof body.params === 'object' && body.params ? body.params : {};
      if (!action) return json(res, 400, { error: 'missing_action' });
      const queue = controlQueues.get(publicId) || [];
      queue.push({ action, params, at: Date.now() });
      while (queue.length > MAX_CONTROL_QUEUE) queue.shift();
      controlQueues.set(publicId, queue);
      return json(res, 200, { ok: true, queued: queue.length });
    }
    const streamMatch = url.pathname.match(/^\/stream\/([a-zA-Z0-9_-]+)$/);
    if (req.method === 'GET' && streamMatch) {
      const publicId = streamMatch[1];
      res.writeHead(200, { 'content-type': 'multipart/x-mixed-replace; boundary=bosframe', 'cache-control': 'no-store', 'access-control-allow-origin': '*' });
      const timer = setInterval(() => {
        const phone = phones.get(publicId);
        const frame = phone?.latestFrame;
        if (frame && Date.now() - (phone.frameAt || 0) < FRAME_TTL_MS) {
          res.write(`--bosframe\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.length}\r\n\r\n`);
          res.write(frame);
          res.write('\r\n');
        }
      }, 160);
      req.on('close', () => clearInterval(timer));
      return;
    }
    return json(res, 404, { error: 'not_found' });
  } catch (error) {
    return json(res, error.message === 'too_large' ? 413 : 500, { error: error.message || 'server_error' });
  }
});

const wss = new WebSocketServer({ server, path: '/ws', maxPayload: MAX_MESSAGE_BYTES });

wss.on('connection', (ws) => {
  let deviceId = null;
  ws.on('message', (raw) => {
    let msg;
    try { msg = JSON.parse(String(raw)); } catch { return send(ws, 'error', { error: 'bad_json' }); }
    if (msg.type === 'hello') {
      deviceId = String(msg.deviceId || randomId(msg.role === 'phone' ? 'phone' : 'device'));
      clients.set(deviceId, { ws, role: msg.role === 'phone' ? 'phone' : 'receiver', name: String(msg.name || 'BOS device').slice(0, 80) });
      send(ws, 'hello_ok', { deviceId, paired: [...(pairings.get(deviceId) || [])] });
      broadcastPresence(deviceId);
      return;
    }
    if (!deviceId || !clients.has(deviceId)) return send(ws, 'error', { error: 'not_registered' });
    if (msg.type === 'create_pair_code') {
      const code = makePairCode();
      pairCodes.set(code, { phoneId: deviceId, expiresAt: Date.now() + PAIR_CODE_TTL_MS });
      return send(ws, 'pair_code', { code, expiresInSeconds: PAIR_CODE_TTL_MS / 1000 });
    }
    if (msg.type === 'pair_with_code') {
      const code = String(msg.code || '').replace(/\D/g, '');
      const record = pairCodes.get(code);
      if (!record || record.expiresAt < Date.now()) { pairCodes.delete(code); return send(ws, 'pair_failed', { reason: 'code_expired_or_invalid' }); }
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
      return send(target.ws, msg.type, { from: deviceId, payload: msg.payload ?? null });
    }
    send(ws, 'error', { error: 'unknown_type', type: msg.type });
  });
  ws.on('close', () => { if (deviceId) { clients.delete(deviceId); broadcastPresence(deviceId); } });
});

setInterval(() => {
  const now = Date.now();
  for (const [code, record] of pairCodes) if (record.expiresAt < now) pairCodes.delete(code);
  for (const [publicId, phone] of phones) {
    if (now - (phone.updatedAt || 0) > 10 * 60 * 1000) {
      phone.latestFrame = null;
      controlQueues.delete(publicId);
    }
  }
}, 30_000).unref();

server.listen(PORT, () => {
  console.log(`BOS relay listening on :${PORT}`);
});
