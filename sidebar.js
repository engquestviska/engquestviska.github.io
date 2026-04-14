/* sidebar.js — shared sidebar for all secondary pages */
(function () {

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
    '    <a href="session.html" class="sidebar-item" data-page="session"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">&#127916;</div><div class="sidebar-item-name">Class Session</div></a>',
    '    <a href="assignments.html" class="sidebar-item" data-page="assignments"><div class="sidebar-item-icon" style="background:rgba(59,130,246,0.2)">&#128203;</div><div class="sidebar-item-name">Assignments</div></a>',
    '    <a href="submission.html" class="sidebar-item" data-page="submission"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">&#128221;</div><div class="sidebar-item-name">Submission</div></a>',
    '    <a href="submission_status.html" class="sidebar-item" data-page="submission_status"><div class="sidebar-item-icon" style="background:rgba(16,185,129,0.2)">&#9989;</div><div class="sidebar-item-name">Submission Status</div></a>',
    '  </div>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-section">',
    '    <div class="sidebar-section-label">&#128101; Student Data</div>',
    '    <a href="students.html" class="sidebar-item" data-page="students"><div class="sidebar-item-icon" style="background:rgba(236,72,153,0.2)">&#127891;</div><div class="sidebar-item-name">Student List</div></a>',
    '    <a href="attendance.html" class="sidebar-item" data-page="attendance"><div class="sidebar-item-icon" style="background:rgba(20,184,166,0.2)">&#9989;</div><div class="sidebar-item-name">Attendance</div></a>',
    '    <a href="scores.html" class="sidebar-item" data-page="scores"><div class="sidebar-item-icon" style="background:rgba(249,115,22,0.2)">&#127942;</div><div class="sidebar-item-name">Student Scores</div></a>',
    '    <a href="tasks.html" class="sidebar-item" data-page="tasks"><div class="sidebar-item-icon" style="background:rgba(16,185,129,0.2)">&#128204;</div><div class="sidebar-item-name">Task Status</div></a>',
    '    <a href="activeness.html" class="sidebar-item" data-page="activeness"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">&#9889;</div><div class="sidebar-item-name">Activeness</div></a>',
    '    <a href="strikes.html" class="sidebar-item" data-page="strikes"><div class="sidebar-item-icon" style="background:rgba(220,38,38,0.2)">&#9888;&#65039;</div><div class="sidebar-item-name">Strikes</div></a>',
    '  </div>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-section">',
    '    <div class="sidebar-section-label">&#128226; Info &amp; Games</div>',
    '    <a href="summative.html" class="sidebar-item" data-page="summative"><div class="sidebar-item-icon" style="background:rgba(16,185,129,0.2)">&#127919;</div><div class="sidebar-item-name">Summative Topic</div></a>',
    '    <a href="jeopardyquiz.html" class="sidebar-item" data-page="jeopardyquiz"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">&#127918;</div><div class="sidebar-item-name">Chapter 4 Quiz</div></a>',
    '    <a href="gallery.html" class="sidebar-item" data-page="gallery"><div class="sidebar-item-icon" style="background:rgba(236,72,153,0.2)">&#128247;</div><div class="sidebar-item-name">Photo Gallery</div></a>',
    '    <a href="chapter5_guess_who.html" class="sidebar-item" data-page="guess_who" id="sbGuessWho" style="display:none"><div class="sidebar-item-icon" style="background:rgba(236,72,153,0.2)">&#129300;</div><div class="sidebar-item-name">Guess Who? <span style="font-size:0.65rem;background:#7C3AED;color:#fff;padding:1px 6px;border-radius:999px;margin-left:4px;font-family:IBM Plex Mono,monospace;">Ch.5</span></div></a>',
    '  </div>',
    '  <div class="sidebar-teacher">',
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
    var out = document.getElementById('sbTeacherLoggedOut');
    var inn = document.getElementById('sbTeacherLoggedIn');
    if (out) out.style.display = loggedIn ? 'none' : 'block';
    if (inn) inn.style.display = loggedIn ? 'block' : 'none';
    var gw  = document.getElementById('sbGuessWho');
    if (gw)  gw.style.display  = loggedIn ? 'flex'  : 'none';
  };

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') window.closeSidebar();
  });

})();
