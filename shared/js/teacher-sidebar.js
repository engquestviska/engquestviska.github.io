/* Shared teacher app shell for manager pages. */
(function () {
  var NAV_ITEMS = [
    { page: 'teacher-home', href: 'index.html', icon: 'house', name: 'Homepage' },
    { page: 'scores', href: 'scores.html', icon: 'user-round', name: 'Students & Scores' },
    { page: 'materials', href: 'materials.html', icon: 'book-open', name: 'Materials' },
    { page: 'assignments', href: 'assignments.html', icon: 'clipboard-list', name: 'Assignments' },
    { page: 'tasks', href: 'tasks.html', icon: 'list-checks', name: 'Task Status' },
    { page: 'submissions', href: 'submissions.html', icon: 'inbox', name: 'Submissions' },
    { page: 'attendance', href: 'attendance.html', icon: 'calendar-check', name: 'Attendance' },
    { page: 'activeness', href: 'activeness.html', icon: 'chart-pie', name: 'Activeness' },
    { page: 'announcements', href: 'announcements.html', icon: 'megaphone', name: 'Announcements' },
    { page: 'strikes', href: 'strikes.html', icon: 'shield-alert', name: 'Strikes' }
  ];

  var CLASSES = [
    { id: 'XE1', href: 'scores.html?class=XE1' },
    { id: 'XE2', href: 'scores.html?class=XE2' },
    { id: 'XE3', href: 'scores.html?class=XE3' },
    { id: 'XE4', href: 'scores.html?class=XE4' },
    { id: 'XE5', href: 'scores.html?class=XE5' },
    { id: 'XIF7', href: '../grade11/' },
    { id: 'XIF8', href: '../grade11/' },
    { id: 'XIF9', href: '../grade11/' }
  ];

  function ensureStyles() {
    if (!document.querySelector('link[href="../shared/css/teacher-dashboard.css"]')) {
      var link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = '../shared/css/teacher-dashboard.css';
      document.head.appendChild(link);
    }
  }

  function ensureIcons() {
    if (window.lucide) {
      window.lucide.createIcons();
      return;
    }
    var script = document.createElement('script');
    script.src = 'https://unpkg.com/lucide@0.468.0/dist/umd/lucide.min.js';
    script.onload = function () { window.lucide.createIcons(); };
    document.head.appendChild(script);
  }

  function icon(name) {
    return '<i data-lucide="' + name + '"></i>';
  }

  function navMarkup() {
    var current = document.body.getAttribute('data-page') || '';
    return NAV_ITEMS.map(function (item) {
      var active = item.page === current ? ' class="active"' : '';
      return '<a' + active + ' href="' + item.href + '">' + icon(item.icon) + '<span>' + item.name + '</span></a>';
    }).join('');
  }

  function classMarkup() {
    return CLASSES.map(function (item) {
      return '<a href="' + item.href + '">' + icon('graduation-cap') + '<span>' + item.id + '</span></a>';
    }).join('');
  }

  function shellMarkup() {
    return [
      '<aside class="dashboard-sidebar teacher-manager-sidebar" id="dashboardSidebar">',
      '  <a class="sidebar-brand" href="index.html"><span class="brand-mark">' + icon('graduation-cap') + '</span><span>English Quest</span></a>',
      '  <nav class="primary-nav" aria-label="Teacher navigation">' + navMarkup() + '</nav>',
      '  <section class="class-nav"><div class="sidebar-label">My Classes <span>(8)</span></div>' + classMarkup() + '</section>',
      '  <div class="sidebar-footer">',
      '    <a href="../index.html">' + icon('layers') + '<span>Choose Track</span></a>',
      '    <button type="button" onclick="teacherSidebarLogout()">' + icon('log-out') + '<span>Logout</span></button>',
      '  </div>',
      '</aside>',
      '<div class="mobile-backdrop" id="mobileBackdrop" onclick="closeTeacherShell()"></div>'
    ].join('');
  }

  function addMobileButton() {
    var topbar = document.querySelector('.teacher-tool-page header .topbar');
    if (!topbar || topbar.querySelector('.teacher-shell-mobile-button')) return;
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'teacher-shell-mobile-button';
    button.setAttribute('aria-label', 'Open navigation');
    button.innerHTML = icon('menu');
    button.onclick = window.openTeacherShell;
    topbar.insertBefore(button, topbar.firstChild);
  }

  window.openTeacherShell = function () {
    document.getElementById('dashboardSidebar').classList.add('open');
    document.getElementById('mobileBackdrop').classList.add('open');
  };

  window.closeTeacherShell = function () {
    document.getElementById('dashboardSidebar').classList.remove('open');
    document.getElementById('mobileBackdrop').classList.remove('open');
  };

  window.toggleSidebar = window.openTeacherShell;
  window.closeSidebar = window.closeTeacherShell;

  window.teacherSidebarLogout = function () {
    if (window.EQAuth) EQAuth.logoutTeacher('index.html');
    else window.location.href = 'index.html';
  };

  document.addEventListener('DOMContentLoaded', function () {
    ensureStyles();
    document.body.classList.add('teacher-shell-enabled');
    if (!document.getElementById('dashboardSidebar')) {
      document.body.insertAdjacentHTML('afterbegin', shellMarkup());
    }
    addMobileButton();
    ensureIcons();
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') window.closeTeacherShell();
  });
})();
