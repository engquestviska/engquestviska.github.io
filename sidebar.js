/* sidebar.js — shared sidebar for all secondary pages */
(function () {
  var SECURITY_MAINTENANCE = false;

  function showSecurityMaintenance() {
    if (!SECURITY_MAINTENANCE || document.getElementById('securityMaintenanceScreen')) return;

    document.documentElement.style.overflow = 'hidden';
    document.body.style.overflow = 'hidden';
    document.body.classList.add('security-maintenance-active');

    var maintenanceStyle = document.createElement('style');
    maintenanceStyle.textContent =
      'body.security-maintenance-active > :not(#securityMaintenanceScreen) { pointer-events:none !important; user-select:none !important; }' +
      '.security-maintenance-screen { position:fixed; inset:0; z-index:99999; display:flex; align-items:center; justify-content:center; padding:28px; background:radial-gradient(circle at 20% 20%, rgba(245,158,11,0.22), transparent 34%), radial-gradient(circle at 78% 26%, rgba(236,72,153,0.18), transparent 30%), linear-gradient(135deg,#1E1040 0%,#4C1D95 48%,#111827 100%); color:#fff; text-align:center; font-family:"IBM Plex Sans",system-ui,sans-serif; }' +
      '.security-maintenance-card { width:min(680px,100%); border:1px solid rgba(255,255,255,0.18); border-radius:28px; padding:clamp(30px,5vw,54px); background:rgba(255,255,255,0.10); box-shadow:0 24px 80px rgba(0,0,0,0.34); backdrop-filter:blur(16px); }' +
      '.security-maintenance-kicker { display:inline-flex; align-items:center; justify-content:center; min-height:34px; padding:7px 14px; border-radius:999px; background:rgba(245,158,11,0.18); border:1px solid rgba(245,158,11,0.42); color:#FDE68A; font-weight:800; font-size:0.78rem; letter-spacing:0.08em; text-transform:uppercase; margin-bottom:18px; }' +
      '.security-maintenance-card h1 { font-family:"Playfair Display",Georgia,serif; font-size:clamp(2.3rem,7vw,4.8rem); line-height:0.95; margin:0 0 18px; color:#fff; }' +
      '.security-maintenance-card p { max-width:560px; margin:0 auto; color:rgba(255,255,255,0.84); font-size:clamp(1rem,2vw,1.15rem); line-height:1.65; }' +
      '.security-maintenance-note { margin-top:24px; padding:14px 16px; border-radius:16px; background:rgba(255,255,255,0.12); color:rgba(255,255,255,0.92); font-weight:700; line-height:1.45; }';
    document.head.appendChild(maintenanceStyle);

    var screen = document.createElement('main');
    screen.className = 'security-maintenance-screen';
    screen.id = 'securityMaintenanceScreen';
    screen.setAttribute('role', 'alert');
    screen.setAttribute('aria-live', 'assertive');
    screen.innerHTML =
      '<section class="security-maintenance-card" aria-labelledby="securityMaintenanceTitle">' +
      '<div class="security-maintenance-kicker">Security Maintenance</div>' +
      '<h1 id="securityMaintenanceTitle">English Quest is temporarily locked.</h1>' +
      '<p>We are checking and securing the system. During this maintenance, student pages, submissions, scores, task status, ASAT packages, and teacher tools are unavailable.</p>' +
      '<div class="security-maintenance-note">Please wait for the official update in your class group before using the website again.</div>' +
      '</section>';
    document.body.appendChild(screen);
  }

  var HTML = [
    '<div class="sidebar-overlay" id="sidebarOverlay" onclick="closeSidebar()"></div>',
    '<nav class="sidebar" id="sidebar">',
    '  <div class="sidebar-head">',
    '    <img class="sidebar-logo" src="logo.jpg" alt="Logo"/>',
    '    <div class="sidebar-head-text">',
    '      <div class="sidebar-head-title">English Quest</div>',
    '      <div class="sidebar-head-sub">SMA Negeri 6 Surakarta</div>',
    '    </div>',
    '    <button class="sidebar-close" onclick="closeSidebar()">&#x2715;</button>',
    '  </div>',
    '  <a href="index.html" class="sidebar-home-btn">',
    '    <div>',
    '      <div class="sidebar-home-btn-text">&#127968; Homepage</div>',
    '      <div class="sidebar-home-btn-sub">Back to main dashboard</div>',
    '    </div>',
    '  </a>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-section">',
    '    <div class="sidebar-section-label">&#128194; Documents</div>',
    '    <a href="lessons.html" class="sidebar-item" data-page="lessons"><div class="sidebar-item-icon" style="background:rgba(124,58,237,0.2)">&#128209;</div><div class="sidebar-item-name">Lesson Materials</div></a>',
    '  </div>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-section">',
    '    <div class="sidebar-section-label">&#128101; Student Data</div>',
    '    <a href="students.html" class="sidebar-item" data-page="students"><div class="sidebar-item-icon" style="background:rgba(236,72,153,0.2)">&#127891;</div><div class="sidebar-item-name">Student List</div></a>',
    '    <a href="scores.html" class="sidebar-item" data-page="scores"><div class="sidebar-item-icon" style="background:rgba(249,115,22,0.2)">&#127942;</div><div class="sidebar-item-name">Student Scores</div></a>',
    '    <a href="tasks.html" class="sidebar-item" data-page="tasks"><div class="sidebar-item-icon" style="background:rgba(16,185,129,0.2)">&#128204;</div><div class="sidebar-item-name">Task Status</div></a>',
    '    <a href="activeness.html" class="sidebar-item" data-page="activeness"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">&#9889;</div><div class="sidebar-item-name">Activeness</div></a>',
    '    <a href="strikes.html" class="sidebar-item" data-page="strikes"><div class="sidebar-item-icon" style="background:rgba(220,38,38,0.2)">&#9888;&#65039;</div><div class="sidebar-item-name">Strikes</div></a>',
    '  </div>',
    '  <div class="sidebar-teacher" id="sidebarTeacherBox" style="display:none;">',
    '    <div class="sidebar-teacher-label">&#128273; Teacher Access</div>',
    '    <div id="sbTeacherLoggedOut">',
    '      <button style="width:100%;padding:10px;background:linear-gradient(135deg,#7C3AED,#5B21B6);color:#fff;font-family:inherit;font-weight:700;font-size:0.85rem;border:none;border-radius:10px;cursor:pointer;" onclick="closeSidebar();window.location.href=\'index.html\';">Login as Teacher</button>',
    '    </div>',
    '    <div id="sbTeacherLoggedIn" style="display:none;">',
    '      <div class="sidebar-teacher-logged">&#127891; Logged in as Teacher <span onclick="localStorage.removeItem(\'eq_tu\');localStorage.removeItem(\'eq_tp\');location.reload();">Logout</span></div>',
    '    </div>',
    '  </div>',
    '</nav>'
  ].join('\n');

  /* Push teacher section to bottom of sidebar */
  var style = document.createElement('style');
  style.textContent =
    '.sidebar { display:flex !important; flex-direction:column !important; overflow-y:auto !important; }' +
    '.sidebar-teacher { margin-top:auto; border-top:1px solid rgba(255,255,255,0.1); padding:16px; }' +
    /* Push teacher badge / login button left of the hamburger (40px wide at right:20px) */
    'header .header-right { right:70px !important; }' +
    'header .teacher-badge { right:70px !important; }' +
    'header .teacher-btn { right:70px !important; }';
  document.head.appendChild(style);

  document.addEventListener('DOMContentLoaded', function () {
    showSecurityMaintenance();

    /* Skip if page already has its own sidebar (index.html) */
    if (document.getElementById('sidebar')) return;

    var wrap = document.createElement('div');
    wrap.innerHTML = HTML;
    while (wrap.firstChild) document.body.appendChild(wrap.firstChild);

    updateSidebarTeacher();

    /* Set active nav item */
    var page = document.body.getAttribute('data-page') || '';
    document.querySelectorAll('.sidebar-item[data-page]').forEach(function (el) {
      el.classList.toggle('active', el.getAttribute('data-page') === page);
    });
  });

  window.toggleSidebar = function () {
    var sb = document.getElementById('sidebar');
    var ov = document.getElementById('sidebarOverlay');
    var hb = document.getElementById('hamburgerBtn');
    if (!sb) return;
    var open = sb.classList.toggle('open');
    if (ov) ov.classList.toggle('open', open);
    if (hb) hb.classList.toggle('open', open);
    document.body.style.overflow = open ? 'hidden' : '';
    updateSidebarTeacher();
    var page = document.body.getAttribute('data-page') || '';
    document.querySelectorAll('.sidebar-item[data-page]').forEach(function (el) {
      el.classList.toggle('active', el.getAttribute('data-page') === page);
    });
  };

  window.closeSidebar = function () {
    var sb = document.getElementById('sidebar');
    var ov = document.getElementById('sidebarOverlay');
    var hb = document.getElementById('hamburgerBtn');
    if (sb) sb.classList.remove('open');
    if (ov) ov.classList.remove('open');
    if (hb) hb.classList.remove('open');
    document.body.style.overflow = '';
  };

  window.updateSidebarTeacher = function () {
    var loggedIn = !!localStorage.getItem('eq_tu');
    var params = new URLSearchParams(window.location.search);
    var teacherAccess = params.get('teacher') === '1' || window.location.hash === '#teacher';
    var box = document.getElementById('sidebarTeacherBox');
    var out = document.getElementById('sbTeacherLoggedOut');
    var inn = document.getElementById('sbTeacherLoggedIn');
    if (box) box.style.display = (loggedIn || teacherAccess) ? 'block' : 'none';
    if (out) out.style.display = (!loggedIn && teacherAccess) ? 'block' : 'none';
    if (inn) inn.style.display = loggedIn ? 'block' : 'none';
    var gw  = document.getElementById('sbGuessWho');
    if (gw)  gw.style.display  = loggedIn ? 'flex'  : 'none';
  };

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') window.closeSidebar();
  });

})();
