#!/usr/bin/env node
// UltraCode bridge — runs the OFFICIAL Claude Code CLI (signed in with your own Claude account)
// and streams its output to the UltraCode app. The app never sees your Claude login.
//
//   node ultracode-bridge.js --token <secret> [--port 8765] [--lan] [--dir ~/projects]
//
// --lan  listen on all interfaces (to connect from a phone to a PC over Wi-Fi)
'use strict';
const http = require('http');
const { spawn, execFile } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const os = require('os');

const argv = process.argv.slice(2);
const arg = (name, def) => { const i = argv.indexOf(name); return i >= 0 && argv[i + 1] ? argv[i + 1] : def; };
const LAN = argv.includes('--lan');
const PORT = parseInt(arg('--port', '8765'), 10);
const BIN = process.env.CLAUDE_BIN || 'claude';
const ROOT = path.resolve(arg('--dir', path.join(os.homedir(), 'projects')).replace(/^~/, os.homedir()));
const TOKEN_FILE = path.join(os.homedir(), '.ultracode-bridge-token');
let TOKEN = arg('--token', '');
if (!TOKEN) {
  TOKEN = fs.existsSync(TOKEN_FILE) ? fs.readFileSync(TOKEN_FILE, 'utf8').trim() : '';
  if (!TOKEN) { TOKEN = crypto.randomBytes(16).toString('hex'); fs.writeFileSync(TOKEN_FILE, TOKEN, { mode: 0o600 }); }
}
fs.mkdirSync(ROOT, { recursive: true });

const DEFAULT_TOOLS = ['Read', 'Edit', 'Write', 'MultiEdit', 'Glob', 'Grep', 'LS', 'TodoWrite', 'WebFetch', 'WebSearch', 'NotebookEdit'];

function authed(req) {
  const got = Buffer.from(String(req.headers['x-token'] || ''));
  const want = Buffer.from(TOKEN);
  return got.length === want.length && crypto.timingSafeEqual(got, want);
}

function json(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(obj));
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0; const chunks = [];
    req.on('data', (c) => { size += c.length; if (size > 20e6) { reject(new Error('body too large')); req.destroy(); } else chunks.push(c); });
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

/** Project folder inside ROOT (no escaping with ".."). */
function projectDir(name) {
  const clean = String(name || '').replace(/\\/g, '/').split('/').filter((s) => s && s !== '.' && s !== '..').join('/');
  const dir = path.join(ROOT, clean || 'ultracode');
  fs.mkdirSync(dir, { recursive: true });
  return dir;
}

let versionCache = null;
function claudeVersion() {
  return new Promise((resolve) => {
    if (versionCache) return resolve(versionCache);
    execFile(BIN, ['--version'], { timeout: 20000 }, (err, out) => {
      if (err) return resolve(null);
      versionCache = String(out).trim();
      resolve(versionCache);
    });
  });
}

async function handleChat(req, res) {
  let body;
  try { body = JSON.parse(await readBody(req)); } catch (e) { return json(res, 400, { error: 'bad json' }); }
  const prompt = String(body.prompt || '').trim();
  if (!prompt) return json(res, 400, { error: 'empty prompt' });

  const cwd = projectDir(body.folder);
  const args = ['-p', '--output-format', 'stream-json', '--verbose', '--include-partial-messages'];
  if (body.model) args.push('--model', String(body.model));
  if (body.session) args.push('--resume', String(body.session));
  if (body.system) args.push('--append-system-prompt', String(body.system));
  const tools = DEFAULT_TOOLS.slice();
  if (body.allowBash !== false) tools.push('Bash');
  args.push('--permission-mode', 'acceptEdits', '--allowedTools', ...tools);

  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
  });
  const send = (obj) => res.write('data: ' + (typeof obj === 'string' ? obj : JSON.stringify(obj)) + '\n\n');
  send({ type: 'bridge_start', cwd });

  let child;
  try {
    child = spawn(BIN, args, { cwd, env: process.env, stdio: ['pipe', 'pipe', 'pipe'] });
  } catch (e) {
    send({ type: 'bridge_error', message: 'Не удалось запустить Claude Code: ' + e.message });
    return res.end();
  }
  let finished = false;
  let buf = '';
  let stderr = '';
  child.stdout.on('data', (d) => {
    buf += d.toString('utf8');
    let i;
    while ((i = buf.indexOf('\n')) >= 0) {
      const line = buf.slice(0, i).trim();
      buf = buf.slice(i + 1);
      if (line.startsWith('{')) send(line);
    }
  });
  child.stderr.on('data', (d) => { stderr = (stderr + d.toString('utf8')).slice(-4000); });
  child.on('error', (e) => {
    finished = true;
    send({ type: 'bridge_error', message: e.code === 'ENOENT'
      ? 'Claude Code не найден. Установите его и войдите в аккаунт (команда claude).'
      : 'Ошибка Claude Code: ' + e.message });
    res.end();
  });
  child.on('close', (code) => {
    if (finished) return;
    finished = true;
    if (buf.trim().startsWith('{')) send(buf.trim());
    if (code !== 0) send({ type: 'bridge_error', message: (stderr.trim() || ('Claude Code завершился с кодом ' + code)).slice(-1500) });
    send({ type: 'bridge_done', code });
    res.end();
  });
  // Client gone (Stop button / app closed) -> stop Claude Code.
  res.on('close', () => { if (!finished) { finished = true; child.kill('SIGTERM'); } });

  child.stdin.end(prompt);
}

const server = http.createServer(async (req, res) => {
  try {
    if (!authed(req)) return json(res, 401, { error: 'unauthorized' });
    const url = new URL(req.url, 'http://x');
    if (req.method === 'GET' && url.pathname === '/health') {
      const v = await claudeVersion();
      return json(res, 200, { ok: !!v, version: v, root: ROOT, bridge: 1 });
    }
    if (req.method === 'POST' && url.pathname === '/chat') return handleChat(req, res);
    json(res, 404, { error: 'not found' });
  } catch (e) {
    try { json(res, 500, { error: String(e && e.message || e) }); } catch (_) { /* already streaming */ }
  }
});

server.listen(PORT, LAN ? '0.0.0.0' : '127.0.0.1', () => {
  const ips = LAN ? Object.values(os.networkInterfaces()).flat().filter((a) => a && a.family === 'IPv4' && !a.internal).map((a) => a.address) : ['127.0.0.1'];
  console.log('\n  UltraCode bridge запущен');
  ips.forEach((ip) => console.log('  Адрес:  http://' + ip + ':' + PORT));
  console.log('  Токен:  ' + TOKEN);
  console.log('  Проекты: ' + ROOT);
  console.log('  Не закрывайте это окно, пока работаете в UltraCode.\n');
  claudeVersion().then((v) => console.log(v ? '  Claude Code: ' + v : '  ВНИМАНИЕ: Claude Code не найден (' + BIN + ')'));
});
