import http from 'node:http';
import { spawn } from 'node:child_process';
import { URL } from 'node:url';

const PORT = Number(process.env.PORT || 9090);
const HOST = '127.0.0.1';
const MAX_OUTPUT = 20000;

function send(res, status, body, type = 'text/html; charset=utf-8') {
  res.writeHead(status, {
    'content-type': type,
    'x-content-type-options': 'nosniff',
    'cache-control': 'no-store'
  });
  res.end(body);
}

function json(res, status, body) {
  send(res, status, JSON.stringify(body), 'application/json; charset=utf-8');
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let data = '';
    req.on('data', chunk => {
      data += chunk;
      if (data.length > 4096) reject(new Error('body_too_large'));
    });
    req.on('end', () => resolve(data));
    req.on('error', reject);
  });
}

function runAdb(args, timeoutMs = 15000) {
  return new Promise((resolve) => {
    const child = spawn('adb', args, { shell: false, windowsHide: true });
    let output = '';
    const timer = setTimeout(() => {
      child.kill();
      output += '\n[timeout]';
    }, timeoutMs);
    child.stdout.on('data', d => { output = (output + d).slice(-MAX_OUTPUT); });
    child.stderr.on('data', d => { output = (output + d).slice(-MAX_OUTPUT); });
    child.on('error', err => {
      clearTimeout(timer);
      resolve({ ok: false, output: `ADB failed: ${err.message}\nInstall Android platform-tools and make sure adb is on PATH.` });
    });
    child.on('close', code => {
      clearTimeout(timer);
      resolve({ ok: code === 0, code, output });
    });
  });
}

function splitArgs(text) {
  const args = [];
  const re = /"([^"]*)"|'([^']*)'|(\S+)/g;
  let match;
  while ((match = re.exec(text))) args.push(match[1] ?? match[2] ?? match[3]);
  return args;
}

