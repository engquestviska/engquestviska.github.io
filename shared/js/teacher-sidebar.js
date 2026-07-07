/* Shared teacher app shell for manager pages. */
(function () {
  var NAV_ITEMS = [
    { page: 'teacher-home', href: 'index.html', icon: 'house', name: 'Homepage' },
    { page: 'classes', href: 'classes.html', icon: 'users-round', name: 'Classes' },
    { page: 'scores', href: 'scores.html', icon: 'user-round', name: 'Students & Scores' },
    { page: 'materials', href: 'materials.html', icon: 'book-open', name: 'Materials' },
    { page: 'assignments', href: 'assignments.html', icon: 'clipboard-list', name: 'Assignments' },
    { page: 'tasks', href: 'tasks.html', icon: 'list-checks', name: 'Task Status' },
    { page: 'submissions', href: 'submissions.html', icon: 'inbox', name: 'Submissions' },
    { page: 'attendance', href: 'attendance.html', icon: 'calendar-check', name: 'Attendance' },
    { page: 'activeness', href: 'activeness.html', icon: 'zap', name: 'Level / XP' },
    { page: 'announcements', href: 'announcements.html', icon: 'megaphone', name: 'Announcements' },
    { page: 'strikes', href: 'strikes.html', icon: 'shield-alert', name: 'Strikes' },
    { page: 'settings', href: 'settings.html', icon: 'settings', name: 'Settings' }
  ];

  var CLASSES = (window.EQClasses ? window.EQClasses.all : [
    { id: 'XE1', shortLabel: 'XE1', grade: '10' },
    { id: 'XE2', shortLabel: 'XE2', grade: '10' },
    { id: 'XE3', shortLabel: 'XE3', grade: '10' },
    { id: 'XE4', shortLabel: 'XE4', grade: '10' },
    { id: 'XE5', shortLabel: 'XE5', grade: '10' },
    { id: 'XIF7', shortLabel: 'XIF7', grade: '11' },
    { id: 'XIF8', shortLabel: 'XIF8', grade: '11' },
    { id: 'XIF9', shortLabel: 'XIF9', grade: '11' }
  ]).map(function(item) {
    return {
      id: item.id,
      label: item.shortLabel || item.id,
      grade: item.grade,
      href: 'classes.html?class=' + encodeURIComponent(item.id)
    };
  });

  function ensureStyles() {
    if (!document.querySelector('link[href*="shared/css/app-shell.css"]')) {
      var link = document.createElement('link');
      link.rel = 'stylesheet';
      link.href = '../shared/css/app-shell.css';
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

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function(ch) {
      return {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
      }[ch];
    });
  }

  function classesForGrade(grade) {
    var gradeId = String(grade || '10');
    if (window.EQClasses && typeof window.EQClasses.listForGrade === 'function') {
      return window.EQClasses.listForGrade(gradeId);
    }
    return CLASSES.filter(function(item) {
      return String(item.grade) === gradeId;
    });
  }

  function hasClass(classes, classId) {
    return classes.some(function(item) {
      return item.id === classId;
    });
  }

  function populateClassSelect(target, options) {
    var select = typeof target === 'string' ? document.getElementById(target) : target;
    if (!select) return '';

    var settings = options || {};
    var grade = String(settings.grade || select.getAttribute('data-grade') || '10');
    var classes = classesForGrade(grade);
    var requested = settings.selectedId || selectedClassFromQuery() || select.value || select.getAttribute('data-default-class') || '';
    var selected = hasClass(classes, requested) ? requested : (classes[0] ? classes[0].id : '');

    select.setAttribute('data-eq-class-select', '');
    select.setAttribute('data-grade', grade);
    select.innerHTML = classes.map(function(item) {
      var label = item.label || item.shortLabel || item.id;
      var isSelected = item.id === selected ? ' selected' : '';
      return '<option value="' + escapeHtml(item.id) + '"' + isSelected + '>' + escapeHtml(label) + '</option>';
    }).join('');

    if (selected) select.value = selected;
    return select.value;
  }

  function navMarkup() {
    var current = document.body.getAttribute('data-page') || currentPageFromPath();
    return NAV_ITEMS.map(function (item) {
      var active = item.page === current ? ' class="active"' : '';
      return '<a' + active + ' href="' + item.href + '">' + icon(item.icon) + '<span>' + item.name + '</span></a>';
    }).join('');
  }

  function classMarkup() {
    return CLASSES.map(function (item) {
      var currentClass = selectedClassFromQuery();
      var active = item.id === currentClass ? ' class="active"' : '';
      return '<a' + active + ' href="' + item.href + '">' + icon('graduation-cap') + '<span>' + item.label + '</span></a>';
    }).join('');
  }

  function currentPageFromPath() {
    var file = (location.pathname.split('/').pop() || 'index.html').replace('.html', '');
    if (!file || file === 'index') return 'teacher-home';
    return file;
  }

  function selectedClassFromQuery() {
    try {
      return new URLSearchParams(location.search).get('class') || '';
    } catch (error) {
      return '';
    }
  }

  function applyClassQueryToSelect() {
    var selected = selectedClassFromQuery();
    var select = document.getElementById('classSelect');
    if (!select) return;
    if (select.hasAttribute('data-eq-class-select') || select.options.length === 0) {
      var currentValue = select.value;
      var nextValue = populateClassSelect(select, { selectedId: selected || select.value });
      if (selected && nextValue === selected && currentValue !== selected) {
        select.dispatchEvent(new Event('change', { bubbles: true }));
      }
      return;
    }
    if (!selected) return;
    var hasOption = Array.prototype.some.call(select.options, function(option) {
      return option.value === selected;
    });
    if (!hasOption || select.value === selected) return;
    select.value = selected;
    select.dispatchEvent(new Event('change', { bubbles: true }));
  }

  function shellMarkup() {
    return [
      '<aside class="dashboard-sidebar teacher-manager-sidebar" id="dashboardSidebar">',
      '  <a class="sidebar-brand" href="index.html"><span class="brand-mark">' + icon('graduation-cap') + '</span><span>English Quest</span></a>',
      '  <nav class="primary-nav" aria-label="Teacher navigation">' + navMarkup() + '</nav>',
      '  <section class="class-nav"><div class="sidebar-label">My Classes <span>(8)</span></div>' + classMarkup() + '</section>',
      '  <div class="sidebar-footer">',
      '    <a href="../grade10/">' + icon('monitor') + '<span>Grade 10 Student Site</span></a>',
      '    <a href="../grade11/">' + icon('monitor') + '<span>Grade 11 Student Site</span></a>',
      '    <a href="../index.html">' + icon('layers') + '<span>Choose Track</span></a>',
      '    <button type="button" onclick="teacherSidebarLogout()">' + icon('log-out') + '<span>Logout</span></button>',
      '  </div>',
      '</aside>',
      '<div class="mobile-backdrop" id="mobileBackdrop" onclick="closeTeacherShell()"></div>'
    ].join('');
  }

  function addMobileButton() {
    var topbar = document.querySelector('.teacher-tool-page header .topbar');
    if (!topbar || topbar.querySelector('.teacher-shell-mobile-button, .mobile-menu')) return;
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'teacher-shell-mobile-button';
    button.setAttribute('aria-label', 'Open navigation');
    button.innerHTML = icon('menu');
    button.onclick = window.openTeacherShell;
    topbar.insertBefore(button, topbar.firstChild);
  }

  function bindMobileControls() {
    var button = document.querySelector('.mobile-menu, .teacher-shell-mobile-button');
    var backdrop = document.getElementById('mobileBackdrop');
    if (button && !button.dataset.shellBound) {
      button.removeAttribute('onclick');
      button.addEventListener('click', window.openTeacherShell);
      button.dataset.shellBound = 'true';
    }
    if (backdrop && !backdrop.dataset.shellBound) {
      backdrop.removeAttribute('onclick');
      backdrop.addEventListener('click', window.closeTeacherShell);
      backdrop.dataset.shellBound = 'true';
    }
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

  window.EQTeacherShell = Object.assign(window.EQTeacherShell || {}, {
    populateClassSelect: populateClassSelect
  });

  function initTeacherShell() {
    ensureStyles();
    document.body.classList.add('teacher-shell-enabled');
    if (!document.getElementById('dashboardSidebar')) {
      document.body.insertAdjacentHTML('afterbegin', shellMarkup());
    }
    addMobileButton();
    bindMobileControls();
    applyClassQueryToSelect();
    ensureIcons();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initTeacherShell);
  } else {
    initTeacherShell();
  }

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') window.closeTeacherShell();
  });
})();
