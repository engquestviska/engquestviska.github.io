/* English Quest shared attendance API helper */
(function(global) {
  var API_URL = 'https://script.google.com/macros/s/AKfycbxHaOK1WSG5hVVrzOUwGI3QxY3eSbjaAseRVxVaMXU-b8k9Stn4exUUbvB56dYbKLfr/exec';

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
