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
<style>body{font-family:sans-serif;background:#f7f7f7;color:#111;margin:0;padding:24px}.card{max-width:760px;margin:auto;background:white;border-radius:16px;padding:22px;box-shadow:0 2px 20px #0001}input,button{font-size:16px;padding:10px;border-radius:8px}input{width:calc(100% - 24px);border:1px solid #ccc}button{border:0;background:#6246ea;color:#fff;margin-top:10px;cursor:pointer}.muted{color:#666}</style></head><body><main class="card">
<h1>BOS Receiver</h1>
<p class="muted">Local desktop helper. This is where ADB terminal support belongs, because ADB runs from the laptop/PC after the phone authorizes debugging.</p>
<h2>Open local phone URL</h2>
<input id="url" placeholder="http://phone-ip:8080"><br><button onclick="openViewer()">Open Viewer</button>
<h2>ADB</h2>
<button onclick="window.open('/adb','_blank')">Open ADB Terminal in new tab</button>
<h2>Global URL</h2>
<p class="muted">Global mode needs a hosted BOS relay and APK integration. Current relay server code exists, but the APK is not connected to it yet.</p>
<script>function openViewer(){const u=document.getElementById('url').value.trim(); if(u) window.open(u,'_blank');}</script>
</main></body></html>`;
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
