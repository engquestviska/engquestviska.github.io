import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { createSession } = require('../js/session.js');

function memoryStorage() {
  const data = {};
  return {
    getItem: k => (k in data ? data[k] : null),
    setItem: (k, v) => { data[k] = String(v); },
    removeItem: k => { delete data[k]; }
  };
}

function fakeApi(responses) {
  const calls = [];
  return {
    calls,
    get(params) {
      calls.push(params);
      const r = responses.shift();
      return r instanceof Error ? Promise.reject(r) : Promise.resolve(r);
    }
  };
}

test('login stores the credentials when the backend says ok', async () => {
  const storage = memoryStorage();
  const s = createSession({ storage, api: fakeApi([{ ok: true }]) });
  const r = await s.login('teacher', 'pw');
  assert.equal(r.ok, true);
  assert.deepEqual(s.get(), { username: 'teacher', password: 'pw' });
  assert.ok(storage.getItem('eqt_login'));
});

test('failed login stores nothing', async () => {
  const s = createSession({ storage: memoryStorage(), api: fakeApi([{ ok: false }]) });
  const r = await s.login('teacher', 'bad');
  assert.equal(r.ok, false);
  assert.ok(r.error);
  assert.equal(s.get(), null);
});

test('call sends the stored username and password', async () => {
  const api = fakeApi([{ ok: true }, { success: true }]);
  const s = createSession({ storage: memoryStorage(), api });
  await s.login('teacher', 'pw');
  await s.call({ action: 'saveAttendance', className: 'XE1' });
  assert.deepEqual(api.calls[1], { action: 'saveAttendance', className: 'XE1', username: 'teacher', password: 'pw' });
});

test('Unauthorized clears the session, fires onExpired once and rejects', async () => {
  const s = createSession({ storage: memoryStorage(), api: fakeApi([{ ok: true }, { error: 'Unauthorized' }]) });
  await s.login('teacher', 'pw');
  let fired = 0;
  s.onExpired = () => { fired++; };
  await assert.rejects(s.call({ action: 'getTaskSubmissions' }), /Session expired/);
  assert.equal(s.get(), null);
  assert.equal(fired, 1);
});

test('other errors reject with the backend message and keep the session', async () => {
  const s = createSession({ storage: memoryStorage(), api: fakeApi([{ ok: true }, { success: false, error: 'Boom' }]) });
  await s.login('teacher', 'pw');
  await assert.rejects(s.call({ action: 'saveAttendance' }), /Boom/);
  assert.ok(s.get());
});

test('read sends no credentials', async () => {
  const api = fakeApi([{ students: [] }]);
  const s = createSession({ storage: memoryStorage(), api });
  await s.read({ action: 'getClassData', className: 'XE1' });
  assert.deepEqual(api.calls[0], { action: 'getClassData', className: 'XE1' });
});

test('setClass and getClass remember the class', () => {
  const s = createSession({ storage: memoryStorage(), api: fakeApi([]) });
  assert.equal(s.getClass(), null);
  s.setClass('XE2');
  assert.equal(s.getClass(), 'XE2');
});

test('logout clears the stored login', async () => {
  const s = createSession({ storage: memoryStorage(), api: fakeApi([{ ok: true }]) });
  await s.login('teacher', 'pw');
  s.logout();
  assert.equal(s.get(), null);
});