async function handleAdb(req, res) {
  const body = await readBody(req);
  let parsed;
  try { parsed = JSON.parse(body || '{}'); } catch { return json(res, 400, { ok: false, output: 'Bad JSON' }); }
  const command = String(parsed.command || '').trim();
  if (!command) return json(res, 400, { ok: false, output: 'No command' });

  // Safety: this page is only for ADB commands, not arbitrary system shell commands.
  const args = command.startsWith('adb ') ? splitArgs(command).slice(1) : splitArgs(command);
  if (args.length === 0) return json(res, 400, { ok: false, output: 'No adb arguments' });
  if (args.some(arg => /[;&|<>`$]/.test(arg))) return json(res, 400, { ok: false, output: 'Unsafe shell characters are not allowed.' });

  const result = await runAdb(args);
  json(res, 200, result);
}

function homePage() {
  return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>BOS Receiver</title>
<style>
:root{color-scheme:light}body{font-family:system-ui,-apple-system,Segoe UI,sans-serif;background:#f4f6fb;color:#111;margin:0;padding:18px}.wrap{max-width:1120px;margin:auto}.grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}.card{background:white;border-radius:16px;padding:18px;box-shadow:0 2px 18px #0001}h1{margin:0 0 6px}h2{margin:6px 0 10px}.muted{color:#666;font-size:14px}input,button,select{font-size:15px;padding:10px;border-radius:9px}input{box-sizing:border-box;width:100%;border:1px solid #ccd}button{border:0;background:#6246ea;color:#fff;margin:8px 6px 0 0;cursor:pointer}button.secondary{background:#e7e9f6;color:#222}button.danger{background:#b42318}pre{background:#0f1117;color:#dbe7ff;border-radius:12px;padding:12px;min-height:160px;max-height:280px;overflow:auto;white-space:pre-wrap}iframe{width:100%;height:68vh;border:1px solid #ccd;border-radius:12px;background:#111}.status{display:inline-block;padding:4px 8px;border-radius:999px;background:#eee;font-size:12px}.ok{background:#d1fadf;color:#05603a}.bad{background:#fee4e2;color:#912018}@media(max-width:860px){.grid{grid-template-columns:1fr}iframe{height:58vh}}
</style></head><body><main class="wrap">
<h1>BOS Receiver</h1>
<p class="muted">Desktop receiver for BOS local viewer, future global relay pairing, and optional ADB helper.</p>
<div class="grid">
<section class="card">
  <h2>Local viewer</h2>
  <p class="muted">Use this with the current APK when phone and laptop are on the same Wi‑Fi.</p>
  <input id="localUrl" placeholder="http://phone-ip:8080">
  <button onclick="openLocalTab()">Open in new tab</button>
  <button class="secondary" onclick="embedLocal()">Open below</button>
  <button class="secondary" onclick="clearFrame()">Clear</button>
  <div style="margin-top:12px"><iframe id="viewer" title="BOS local viewer"></iframe></div>
</section>

<section class="card">
  <h2>Global pairing</h2>
  <p class="muted">Receiver side is ready to connect to a hosted BOS relay. Phone APK global mode is still not integrated yet.</p>
  <label class="muted">Relay WebSocket URL</label>
  <input id="relayUrl" value="ws://localhost:8787/ws">
  <label class="muted">Receiver ID</label>
  <input id="receiverId">
  <label class="muted">Pairing code from phone</label>
  <input id="pairCode" placeholder="123456">
  <button onclick="connectRelay()">Connect relay</button>
  <button onclick="pairWithCode()">Pair code</button>
  <button class="secondary" onclick="sendPing()">Send test signal</button>
  <p>Status: <span id="relayStatus" class="status bad">offline</span></p>
  <pre id="log">Global relay log.\n</pre>
</section>

<section class="card">
  <h2>ADB terminal</h2>
  <p class="muted">Runs on this laptop only. Requires Android platform-tools and phone authorization. Not required for normal viewing.</p>
  <button onclick="window.open('/adb','_blank')">Open ADB Terminal in new tab</button>
  <button class="secondary" onclick="quickAdb('devices -l')">Check devices</button>
  <pre id="adbQuick">ADB quick output.\n</pre>
</section>

<section class="card">
  <h2>What works now</h2>
  <ul>
    <li>Local same-Wi‑Fi viewer via phone URL.</li>
    <li>ADB helper from laptop if user enabled debugging.</li>
    <li>Relay server code and receiver relay UI.</li>
  </ul>
  <h2>Not done yet</h2>
  <ul>
    <li>Phone APK does not connect to relay yet.</li>
    <li>Global video/control relay is not implemented yet.</li>
    <li>WebRTC backup mode is planned, not active.</li>
  </ul>
</section>
</div>
</main>
<script>
const logEl = document.getElementById('log');
const statusEl = document.getElementById('relayStatus');
let ws = null;
let pairedDeviceId = null;
function receiverId(){
  let id = localStorage.getItem('bosReceiverId');
  if(!id){ id = 'receiver_' + crypto.getRandomValues(new Uint32Array(2)).join(''); localStorage.setItem('bosReceiverId', id); }
  return id;
}
document.getElementById('receiverId').value = receiverId();
function log(msg){ logEl.textContent += '[' + new Date().toLocaleTimeString() + '] ' + msg + '\n'; logEl.scrollTop = logEl.scrollHeight; }
function setStatus(ok, text){ statusEl.className = 'status ' + (ok ? 'ok' : 'bad'); statusEl.textContent = text; }
function openLocalTab(){ const u=document.getElementById('localUrl').value.trim(); if(u) window.open(u,'_blank'); }
function embedLocal(){ const u=document.getElementById('localUrl').value.trim(); if(u) document.getElementById('viewer').src = u; }
function clearFrame(){ document.getElementById('viewer').src = 'about:blank'; }
function connectRelay(){
  if(ws) ws.close();
  const url = document.getElementById('relayUrl').value.trim();
  const id = document.getElementById('receiverId').value.trim() || receiverId();
  localStorage.setItem('bosReceiverId', id);
  log('connecting to ' + url);
  ws = new WebSocket(url);
  ws.onopen = () => { setStatus(true, 'connected'); log('relay connected'); ws.send(JSON.stringify({type:'hello', role:'receiver', deviceId:id, name:'BOS Desktop Receiver'})); };
  ws.onclose = () => { setStatus(false, 'offline'); log('relay closed'); };
  ws.onerror = () => { setStatus(false, 'error'); log('relay error'); };
  ws.onmessage = (event) => {
    log('recv ' + event.data);
    try {
      const msg = JSON.parse(event.data);
      if(msg.type === 'pair_ok') pairedDeviceId = msg.deviceId;
    } catch (_) {}
  };
}
function pairWithCode(){
  if(!ws || ws.readyState !== WebSocket.OPEN) return log('connect relay first');
  const code = document.getElementById('pairCode').value.trim();
  ws.send(JSON.stringify({type:'pair_with_code', code}));
  log('sent pair code');
}
function sendPing(){
  if(!ws || ws.readyState !== WebSocket.OPEN) return log('connect relay first');
  if(!pairedDeviceId) return log('no paired phone yet');
  ws.send(JSON.stringify({type:'signal', to:pairedDeviceId, payload:{kind:'receiver_ping', at:Date.now()}}));
  log('sent test signal to ' + pairedDeviceId);
}
async function quickAdb(command){
  const res = await fetch('/api/adb', {method:'POST', headers:{'content-type':'application/json'}, body:JSON.stringify({command})});
  const data = await res.json();
  document.getElementById('adbQuick').textContent += '$ adb ' + command + '\n' + (data.output || '') + '\n';
}
</script></body></html>`;
}

function adbPage() {
  return `<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>BOS ADB Terminal</title>
<style>body{font-family:ui-monospace,Consolas,monospace;background:#0f1117;color:#e6edf3;margin:0;padding:14px}button,input{font:inherit;padding:10px;border-radius:8px}input{width:calc(100% - 24px);background:#161b22;color:#e6edf3;border:1px solid #30363d}button{background:#238636;color:white;border:0;margin:6px 4px 6px 0}pre{white-space:pre-wrap;background:#010409;border:1px solid #30363d;border-radius:8px;padding:12px;min-height:55vh}.warn{color:#ffa657}</style></head><body>
<h1>BOS ADB Terminal</h1>
<p class="warn">Only works if Android platform-tools/adb is installed on this laptop and the phone authorized USB/Wireless debugging. This does not enable ADB by itself.</p>
<button onclick="run('devices -l')">adb devices</button>
<button onclick="run('shell input keyevent KEYCODE_WAKEUP')">Wake</button>
<button onclick="run('shell input keyevent KEYCODE_SLEEP')">Sleep</button>
<button onclick="run('shell input keyevent KEYCODE_VOLUME_UP')">Vol+</button>
<button onclick="run('shell input keyevent KEYCODE_VOLUME_DOWN')">Vol-</button>
<button onclick="run('shell input keyevent KEYCODE_POWER')">Power key</button>
<input id="cmd" placeholder="adb shell input tap 500 500"><button onclick="run(document.getElementById('cmd').value)">Run ADB</button>
<pre id="out">Ready.</pre>
<script>
async function run(command){
  document.getElementById('out').textContent += '\n$ adb ' + command.replace(/^adb\s+/, '') + '\n';
  const res = await fetch('/api/adb', {method:'POST', headers:{'content-type':'application/json'}, body:JSON.stringify({command})});
  const data = await res.json();
  document.getElementById('out').textContent += (data.output || '') + '\n';
}
</script></body></html>`;
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url || '/', `http://${HOST}:${PORT}`);
  try {
    if (req.method === 'GET' && url.pathname === '/') return send(res, 200, homePage());
    if (req.method === 'GET' && url.pathname === '/adb') return send(res, 200, adbPage());
    if (req.method === 'GET' && url.pathname === '/health') return json(res, 200, { ok: true });
    if (req.method === 'POST' && url.pathname === '/api/adb') return await handleAdb(req, res);
    return send(res, 404, 'Not found', 'text/plain; charset=utf-8');
  } catch (error) {
    return json(res, 500, { ok: false, output: error.message });
  }
});

server.listen(PORT, HOST, () => {
  console.log(`BOS Receiver listening on http://${HOST}:${PORT}`);
});
