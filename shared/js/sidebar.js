/* sidebar.js — shared sidebar for all secondary pages */
(function () {

  var HTML = [
    '<div class="sidebar-overlay" id="sidebarOverlay" onclick="closeSidebar()"></div>',
    '<nav class="sidebar" id="sidebar">',
    '  <div class="sidebar-head">',
    '    <img class="sidebar-logo" src="../logo.jpg" alt="Logo"/>',
    '    <div class="sidebar-head-text">',
    '      <div class="sidebar-head-title">English Quest</div>',
    '      <div class="sidebar-head-sub">SMA Negeri 6 Surakarta</div>',
    '    </div>',
    '    <button class="sidebar-close" onclick="closeSidebar()">&#x2715;</button>',
    '  </div>',
    '  <a href="index.html" class="sidebar-home-btn">',
    '    <div>',
    '      <div class="sidebar-home-btn-text">&#127968; Grade 10</div>',
    '      <div class="sidebar-home-btn-sub">Back to dashboard</div>',
    '    </div>',
    '  </a>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-section">',
    '    <div class="sidebar-section-label">&#128200; My Space</div>',
    '    <a href="dashboard.html" class="sidebar-item" data-page="dashboard"><div class="sidebar-item-icon" style="background:rgba(37,99,235,0.2)">ST</div><div class="sidebar-item-name">My Statistics</div></a>',
    '    <a href="strikes.html" class="sidebar-item" data-page="strikes"><div class="sidebar-item-icon" style="background:rgba(239,68,68,0.2)">WR</div><div class="sidebar-item-name">My Strikes</div></a>',
    '    <a href="../quiz.html" class="sidebar-item" data-page="activities"><div class="sidebar-item-icon" style="background:rgba(124,58,237,0.2)">QZ</div><div class="sidebar-item-name">Class Activities</div></a>',
    '  </div>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-section">',
    '    <div class="sidebar-section-label">&#128194; Documents</div>',
    '    <a href="lessons.html" class="sidebar-item" data-page="lessons"><div class="sidebar-item-icon" style="background:rgba(37,99,235,0.2)">&#128209;</div><div class="sidebar-item-name">Lesson Materials</div></a>',
    '    <a href="session.html" class="sidebar-item" data-page="session"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">&#128214;</div><div class="sidebar-item-name">Reading</div></a>',
    '    <a href="assignments.html" class="sidebar-item" data-page="assignments"><div class="sidebar-item-icon" style="background:rgba(59,130,246,0.2)">&#128203;</div><div class="sidebar-item-name">Assignments</div></a>',
    '    <a href="submission.html" class="sidebar-item" data-page="submission"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">&#128221;</div><div class="sidebar-item-name">Submission</div></a>',
    '  </div>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-section">',
    '    <div class="sidebar-section-label">&#128101; Student Data</div>',
    '    <a href="students.html" class="sidebar-item" data-page="students"><div class="sidebar-item-icon" style="background:rgba(236,72,153,0.2)">&#127891;</div><div class="sidebar-item-name">Student List</div></a>',
    '    <a href="attendance.html" class="sidebar-item" data-page="attendance"><div class="sidebar-item-icon" style="background:rgba(20,184,166,0.2)">&#9989;</div><div class="sidebar-item-name">Attendance</div></a>',
    '    <a href="scores.html" class="sidebar-item" data-page="scores"><div class="sidebar-item-icon" style="background:rgba(249,115,22,0.2)">&#127942;</div><div class="sidebar-item-name">Student Scores</div></a>',
    '    <a href="tasks.html" class="sidebar-item" data-page="tasks"><div class="sidebar-item-icon" style="background:rgba(16,185,129,0.2)">&#128204;</div><div class="sidebar-item-name">Task Status</div></a>',
    '    <a href="activeness.html" class="sidebar-item" data-page="activeness"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">&#9889;</div><div class="sidebar-item-name">Activeness</div></a>',
    '  </div>',
    '</nav>'
  ].join('\n');

  /* Keep shared sidebars consistent on secondary student pages. */
  var style = document.createElement('style');
  style.textContent =
    '.sidebar { display:flex !important; flex-direction:column !important; overflow-y:auto !important; }' +
    '#hamburgerBtn { width:92px !important; min-width:92px !important; height:42px !important; align-items:flex-start !important; padding:0 14px !important; border-radius:999px !important; position:absolute !important; }' +
    '#hamburgerBtn::after { content:"Menu"; position:absolute; right:14px; top:50%; transform:translateY(-50%); color:#fff; font:700 0.82rem "IBM Plex Sans", sans-serif; }' +
    '#hamburgerBtn.open::after { content:"Close"; }' +
    '#hamburgerBtn span { margin-left:0 !important; }';
  document.head.appendChild(style);

  document.addEventListener('DOMContentLoaded', function () {
    /* Skip if page already has its own sidebar (index.html) */
    if (document.getElementById('sidebar')) return;

    var wrap = document.createElement('div');
    wrap.innerHTML = HTML;
    while (wrap.firstChild) document.body.appendChild(wrap.firstChild);

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

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') window.closeSidebar();
  });

})();
