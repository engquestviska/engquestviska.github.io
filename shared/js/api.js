/* English Quest shared API helpers */
(function(global) {
  // MIGRATED 2026-07-30 to the new Turso/Cloudflare backend (via its compatibility
  // bridge). To ROLL BACK, restore the old Apps Script URL on the next line:
  // var API_URL = 'https://script.google.com/macros/s/AKfycbxK9MSUe75KC7xMhZhy-9a4omw9rVf1RugYR72OaGlzuNiKJvS5XRoZRp9hcTzjibHuxg/exec';
  var API_URL = 'https://engquest-api.engquestviska.workers.dev/exec';
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

  // Apps Script web apps do NOT send CORS headers, so a normal cross-origin
  // fetch() can never read the response — it just hangs/redirects for many
  // seconds before failing, delaying every call. So we go straight to JSONP
  // (a <script> tag), which is the only path that actually works here.
  function attemptGet(params) {
    return new Promise(function(resolve, reject) {
      var cb = 'cb_' + Date.now() + '_' + Math.random().toString(36).slice(2);
      var p = Object.assign({}, params, { callback: cb, _t: Date.now() });
      var script = document.createElement('script');
      var timer = setTimeout(function() { cleanup(); reject(new Error('Connection timed out')); }, 20000);
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
  }

  // Retry transient transport failures (e.g. a 429 "too many requests" rate-limit)
  // with exponential backoff. A real response — including an {error:...} payload —
  // resolves and is NOT retried; only network/rate-limit failures back off.
  function apiGet(params, _attempt) {
    _attempt = _attempt || 0;
    return attemptGet(params).catch(function(err) {
      if (_attempt < 3) {
        var delay = 800 * Math.pow(2, _attempt); // 0.8s → 1.6s → 3.2s
        return new Promise(function(resolve) { setTimeout(resolve, delay); })
          .then(function() { return apiGet(params, _attempt + 1); });
      }
      throw err;
    });
  }

  // POST a JSON body (used for large payloads like image uploads that can't fit
  // in a GET URL). Apps Script web apps don't return CORS headers, so we send a
  // "simple" text/plain request in no-cors mode: the request executes on the
  // server but the response is opaque (unreadable). Callers confirm success with
  // a follow-up GET (e.g. getMySubmissions) rather than reading the response.
  function apiPost(body) {
    return fetch(API_URL, {
      method: 'POST',
      mode: 'no-cors',
      headers: { 'Content-Type': 'text/plain;charset=utf-8' },
      body: JSON.stringify(body)
    });
  }

  global.EQApi = {
    url: API_URL,
    classes: CLASSES,
    grade10Classes: GRADE10_CLASSES,
    grade11Classes: GRADE11_CLASSES,
    classLabels: CLASS_LABELS,
    get: apiGet,
    post: apiPost
  };
})(window);
