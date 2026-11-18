/* EngQuest Chronicle switch: once the semester has ended (the server decides, from the date the
   teacher set), a signed-in student opening any student page is taken to the Chronicle.
   Before that date, or if the server can't be reached, the page works as usual. */
(function (global) {
  var KEY = 'eq_chronicle_status';
  var TTL = 10 * 60 * 1000;
  var target = new URL('../../chronicle.html', document.currentScript && document.currentScript.src || location.href).href;

  function cached() {
    try {
      var c = JSON.parse(global.sessionStorage.getItem(KEY) || 'null');
      return c && Date.now() - c.at < TTL ? c : null;
    } catch (e) { return null; }
  }
  function remember(active) {
    try { global.sessionStorage.setItem(KEY, JSON.stringify({ active: !!active, at: Date.now() })); } catch (e) {}
  }
  function go(active) { if (active) location.replace(target); }

  function check() {
    var profile = global.EQStudent ? global.EQStudent.read() : null;
    if (!profile || !profile.hasProfile || !global.EQApi) return;
    var c = cached();
    if (c) { go(c.active); return; }
    global.EQApi.get({ action: 'getChronicleStatus' })
      .then(function (r) { var on = !!(r && r.active); remember(on); go(on); })
      .catch(function () {});
  }

  global.EQChronicleGate = { check: check };
  check();
})(window);
