/* Legacy root-page sidebar.
   Active Grade 10 pages use shared/js/sidebar.js after the folder restructure. */
(function () {
  var style = document.createElement('style');
  style.textContent =
    '#hamburgerBtn { width:92px !important; min-width:92px !important; height:42px !important; align-items:flex-start !important; padding:0 14px !important; border-radius:999px !important; position:absolute !important; }' +
    '#hamburgerBtn::after { content:"Menu"; position:absolute; right:14px; top:50%; transform:translateY(-50%); color:#fff; font:700 0.82rem "IBM Plex Sans", sans-serif; }' +
    '#hamburgerBtn.open::after { content:"Close"; }' +
    '#hamburgerBtn span { margin-left:0 !important; }';
  document.head.appendChild(style);

  var HTML = [
    '<div class="sidebar-overlay" id="sidebarOverlay" onclick="closeSidebar()"></div>',
    '<nav class="sidebar" id="sidebar" aria-label="Legacy page navigation">',
    '  <div class="sidebar-head">',
    '    <img class="sidebar-logo" src="logo.jpg" alt="English Quest Logo"/>',
    '    <div class="sidebar-head-text">',
    '      <div class="sidebar-head-title">English Quest</div>',
    '      <div class="sidebar-head-sub">Legacy activity pages</div>',
    '    </div>',
    '    <button class="sidebar-close" type="button" onclick="closeSidebar()" aria-label="Close menu">&#x2715;</button>',
    '  </div>',
    '  <a href="index.html" class="sidebar-home-btn">',
    '    <div>',
    '      <div class="sidebar-home-btn-text">&#127968; Landing</div>',
    '      <div class="sidebar-home-btn-sub">Choose a track</div>',
    '    </div>',
    '  </a>',
    '  <a href="grade10/" class="sidebar-home-btn">',
    '    <div>',
    '      <div class="sidebar-home-btn-text">G10 Grade 10</div>',
    '      <div class="sidebar-home-btn-sub">Student home</div>',
    '    </div>',
    '  </a>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-section">',
    '    <div class="sidebar-section-label">&#127919; Activities</div>',
    '    <a href="quiz.html" class="sidebar-item" data-page="quiz"><div class="sidebar-item-icon" style="background:rgba(37,99,235,0.2)">QZ</div><div class="sidebar-item-name">Activity Hub</div></a>',
    '    <a href="jeopardyquiz.html" class="sidebar-item" data-page="jeopardyquiz"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">J</div><div class="sidebar-item-name">Jeopardy Quiz</div></a>',
    '    <a href="chapter5_guess_who.html" class="sidebar-item" data-page="chapter5_guess_who"><div class="sidebar-item-icon" style="background:rgba(236,72,153,0.2)">GW</div><div class="sidebar-item-name">Guess Who</div><span class="sidebar-item-badge">Teacher</span></a>',
    '  </div>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-section">',
    '    <div class="sidebar-section-label">&#128196; Legacy Tools</div>',
    '    <a href="submission_status.html" class="sidebar-item" data-page="submission_status"><div class="sidebar-item-icon" style="background:rgba(16,185,129,0.2)">SS</div><div class="sidebar-item-name">Submission Status</div></a>',
    '    <a href="summative.html" class="sidebar-item" data-page="summative"><div class="sidebar-item-icon" style="background:rgba(245,158,11,0.2)">ST</div><div class="sidebar-item-name">Summative Topic</div></a>',
    '    <a href="gallery.html" class="sidebar-item" data-page="gallery"><div class="sidebar-item-icon" style="background:rgba(236,72,153,0.2)">GL</div><div class="sidebar-item-name">Gallery</div></a>',
    '  </div>',
    '  <div class="sidebar-divider"></div>',
    '  <div class="sidebar-teacher" id="sidebarTeacherState"></div>',
    '</nav>'
  ].join('\n');

  function hasTeacher() {
    return !!(localStorage.getItem('eq_tu') && localStorage.getItem('eq_tp'));
  }

  function setActiveItem() {
    var page = document.body.getAttribute('data-page') || '';
    document.querySelectorAll('.sidebar-item[data-page]').forEach(function (el) {
      el.classList.toggle('active', el.getAttribute('data-page') === page);
    });
  }

  function updateSidebarTeacher() {
    var box = document.getElementById('sidebarTeacherState');
    if (!box) return;
    if (hasTeacher()) {
      box.innerHTML =
        '<div class="sidebar-teacher-label">Teacher Session</div>' +
        '<div class="sidebar-teacher-logged">' +
          '<span style="cursor:default;color:#60A5FA;font-size:0.82rem;margin:0;">' +
            (localStorage.getItem('eq_tu') || 'Teacher') +
          '</span>' +
          '<a href="teacher/" style="margin-left:auto;color:rgba(255,255,255,0.65);font-size:0.75rem;text-decoration:none;">Open</a>' +
        '</div>';
      return;
    }
    box.innerHTML =
      '<div class="sidebar-teacher-label">Teacher</div>' +
      '<a href="teacher/" style="color:#fff;text-decoration:none;font-size:0.86rem;font-weight:700;">Open teacher dashboard</a>';
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (document.getElementById('sidebar')) return;
    var wrap = document.createElement('div');
    wrap.innerHTML = HTML;
    while (wrap.firstChild) document.body.appendChild(wrap.firstChild);
    setActiveItem();
    updateSidebarTeacher();
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
    setActiveItem();
    updateSidebarTeacher();
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

  window.updateSidebarTeacher = updateSidebarTeacher;

  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') window.closeSidebar();
  });
})();
