/* English Quest shared auth/session helpers */
(function(global) {
  function getTeacher() {
    return {
      username: localStorage.getItem('eq_tu') || '',
      password: localStorage.getItem('eq_tp') || ''
    };
  }

  function hasTeacher() {
    var teacher = getTeacher();
    return !!(teacher.username && teacher.password);
  }

  function logoutTeacher(redirectTo) {
    localStorage.removeItem('eq_tu');
    localStorage.removeItem('eq_tp');
    if (redirectTo) location.href = redirectTo;
  }

  function showTeacherGate(options) {
    var access = document.getElementById(options.accessId);
    var app = document.getElementById(options.appId);
    var pill = options.pillId ? document.getElementById(options.pillId) : null;
    if (hasTeacher()) {
      if (pill) pill.textContent = getTeacher().username || 'Teacher';
      if (access) access.hidden = true;
      if (app) app.hidden = false;
      return true;
    }
    if (access) access.hidden = false;
    if (app) app.hidden = true;
    return false;
  }

  global.EQAuth = {
    getTeacher: getTeacher,
    hasTeacher: hasTeacher,
    logoutTeacher: logoutTeacher,
    showTeacherGate: showTeacherGate
  };
})(window);
