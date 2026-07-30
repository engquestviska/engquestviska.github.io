/* English Quest shared attendance API helper */
(function(global) {
  // Repointed 2026-07-10 from the old presensi backend (old-year data) to the
  // main Scores API, which now serves attendance via getClassData reading the
  // new "EQ Attendance 2026-2027" sheet. All attendance pages are read-only.
  // MIGRATED 2026-07-30 to the new Turso/Cloudflare backend (rollback: restore the old
  // Apps Script URL — see shared/js/api.js).
  var API_URL = 'https://engquest-api.engquestviska.workers.dev/exec';

  function get(params) {
    return new Promise(function(resolve, reject) {
      var callback = 'att_cb_' + Date.now() + '_' + Math.random().toString(36).slice(2);
      var payload = Object.assign({}, params, { callback: callback, _t: Date.now() });
      var script = document.createElement('script');
      var timer = setTimeout(function() {
        cleanup();
        reject(new Error('Attendance request timed out'));
      }, 15000);

      function cleanup() {
        clearTimeout(timer);
        delete global[callback];
        if (script.parentNode) script.parentNode.removeChild(script);
      }

      global[callback] = function(data) {
        cleanup();
        resolve(data);
      };
      script.onerror = function() {
        cleanup();
        reject(new Error('Attendance network error'));
      };
      script.src = API_URL + '?' + new URLSearchParams(payload);
      (document.body || document.head).appendChild(script);
    });
  }

  global.EQAttendance = {
    url: API_URL,
    get: get
  };
})(window);
