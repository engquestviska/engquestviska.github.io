/* English Quest shared API helpers */
(function(global) {
  var API_URL = 'https://script.google.com/macros/s/AKfycbxK9MSUe75KC7xMhZhy-9a4omw9rVf1RugYR72OaGlzuNiKJvS5XRoZRp9hcTzjibHuxg/exec';
  var classConfig = global.EQClasses || null;
  var CLASS_LABELS = classConfig ? classConfig.labels : {
    XE1: 'X E-1',
    XE2: 'X E-2',
    XE3: 'X E-3',
    XE4: 'X E-4',
    XE5: 'X E-5',
    XIF7: 'XI F-7',
    XIF8: 'XI F-8',
    XIF9: 'XI F-9'
  };
  var CLASSES = classConfig ? classConfig.all.map(function(item) { return item.id; }) : Object.keys(CLASS_LABELS);
  var GRADE10_CLASSES = classConfig ? classConfig.idsForGrade('10') : ['XE1', 'XE2', 'XE3', 'XE4', 'XE5'];
  var GRADE11_CLASSES = classConfig ? classConfig.idsForGrade('11') : ['XIF7', 'XIF8', 'XIF9'];

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
    grade10Classes: GRADE10_CLASSES,
    grade11Classes: GRADE11_CLASSES,
    classLabels: CLASS_LABELS,
    get: apiGet
  };
})(window);
