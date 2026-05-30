(function(global) {
  var API_URL = 'https://script.google.com/macros/s/AKfycbyNLYf1615P2rAZsW16sGxPO3jCGGK6TpOwpBmmwNNnCRI8hmwR0eN6d2DVAqj4EsELbg/exec';

  function buildUrl(params) {
    var query = new URLSearchParams(params || {});
    return API_URL + '?' + query.toString();
  }

  function jsonp(params) {
    return new Promise(function(resolve, reject) {
      var cb = 'eq_next_cb_' + Date.now() + '_' + Math.random().toString(36).slice(2);
      var query = Object.assign({}, params || {}, { callback: cb, _t: Date.now() });
      var script = document.createElement('script');
      var timer = setTimeout(function() {
        cleanup();
        reject(new Error('Connection timed out'));
      }, 15000);

      function cleanup() {
        delete global[cb];
        clearTimeout(timer);
        if (script.parentNode) script.parentNode.removeChild(script);
      }

      global[cb] = function(data) {
        cleanup();
        if (data && data.error) reject(new Error(data.error));
        else resolve(data);
      };

      script.onerror = function() {
        cleanup();
        reject(new Error('API access is not public yet'));
      };
      script.src = buildUrl(query);
      document.body.appendChild(script);
    });
  }

  async function get(params) {
    try {
      var response = await fetch(buildUrl(params), { cache: 'no-store' });
      var text = await response.text();
      var data;

      try {
        data = JSON.parse(text);
      } catch (err) {
        throw new Error(response.ok ? 'Invalid API response' : 'API access is not public yet');
      }

      if (!response.ok || data.error) {
        throw new Error(data.error || 'API request failed');
      }
      return data;
    } catch (err) {
      return jsonp(params);
    }
  }

  global.EQNextAPI = {
    url: API_URL,
    get: get
  };
})(window);
