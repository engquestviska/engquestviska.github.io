/* EngQuest Chronicle switch: once the semester has ended for the student's class (the server
   decides, from the date the teacher set; exempt classes such as XE5 keep the normal site), a
   signed-in student opening a student page is taken to the Chronicle. Materials and Assignments
   stay open. Before that date, or if the server can't be reached, the page works as usual. */
(function (global) {
  var KEY = 'eq_chronicle_status';
  var TTL = 10 * 60 * 1000;
  var OPEN_PAGES = /\/(lessons|assignments)\.html$/;
  var target = new URL('../../chronicle.html', document.currentScript && document.currentScript.src || location.href).href;

  function cached(cls) {
    try {
      var c = JSON.parse(global.sessionStorage.getItem(KEY) || 'null');
      return c && c.cls === cls && Date.now() - c.at < TTL ? c : null;
    } catch (e) { return null; }
  }
  function remember(cls, active) {
    try { global.sessionStorage.setItem(KEY, JSON.stringify({ cls: cls, active: !!active, at: Date.now() })); } catch (e) {}
  }
  function go(active) { if (active && !OPEN_PAGES.test(location.pathname)) location.replace(target); }

  function check() {
    var profile = global.EQStudent ? global.EQStudent.read() : null;
    if (!profile || !profile.hasProfile || !global.EQApi) return;
    var cls = profile.classId;
    var c = cached(cls);
    if (c) { go(c.active); return; }
    global.EQApi.get({ action: 'getChronicleStatus', className: cls })
      .then(function (r) { var on = !!(r && r.active); remember(cls, on); go(on); })
      .catch(function () {});
  }

  global.EQChronicleGate = { check: check };
  // The API helper adds a <script> to <body>, so wait until the body exists.
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', check);
  else check();
})(window);
