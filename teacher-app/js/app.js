/* EngQuest Teacher app: frame, routing, class picker, login, toast and sheets. */
(function (global) {
  var EQT = global.EQT = global.EQT || {};
  var screens = {};
  var ORDER = ['attendance', 'submissions', 'xp', 'scores', 'more'];
  var current = null;      // name of the screen on show
  var currentHash = '';
  var session;
  var className = null;
  var toastTimer = null;

  function $(id) { return document.getElementById(id); }

  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function classes() { return (global.EQClasses && EQClasses.all) || []; }
  function classLabel(id) { return (global.EQApi && EQApi.classLabels && EQApi.classLabels[id]) || id || ''; }

  EQT.esc = esc;
  EQT.registerScreen = function (name, def) { screens[name] = def; };

  // ---- Toast --------------------------------------------------------------
  function toast(msg, type, action) {
    var t = $('toast');
    $('toastText').textContent = msg;
    t.className = 'toast' + (type ? ' ' + type : '');
    var btn = $('toastAction');
    if (action) {
      btn.textContent = action.label; btn.hidden = false;
      btn.onclick = function () { hideToast(); action.onClick(); };
    } else { btn.hidden = true; btn.onclick = null; }
    t.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(hideToast, action ? 5000 : 2600);
  }
  function hideToast() { $('toast').hidden = true; }

  // ---- Bottom sheet -------------------------------------------------------
  function openSheet(html, onReady) {
    var sheet = $('sheet');
    sheet.innerHTML = html;
    sheet.hidden = false; $('sheetBackdrop').hidden = false;
    if (onReady) onReady(sheet);
  }
  function closeSheet() { $('sheet').hidden = true; $('sheetBackdrop').hidden = true; $('sheet').innerHTML = ''; }

  // A friendlier confirm than window.confirm. Resolves true/false.
  function confirmSheet(title, text, okLabel, danger) {
    return new Promise(function (resolve) {
      openSheet('<h2>' + esc(title) + '</h2>' + (text ? '<p>' + esc(text) + '</p>' : '') +
        '<div class="sheet-actions"><button class="btn ghost" type="button" data-a="no">Cancel</button>' +
        '<button class="btn ' + (danger ? 'bad' : 'brand') + '" type="button" data-a="yes">' + esc(okLabel || 'OK') + '</button></div>',
        function (el) {
          el.onclick = function (e) {
            var b = e.target.closest('[data-a]'); if (!b) return;
            closeSheet(); resolve(b.getAttribute('data-a') === 'yes');
          };
          $('sheetBackdrop').onclick = function () { closeSheet(); resolve(false); };
        });
    });
  }

  function openClassSheet() {
    function group(grade, title) {
      var list = classes().filter(function (c) { return c.grade === grade; });
      if (!list.length) return '';
      return '<div class="sheet-group">' + title + '</div><div class="sheet-grid">' + list.map(function (c) {
        return '<button class="sheet-opt' + (c.id === className ? ' on' : '') + '" type="button" data-class="' + esc(c.id) + '">' + esc(c.label || c.id) + '</button>';
      }).join('') + '</div>';
    }
    openSheet('<h2>Choose a class</h2>' + group('10', 'Grade 10') + group('11', 'Grade 11'), function (el) {
      el.onclick = function (e) {
        var b = e.target.closest('[data-class]'); if (!b) return;
        var id = b.getAttribute('data-class');
        closeSheet();
        if (id === className) return;
        canLeave().then(function (ok) {
          if (!ok) return;
          setClass(id);
          renderCurrent();
        });
      };
      $('sheetBackdrop').onclick = closeSheet;
    });
  }

  function setClass(id) {
    className = id;
    session.setClass(id);
    $('classLabel').textContent = classLabel(id);
  }

  // ---- Screen context -----------------------------------------------------
  function makeCtx() {
    return {
      className: className,
      classLabel: classLabel(className),
      session: session,
      core: EQT.core,
      esc: esc,
      toast: toast,
      confirm: confirmSheet,
      openSheet: openSheet,
      closeSheet: closeSheet,
      setSubtitle: function (text, onClick) {
        var s = $('subtitle');
        s.textContent = text || '';
        s.classList.toggle('tappable', !!onClick);
        s.tabIndex = onClick ? 0 : -1;
        s.onclick = onClick || null;
      },
      setAction: function (onClick) {
        var a = $('headerAction');
        a.hidden = !onClick;
        a.onclick = onClick || null;
      },
      setBadge: setBadge
    };
  }

  function setBadge(tab, count) {
    var b = document.querySelector('.tab[data-tab="' + tab + '"] .badge');
    if (!b) return;
    b.hidden = !count;
    b.textContent = count > 99 ? '99+' : String(count || '');
  }

  // ---- Routing -------------------------------------------------------------
  function routeName() {
    var name = (location.hash || '').replace(/^#\/?/, '').split('?')[0];
    return ORDER.indexOf(name) > -1 ? name : 'attendance';
  }

  function canLeave() {
    var def = current && screens[current];
    if (!def || !def.leave) return Promise.resolve(true);
    return Promise.resolve(def.leave());
  }

  function renderCurrent() {
    var def = screens[current];
    var view = $('view');
    view.innerHTML = '';
    view.scrollTop = 0;
    hideToast();
    var ctx = makeCtx();
    ctx.setSubtitle('');
    ctx.setAction(null);
    $('screenTitle').textContent = def ? def.title : '';
    document.querySelectorAll('.tab').forEach(function (t) {
      t.classList.toggle('on', t.getAttribute('data-tab') === current);
    });
    if (def) def.render(view, ctx);
  }

  function onHashChange() {
    var next = routeName();
    if (next === current) return;
    canLeave().then(function (ok) {
      if (!ok) { history.replaceState(null, '', currentHash || '#/' + current); return; }
      current = next;
      currentHash = '#/' + next;
      renderCurrent();
    });
  }

  // ---- Login --------------------------------------------------------------
  function showLogin(note) {
    $('app').hidden = true;
    $('login').hidden = false;
    $('loginNote').textContent = note || 'Log in once. The app remembers you on this phone.';
    $('loginError').textContent = '';
    $('loginBtn').disabled = false; $('loginBtn').textContent = 'Log in';
    current = null;
  }

  function showApp() {
    $('login').hidden = true;
    $('app').hidden = false;
    var saved = session.getClass();
    var valid = classes().some(function (c) { return c.id === saved; });
    setClass(valid ? saved : (classes()[0] && classes()[0].id));
    if (!location.hash) history.replaceState(null, '', '#/attendance');
    current = routeName();
    currentHash = '#/' + current;
    renderCurrent();
  }

  function onLoginSubmit(e) {
    e.preventDefault();
    var user = $('loginUser').value.trim();
    var pass = $('loginPass').value.trim();
    if (!user || !pass) return;
    var btn = $('loginBtn');
    btn.disabled = true; btn.textContent = 'Checking…';
    $('loginError').textContent = '';
    session.login(user, pass).then(function (r) {
      if (!r.ok) {
        $('loginError').textContent = r.error;
        btn.disabled = false; btn.textContent = 'Log in';
        return;
      }
      $('loginPass').value = '';
      showApp();
    });
  }

  EQT.logout = function () {
    session.logout();
    try { ['eq_tu', 'eq_tp', 'eq_teacher_verified_at', 'eq_teacher_verified_user'].forEach(function (k) { sessionStorage.removeItem(k); }); } catch (e) { /* storage off */ }
    showLogin('You are logged out.');
  };

  EQT.start = function () {
    session = EQT.session = EQT.createSession({ storage: global.localStorage, api: global.EQApi });
    session.onExpired = function () { closeSheet(); showLogin('Please log in again.'); };
    $('loginForm').addEventListener('submit', onLoginSubmit);
    $('classPill').addEventListener('click', openClassSheet);
    global.addEventListener('hashchange', function () { if (!$('app').hidden) onHashChange(); });
    if (session.get()) showApp(); else showLogin();
  };
})(window);
