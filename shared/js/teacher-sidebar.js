/* teacher-sidebar.js - shared navigation for teacher dashboard and manager pages */
(function () {
  var ITEMS = [
    { page: 'teacher-home', href: 'index.html', code: 'DB', name: 'Dashboard', section: 'Control Room' },
    { page: 'materials', href: 'materials.html', code: 'MT', name: 'Materials', section: 'Grade 10 Managers' },
    { page: 'assignments', href: 'assignments.html', code: 'AS', name: 'Assignments', section: 'Grade 10 Managers' },
    { page: 'submissions', href: 'submissions.html', code: 'SB', name: 'Submissions', section: 'Grade 10 Managers' },
    { page: 'attendance', href: 'attendance.html', code: 'AT', name: 'Attendance', section: 'Grade 10 Managers' },
    { page: 'activeness', href: 'activeness.html', code: 'AC', name: 'Activeness', section: 'Grade 10 Managers' },
    { page: 'announcements', href: 'announcements.html', code: 'AN', name: 'Announcements', section: 'Grade 10 Managers' },
    { page: 'scores', href: 'scores.html', code: 'SC', name: 'Scores', section: 'Grade 10 Managers' },
    { page: 'tasks', href: 'tasks.html', code: 'TS', name: 'Tasks', section: 'Grade 10 Managers' },
    { page: 'strikes', href: 'strikes.html', code: 'ST', name: 'Strikes', section: 'Grade 10 Managers' }
  ];

  var style = document.createElement('style');
  style.textContent =
    '#hamburgerBtn.teacher-menu-button { position:relative; z-index:260; width:96px; min-width:96px; height:42px; display:flex; flex-direction:column; align-items:flex-start; justify-content:center; gap:5px; padding:0 14px; border:1.5px solid rgba(255,255,255,0.35); border-radius:999px; background:rgba(255,255,255,0.12); cursor:pointer; }' +
    '#hamburgerBtn.teacher-menu-button::after { content:"Menu"; position:absolute; right:14px; top:50%; transform:translateY(-50%); color:#fff; font:800 0.82rem "IBM Plex Sans", sans-serif; }' +
    '#hamburgerBtn.teacher-menu-button.open::after { content:"Close"; }' +
    '#hamburgerBtn.teacher-menu-button span { display:block; width:18px; height:2px; border-radius:999px; background:#fff; transition:all 0.3s; }' +
    '#hamburgerBtn.teacher-menu-button.open span:nth-child(1) { transform:translateY(7px) rotate(45deg); }' +
    '#hamburgerBtn.teacher-menu-button.open span:nth-child(2) { opacity:0; }' +
    '#hamburgerBtn.teacher-menu-button.open span:nth-child(3) { transform:translateY(-7px) rotate(-45deg); }' +
    '.teacher-sidebar .sidebar-item-icon { background:rgba(37,99,235,0.22); color:#bfdbfe; }' +
    '.teacher-sidebar .sidebar-item.active .sidebar-item-icon { background:#2563eb; color:#fff; }' +
    '.teacher-sidebar-quick { display:grid; gap:8px; margin:12px 16px 20px; }' +
    '.teacher-sidebar-quick a, .teacher-sidebar-quick button { min-height:40px; border:1px solid rgba(255,255,255,0.1); border-radius:12px; background:rgba(255,255,255,0.06); color:#fff; text-decoration:none; font:inherit; font-size:0.82rem; font-weight:800; cursor:pointer; display:flex; align-items:center; justify-content:center; }' +
    '.teacher-sidebar-quick .danger { color:#fecaca; }' +
    '@media (max-width:760px) { #hamburgerBtn.teacher-menu-button { width:92px; min-width:92px; } }';
  document.head.appendChild(style);

  function sectionMarkup(sectionName, items) {
    return [
      '<div class="sidebar-section">',
      '  <div class="sidebar-section-label">' + sectionName + '</div>',
      items.map(function (item) {
        return '<a href="' + item.href + '" class="sidebar-item" data-page="' + item.page + '">' +
          '<div class="sidebar-item-icon">' + item.code + '</div>' +
          '<div class="sidebar-item-name">' + item.name + '</div>' +
        '</a>';
      }).join(''),
      '</div>'
    ].join('');
  }

  function buildSidebar() {
    var sections = [];
    var grouped = {};
    ITEMS.forEach(function (item) {
      grouped[item.section] = grouped[item.section] || [];
      grouped[item.section].push(item);
    });
    Object.keys(grouped).forEach(function (section) {
      sections.push(sectionMarkup(section, grouped[section]));
    });

    return [
      '<div class="sidebar-overlay" id="sidebarOverlay" onclick="closeSidebar()"></div>',
      '<nav class="sidebar teacher-sidebar" id="sidebar">',
      '  <div class="sidebar-head">',
      '    <img class="sidebar-logo" src="../logo.jpg" alt="Logo"/>',
      '    <div class="sidebar-head-text">',
      '      <div class="sidebar-head-title">English Quest</div>',
      '      <div class="sidebar-head-sub">Teacher Control Room</div>',
      '    </div>',
      '    <button class="sidebar-close" type="button" onclick="closeSidebar()">&#x2715;</button>',
      '  </div>',
      '  <a href="../grade10/" class="sidebar-home-btn">',
      '    <div>',
      '      <div class="sidebar-home-btn-text">Grade 10 Student View</div>',
      '      <div class="sidebar-home-btn-sub">Open the student track</div>',
      '    </div>',
      '  </a>',
      '  <a href="../grade11/" class="sidebar-home-btn">',
      '    <div>',
      '      <div class="sidebar-home-btn-text">Grade 11 Preview</div>',
      '      <div class="sidebar-home-btn-sub">Placeholder track</div>',
      '    </div>',
      '  </a>',
      '  <div class="sidebar-divider"></div>',
      sections.join('<div class="sidebar-divider"></div>'),
      '  <div class="sidebar-divider"></div>',
      '  <div class="teacher-sidebar-quick">',
      '    <a href="../index.html">Landing Page</a>',
      '    <button class="danger" type="button" onclick="teacherSidebarLogout()">Logout</button>',
      '  </div>',
      '</nav>'
    ].join('');
  }

  function ensureMenuButton() {
    if (document.getElementById('hamburgerBtn')) return;
    var host = document.querySelector('.nav-actions');
    if (!host) host = document.querySelector('.topbar') || document.querySelector('.nav') || document.querySelector('header') || document.body;
    var btn = document.createElement('button');
    btn.className = 'teacher-menu-button';
    btn.id = 'hamburgerBtn';
    btn.type = 'button';
    btn.setAttribute('aria-label', 'Menu');
    btn.onclick = window.toggleSidebar;
    btn.innerHTML = '<span></span><span></span><span></span>';
    host.appendChild(btn);
  }

  function setActiveItem() {
    var page = document.body.getAttribute('data-page') || '';
    document.querySelectorAll('.sidebar-item[data-page]').forEach(function (el) {
      el.classList.toggle('active', el.getAttribute('data-page') === page);
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    ensureMenuButton();
    if (!document.getElementById('sidebar')) {
      var wrap = document.createElement('div');
      wrap.innerHTML = buildSidebar();
      while (wrap.firstChild) document.body.appendChild(wrap.firstChild);
    }
    setActiveItem();
  });

  window.toggleSidebar = function () {
    var sidebar = document.getElementById('sidebar');
    var overlay = document.getElementById('sidebarOverlay');
    var button = document.getElementById('hamburgerBtn');
    if (!sidebar) return;
    var open = sidebar.classList.toggle('open');
    if (overlay) overlay.classList.toggle('open', open);
    if (button) button.classList.toggle('open', open);
    document.body.style.overflow = open ? 'hidden' : '';
    setActiveItem();
  };

  window.closeSidebar = function () {
    var sidebar = document.getElementById('sidebar');
    var overlay = document.getElementById('sidebarOverlay');
    var button = document.getElementById('hamburgerBtn');
    if (sidebar) sidebar.classList.remove('open');
    if (overlay) overlay.classList.remove('open');
    if (button) button.classList.remove('open');
    document.body.style.overflow = '';
  };

  window.teacherSidebarLogout = function () {
    if (window.EQAuth) EQAuth.logoutTeacher('index.html');
    else window.location.href = 'index.html';
  };

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') window.closeSidebar();
  });
})();
