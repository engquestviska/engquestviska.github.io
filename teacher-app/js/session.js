/* EngQuest Teacher app: remembered login + API wrapper (browser + Node).
   The app keeps the login on the owner's phone (localStorage), unlike the
   teacher website, which keeps it only for the browser session. */
(function (root) {
  var LOGIN_KEY = 'eqt_login';
  var CLASS_KEY = 'eqt_class';

  function createSession(opts) {
    var storage = opts.storage;
    var api = opts.api;

    function safeGet(key) { try { return storage.getItem(key); } catch (e) { return null; } }
    function safeSet(key, value) { try { storage.setItem(key, value); } catch (e) { /* storage off */ } }
    function safeRemove(key) { try { storage.removeItem(key); } catch (e) { /* storage off */ } }

    var session = {
      onExpired: null,

      get: function () {
        try {
          var v = JSON.parse(safeGet(LOGIN_KEY) || 'null');
          return v && v.username && v.password ? { username: v.username, password: v.password } : null;
        } catch (e) { return null; }
      },

      login: function (username, password) {
        return api.get({ action: 'checkLogin', username: username, password: password }).then(function (data) {
          if (!data || !data.ok) return { ok: false, error: 'Incorrect username or password.' };
          safeSet(LOGIN_KEY, JSON.stringify({ username: username, password: password }));
          return { ok: true };
        }, function () {
          return { ok: false, error: 'No connection. Check the internet and try again.' };
        });
      },

      logout: function () { safeRemove(LOGIN_KEY); },

      call: function (params) {
        var who = session.get() || { username: '', password: '' };
        var p = Object.assign({}, params, { username: who.username, password: who.password });
        return api.get(p).then(handle);
      },

      read: function (params) {
        return api.get(Object.assign({}, params)).then(handle);
      },

      getClass: function () { return safeGet(CLASS_KEY); },
      setClass: function (id) { safeSet(CLASS_KEY, id); }
    };

    function handle(res) {
      if (res && res.error === 'Unauthorized') {
        session.logout();
        if (typeof session.onExpired === 'function') session.onExpired();
        throw new Error('Session expired');
      }
      if (!res || res.error || res.success === false) throw new Error((res && res.error) || 'Save failed');
      return res;
    }

    return session;
  }

  if (typeof module === 'object' && module.exports) module.exports = { createSession: createSession };
  else { root.EQT = root.EQT || {}; root.EQT.createSession = createSession; }
})(this);
