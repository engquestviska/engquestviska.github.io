/* Shared student app shell for Grade X and Grade XI pages. */
(function () {
  var grade = document.body && document.body.classList.contains('grade11-page') ? '11' : '';

  function detectGrade() {
    if (grade) return grade;
    if (location.pathname.indexOf('/grade11/') !== -1) return '11';
    return '10';
  }

  function icon(name) {
    return '<i data-lucide="' + name + '"></i>';
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

  function navItem(page, href, iconName, name, current) {
    var active = page === current ? ' class="active"' : '';
    return '<a' + active + ' href="' + href + '">' + icon(iconName) + '<span>' + name + '</span></a>';
  }

  function getStudentProfile(currentGrade) {
    var classId = localStorage.getItem('eq_student_class') || '';
    var studentNo = localStorage.getItem('eq_student_no') || '';
    var studentName = localStorage.getItem('eq_student_name') || '';
    var savedGrade = localStorage.getItem('eq_grade') || currentGrade;
    var classGrade = window.EQClasses ? window.EQClasses.gradeFor(classId) : savedGrade;
    var hasMatchingClass = classId && classGrade === currentGrade;
    return {
      classId: hasMatchingClass ? classId : '',
      studentNo: hasMatchingClass ? studentNo : '',
      studentName: hasMatchingClass ? studentName : ''
    };
  }

  function classDisplay(classId) {
    return window.EQClasses ? window.EQClasses.labelFor(classId) : classId;
  }

  function profileMarkup(currentGrade) {
    var profile = getStudentProfile(currentGrade);
    var classText = profile.classId ? classDisplay(profile.classId) : 'Choose your class';
    var detailText = profile.studentName && profile.studentNo
      ? profile.studentName + ' · No. ' + profile.studentNo
      : 'Login on the homepage';
    return [
      '<section class="student-profile-nav" aria-label="Current student profile">',
      '  <div class="sidebar-label">My Class</div>',
      '  <a href="index.html#profile" class="student-profile-link">',
      '    <span class="student-profile-icon">' + icon('id-card') + '</span>',
      '    <span><strong id="studentSidebarClass">' + classText + '</strong><small id="studentSidebarDetail">' + detailText + '</small></span>',
      '  </a>',
      '</section>'
    ].join('');
  }

  function shellMarkup() {
    var filePage = (location.pathname.split('/').pop() || 'index.html').replace('.html', '');
    var current = document.body.getAttribute('data-page') || (filePage === 'index' ? 'home' : filePage);
    var currentGrade = detectGrade();
    var nav = [
      navItem('home', 'index.html', 'house', 'Homepage', current),
      navItem('dashboard', 'dashboard.html', 'chart-no-axes-column-increasing', 'My Statistics', current),
      navItem('tasks', 'tasks.html', 'list-checks', 'Tasks', current),
      navItem('submission', 'submission.html', 'file-up', 'Submission', current),
      navItem('lessons', 'lessons.html', 'book-open', 'Materials', current),
      navItem('session', 'session.html', 'book-marked', 'Reading', current),
      navItem('attendance', 'attendance.html', 'calendar-check', 'Attendance', current),
      navItem('scores', 'scores.html', 'trophy', 'Scores', current),
      navItem('students', 'students.html', 'users-round', 'Classmates', current),
      navItem('activeness', 'activeness.html', 'zap', 'Participation', current),
      navItem('strikes', 'strikes.html', 'shield-alert', 'Strikes', current)
    ].join('');

    return [
      '<aside class="dashboard-sidebar student-app-sidebar" id="dashboardSidebar">',
      '  <a class="sidebar-brand" href="index.html"><span class="brand-mark">' + icon('graduation-cap') + '</span><span>English Quest</span></a>',
      '  <div class="sidebar-label">Grade ' + (currentGrade === '11' ? 'XI' : 'X') + ' Student</div>',
      '  <nav class="primary-nav" aria-label="Student navigation">' + nav + '</nav>',
      '  ' + profileMarkup(currentGrade),
      '  <div class="sidebar-footer">',
      '    <a href="../index.html">' + icon('layers') + '<span>Choose Grade</span></a>',
      '    <button type="button" onclick="studentShellLogout()">' + icon('log-out') + '<span>Logout / Change Name</span></button>',
      '  </div>',
      '</aside>',
      '<div class="mobile-backdrop" id="mobileBackdrop" onclick="closeSidebar()"></div>'
    ].join('');
  }

  function addMobileButton() {
    var topbar = document.querySelector('.topbar');
    if (!topbar || topbar.querySelector('.student-shell-mobile-button')) return;
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'student-shell-mobile-button';
    button.setAttribute('aria-label', 'Open navigation');
    button.innerHTML = icon('menu');
    button.onclick = window.toggleSidebar;
    topbar.insertBefore(button, topbar.firstChild);
  }

  window.toggleSidebar = function () {
    document.getElementById('dashboardSidebar').classList.add('open');
    document.getElementById('mobileBackdrop').classList.add('open');
  };

  window.closeSidebar = function () {
    document.getElementById('dashboardSidebar').classList.remove('open');
    document.getElementById('mobileBackdrop').classList.remove('open');
  };

  window.studentShellLogout = function () {
    ['eq_student_class', 'eq_student_no', 'eq_student_name'].forEach(function (key) {
      localStorage.removeItem(key);
    });
    location.href = 'index.html';
  };

  document.addEventListener('DOMContentLoaded', function () {
    document.body.classList.add('student-shell-enabled');
    if (!document.getElementById('dashboardSidebar')) {
      document.body.insertAdjacentHTML('afterbegin', shellMarkup());
    }
    addMobileButton();
    ensureIcons();
  });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') window.closeSidebar();
  });
})();
