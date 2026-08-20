import WebSocket from 'ws';

const relay = process.argv[2] || 'ws://localhost:8787/ws';
const code = process.argv[3];
const deviceId = process.argv[4] || `receiver_${Math.random().toString(36).slice(2)}`;

if (!code) {
  console.error('Usage: node src/receiver-test.js ws://localhost:8787/ws 123456 [deviceId]');
  process.exit(1);
}

const ws = new WebSocket(relay);
ws.on('open', () => {
  ws.send(JSON.stringify({ type: 'hello', role: 'receiver', deviceId, name: 'BOS Receiver Test' }));
  ws.send(JSON.stringify({ type: 'pair_with_code', code }));
});
ws.on('message', (msg) => console.log(String(msg)));
ws.on('close', () => console.log('closed'));
