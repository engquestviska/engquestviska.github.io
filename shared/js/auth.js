/* English Quest shared auth/session helpers */
(function(global) {
  var VERIFY_KEY = 'eq_teacher_verified_at';
  var VERIFY_USER_KEY = 'eq_teacher_verified_user';
  var VERIFY_MAX_AGE = 1000 * 60 * 60 * 8;
  var verifyInFlight = false;

  // Security: teacher credentials live in sessionStorage (cleared when the
  // browser/tab closes) so a login no longer persists forever. Proactively
  // remove any long-lived credentials left over in localStorage.
  try {
    localStorage.removeItem('eq_tu');
    localStorage.removeItem('eq_tp');
  } catch (e) {}

  function getTeacher() {
    return {
      username: sessionStorage.getItem('eq_tu') || '',
      password: sessionStorage.getItem('eq_tp') || ''
    };
  }

  function hasTeacher() {
    var teacher = getTeacher();
    return !!(teacher.username && teacher.password);
  }

  function clearVerification() {
    sessionStorage.removeItem(VERIFY_KEY);
    sessionStorage.removeItem(VERIFY_USER_KEY);
  }

  function markTeacherVerified(username) {
    sessionStorage.setItem(VERIFY_KEY, String(Date.now()));
    sessionStorage.setItem(VERIFY_USER_KEY, username || '');
  }

  function hasFreshVerification(username) {
    var verifiedAt = Number(sessionStorage.getItem(VERIFY_KEY) || 0);
    var verifiedUser = sessionStorage.getItem(VERIFY_USER_KEY) || '';
    return !!(verifiedAt && verifiedUser === username && Date.now() - verifiedAt < VERIFY_MAX_AGE);
  }

  function logoutTeacher(redirectTo) {
    sessionStorage.removeItem('eq_tu');
    sessionStorage.removeItem('eq_tp');
    localStorage.removeItem('eq_tu');
    localStorage.removeItem('eq_tp');
    clearVerification();
    if (redirectTo) location.href = redirectTo;
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
    if (access) access.hidden = false;
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
    markTeacherVerified: markTeacherVerified,
    clearVerification: clearVerification,
    logoutTeacher: logoutTeacher,
    showTeacherGate: showTeacherGate
  };
})(window);
