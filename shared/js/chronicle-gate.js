/* EngQuest Chronicle switch for student pages. Once the semester has ended for the student's class
   (the server decides from the dates the teacher set; exempt classes such as XE5 keep everything):
   - the pages for doing work (Submission, My Vocabulary) close and send the student Home,
     and links to them are hidden; every page for looking at stats, Materials and Assignments stays;
   - Home shows the Chronicle banner: "coming, make your PIN" until Chronicle opens, then "ready".
   Before that date, or if the server can't be reached, pages work as usual. */
(function (global) {
  var KEY = 'eq_chronicle_status';
  var TTL = 10 * 60 * 1000;
  var CLOSED = ['submission', 'vocabulary'];
  var here = location.pathname;
  var base = document.currentScript && document.currentScript.src || location.href;
  var chronicleUrl = new URL('../../chronicle.html', base).href;

  function cached(cls) {
    try {
      var c = JSON.parse(global.sessionStorage.getItem(KEY) || 'null');
      return c && c.cls === cls && Date.now() - c.at < TTL ? c : null;
    } catch (e) { return null; }
  }
  function remember(cls, r) {
    var c = { cls: cls, active: !!(r && r.active), open: !!(r && r.open), openAt: (r && r.openAt) || '', at: Date.now() };
    try { global.sessionStorage.setItem(KEY, JSON.stringify(c)); } catch (e) {}
    return c;
  }

  function isHome() { return /\/grade1[01]\/(index\.html)?$/.test(here); }
  function closedPage() { return CLOSED.some(function (n) { return new RegExp('/' + n + '\\.html$').test(here); }); }

  function dayText(iso) {
    try { return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric', timeZone: 'Asia/Jakarta' }); }
    catch (e) { return ''; }
  }

  function hideClosedLinks() {
    var css = CLOSED.map(function (n) { return 'a[href$="' + n + '.html"]'; }).join(',') + '{display:none!important}';
    var st = document.createElement('style');
    st.textContent = css;
    document.head.appendChild(st);
  }

  function banner(c, profile) {
    var main = document.querySelector('main') || document.body;
    if (!main || document.getElementById('eq-chronicle-banner')) return;
    var pinReady = false;
    try { pinReady = !!global.localStorage.getItem('eq_chronicle_pin_' + profile.classId + '_' + profile.studentNo); } catch (e) {}
    var day = dayText(c.openAt);
    var title = c.open ? 'Your semester, <i>wrapped.</i>' : 'Your semester story is <i>coming.</i>';
    var sub = c.open ? 'Your EngQuest Chronicle is ready. Tap to see it.'
      : (pinReady ? 'Your PIN is ready. Chronicle opens on ' + day + '.'
        : 'Opens on ' + day + '. Make your 6-number PIN now so it’s ready.');
    var cta = c.open ? 'Open my Chronicle' : (pinReady ? 'PIN ready ✓' : 'Make my PIN');
    var a = document.createElement('a');
    a.id = 'eq-chronicle-banner';
    a.href = chronicleUrl;
    a.innerHTML =
      '<span class="eqcb-k">ENGQUEST CHRONICLE · SEMESTER 1</span>' +
      '<span class="eqcb-t">' + title + '</span>' +
      '<span class="eqcb-s">' + sub + '</span>' +
      '<span class="eqcb-b">' + cta + ' →</span>' +
      '<span class="eqcb-dot" aria-hidden="true"></span>';
    var grade11 = /\/grade11\//.test(here);
    var st = document.createElement('style');
    st.textContent =
      '#eq-chronicle-banner{position:relative;overflow:hidden;display:flex;flex-direction:column;gap:6px;margin:0 0 16px;padding:18px 20px 20px;border-radius:20px;' +
      'background:#F4F2ED;color:#141414;text-decoration:none;border:1.5px solid #141414;font-family:"Nunito",system-ui,sans-serif}' +
      '#eq-chronicle-banner .eqcb-k{font-size:11px;font-weight:800;letter-spacing:.12em;opacity:.65;border-top:1.5px solid #141414;border-bottom:1px solid #141414;padding:4px 0;position:relative;z-index:1}' +
      '#eq-chronicle-banner .eqcb-t{font:800 30px/1.05 "Playfair Display",Georgia,serif;position:relative;z-index:1;max-width:80%}' +
      '#eq-chronicle-banner .eqcb-t i{font-style:italic;color:' + (grade11 ? '#0F8A73' : '#2450E0') + '}' +
      '#eq-chronicle-banner .eqcb-s{font-size:15px;font-weight:700;position:relative;z-index:1;max-width:80%}' +
      '#eq-chronicle-banner .eqcb-b{align-self:flex-start;margin-top:6px;background:#141414;color:#F4F2ED;border-radius:999px;padding:9px 16px;font-weight:800;font-size:14px;position:relative;z-index:1}' +
      '#eq-chronicle-banner .eqcb-dot{position:absolute;width:190px;height:190px;border-radius:50%;right:-60px;bottom:-80px;background:' + (grade11 ? '#0F8A73' : '#2450E0') + ';animation:eqcbBreathe 6s ease-in-out infinite}' +
      '#eq-chronicle-banner:focus-visible{outline:3px solid ' + (grade11 ? '#0F8A73' : '#2450E0') + ';outline-offset:3px}' +
      '@keyframes eqcbBreathe{50%{transform:scale(1.08) translate(-6px,4px)}}' +
      '@media (prefers-reduced-motion:reduce){#eq-chronicle-banner .eqcb-dot{animation:none}}';
    document.head.appendChild(st);
    main.insertBefore(a, main.firstChild);
  }

  function apply(c, profile) {
    if (!c.active) return;
    if (closedPage()) { location.replace(isHome() ? location.href : './index.html'); return; }
    hideClosedLinks();
    if (isHome()) banner(c, profile);
  }

  function check() {
    var profile = global.EQStudent ? global.EQStudent.read() : null;
    if (!profile || !profile.hasProfile || !global.EQApi) return;
    var cls = profile.classId;
    var c = cached(cls);
    if (c) { apply(c, profile); return; }
    global.EQApi.get({ action: 'getChronicleStatus', className: cls })
      .then(function (r) { apply(remember(cls, r), profile); })
      .catch(function () {});
  }

  global.EQChronicleGate = { check: check };
  // The API helper adds a <script> to <body>, so wait until the body exists.
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', check);
  else check();
})(window);
