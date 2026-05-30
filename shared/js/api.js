/* English Quest shared API helpers */
(function(global) {
  var API_URL = 'https://script.google.com/macros/s/AKfycbxK9MSUe75KC7xMhZhy-9a4omw9rVf1RugYR72OaGlzuNiKJvS5XRoZRp9hcTzjibHuxg/exec';
  var CLASS_LABELS = {
    XE1: 'X E-1',
    XE4: 'X E-4',
    XE5: 'X E-5',
    XE6: 'X E-6',
    XE7: 'X E-7',
    XE8: 'X E-8',
    XE9: 'X E-9',
    XE10: 'X E-10',
    XE11: 'X E-11'
  };
  var CLASSES = Object.keys(CLASS_LABELS);

  function apiGet(params) {
    var url = API_URL + '?' + new URLSearchParams(params);
    return fetch(url).then(function(res) {
      if (!res.ok) throw new Error('Network error');
      return res.json();
    }).catch(function() {
      return new Promise(function(resolve, reject) {
        var cb = 'cb_' + Date.now() + '_' + Math.random().toString(36).slice(2);
        var p = Object.assign({}, params, { callback: cb, _t: Date.now() });
        var script = document.createElement('script');
        var timer = setTimeout(function() { cleanup(); reject(new Error('Connection timed out')); }, 15000);
        function cleanup() {
          delete global[cb];
          clearTimeout(timer);
          if (script.parentNode) script.parentNode.removeChild(script);
        }
        global[cb] = function(data) { cleanup(); resolve(data); };
        script.onerror = function() { cleanup(); reject(new Error('Network error')); };
        script.src = API_URL + '?' + new URLSearchParams(p);
        document.body.appendChild(script);
      });
    });
  }

  global.EQApi = {
    url: API_URL,
    classes: CLASSES,
    classLabels: CLASS_LABELS,
    get: apiGet
  };
})(window);
