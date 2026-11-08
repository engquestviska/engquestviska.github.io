/* English Quest shared auth/session helpers */
(function(global) {
  var VERIFY_KEY = 'eq_teacher_verified_at';
  var VERIFY_USER_KEY = 'eq_teacher_verified_user';
  var VERIFY_MAX_AGE = 1000 * 60 * 60 * 8;
  var verifyInFlight = false;

  // Teacher password gate is ACTIVE. Login verified working end-to-end
  // (2026-07-07): plain + hashed-session paths both return ok:true against the
  // live backend. Password is set via Script Property TEACHER_PASSWORD_SHA256.
  var GATE_DISABLED = false;

  // The login is remembered in localStorage (owner's choice, 2026-11-08), so every tab and
  // a reopened browser stay logged in until Logout. A login handed over in sessionStorage
  // (the old teacher app's "More" links) is moved here on first use.
  function store(key, value) {
    try { localStorage.setItem(key, value); } catch (e) {}
  }

  function read(key) {
    try {
      var kept = localStorage.getItem(key);
      if (kept) return kept;
      var handed = sessionStorage.getItem(key);
      if (handed) { store(key, handed); sessionStorage.removeItem(key); }
      return handed || '';
    } catch (e) {
      return '';
    }
  }

  function getTeacher() {
    return {
      username: read('eq_tu'),
      password: read('eq_tp')
    };
  }

  function saveTeacher(username, password) {
    store('eq_tu', username);
    store('eq_tp', password);
    markTeacherVerified(username);
  }

  function hasTeacher() {
    var teacher = getTeacher();
    return !!(teacher.username && teacher.password);
  }

  function clearVerification() {
    [localStorage, sessionStorage].forEach(function(s) {
      try { s.removeItem(VERIFY_KEY); s.removeItem(VERIFY_USER_KEY); } catch (e) {}
    });
  }

  function markTeacherVerified(username) {
    store(VERIFY_KEY, String(Date.now()));
    store(VERIFY_USER_KEY, username || '');
  }

  function hasFreshVerification(username) {
    var verifiedAt = Number(read(VERIFY_KEY) || 0);
    var verifiedUser = read(VERIFY_USER_KEY);
    return !!(verifiedAt && verifiedUser === username && Date.now() - verifiedAt < VERIFY_MAX_AGE);
  }

  function logoutTeacher(redirectTo) {
    [localStorage, sessionStorage].forEach(function(s) {
      try { s.removeItem('eq_tu'); s.removeItem('eq_tp'); } catch (e) {}
    });
    clearVerification();
    if (redirectTo) location.href = redirectTo;
  }

  // The dashboard's login link with this page as the place to come back to.
  function loginLink() {
    var page = location.pathname.split('/').pop() || 'index.html';
    return 'index.html?next=' + encodeURIComponent(page + location.search);
  }

  // Only a page in the teacher folder is a safe place to send the teacher after login.
  function nextPage() {
    var next = new URLSearchParams(location.search).get('next') || '';
    return /^[a-z0-9-]+\.html(\?[^#]*)?$/i.test(next) && next.indexOf('index.html') !== 0 ? next : '';
  }

  function setGateMessage(options, message) {
    var access = document.getElementById(options.accessId);
    if (!access || !message) return;
    var title = access.querySelector('.access-title');
    var copy = access.querySelector('.access-copy');
    if (title) title.textContent = message.title;
    if (copy) copy.textContent = message.copy;
  }

  function revealTeacherApp(options, teacher) {
    var access = document.getElementById(options.accessId);
    var app = document.getElementById(options.appId);
    var pill = options.pillId ? document.getElementById(options.pillId) : null;
    if (pill) pill.textContent = teacher.username || 'Teacher';
    if (access) access.hidden = true;
    if (app) app.hidden = false;
  }

  function hideTeacherApp(options) {
    var access = document.getElementById(options.accessId);
    var app = document.getElementById(options.appId);
    if (access) {
      access.hidden = false;
      access.querySelectorAll('a[href="index.html"]').forEach(function(a) { a.href = loginLink(); });
    }
    if (app) app.hidden = true;
  }

  function verifyTeacher(options) {
    var teacher = getTeacher();
    if (!teacher.username || !teacher.password || verifyInFlight) return;
    verifyInFlight = true;
    setGateMessage(options, {
      title: 'Verifying teacher session.',
      copy: 'Checking your saved teacher login before opening this management page.'
    });
    if (!global.EQApi || !global.EQApi.get) {
      logoutTeacher();
      setGateMessage(options, {
        title: 'Teacher login required.',
        copy: 'The teacher API is not available on this page. Return to the teacher dashboard and log in again.'
      });
      verifyInFlight = false;
      return;
    }
    global.EQApi.get({ action: 'checkLogin', username: teacher.username, password: teacher.password }).then(function(data) {
      verifyInFlight = false;
      if (data && data.ok) {
        markTeacherVerified(teacher.username);
        location.reload();
        return;
      }
      logoutTeacher();
      setGateMessage(options, {
        title: 'Teacher login required.',
        copy: 'Your saved teacher session could not be verified. Open the teacher dashboard and log in again.'
      });
    }).catch(function() {
      verifyInFlight = false;
      hideTeacherApp(options);
      setGateMessage(options, {
        title: 'Could not verify teacher session.',
        copy: 'Check your connection, then return to the teacher dashboard and log in again.'
      });
    });
  }

  function showTeacherGate(options) {
    if (GATE_DISABLED) {
      revealTeacherApp(options, { username: getTeacher().username || 'Teacher' });
      return true;
    }
    var teacher = getTeacher();
    if (teacher.username && teacher.password && hasFreshVerification(teacher.username)) {
      revealTeacherApp(options, teacher);
      return true;
    }
    hideTeacherApp(options);
    if (teacher.username && teacher.password) {
      verifyTeacher(options);
    }
    return false;
  }

  global.EQAuth = {
    getTeacher: getTeacher,
    hasTeacher: hasTeacher,
    saveTeacher: saveTeacher,
    loginLink: loginLink,
    nextPage: nextPage,
    gateDisabled: GATE_DISABLED,
    markTeacherVerified: markTeacherVerified,
    clearVerification: clearVerification,
    logoutTeacher: logoutTeacher,
    showTeacherGate: showTeacherGate
  };
})(window);
