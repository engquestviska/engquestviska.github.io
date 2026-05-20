/* Legacy root-page sidebar.
   Active Grade 10 pages use shared/js/sidebar.js after the folder restructure. */
(function () {
  var style = document.createElement('style');
  style.textContent =
    '#hamburgerBtn { position:absolute; top:20px; right:20px; z-index:260; width:92px !important; min-width:92px !important; height:42px !important; display:flex; flex-direction:column; align-items:flex-start !important; justify-content:center; gap:5px; padding:0 14px !important; border:1.5px solid rgba(255,255,255,0.3); border-radius:999px !important; background:rgba(255,255,255,0.15); cursor:pointer; }' +
    '#hamburgerBtn::after { content:"Menu"; position:absolute; right:14px; top:50%; transform:translateY(-50%); color:#fff; font:700 0.82rem "IBM Plex Sans", sans-serif; }' +
    '#hamburgerBtn.open::after { content:"Close"; }' +
    '#hamburgerBtn span { display:block; width:18px; height:2px; margin-left:0 !important; border-radius:999px; background:#fff; transition:all 0.3s; }' +
    '#hamburgerBtn.open span:nth-child(1) { transform:translateY(7px) rotate(45deg); }' +
    '#hamburgerBtn.open span:nth-child(2) { opacity:0; }' +
    '#hamburgerBtn.open span:nth-child(3) { transform:translateY(-7px) rotate(-45deg); }' +
    '.sidebar-overlay { position:fixed; inset:0; background:rgba(15,10,40,0.6); backdrop-filter:blur(6px); z-index:300; opacity:0; pointer-events:none; transition:opacity 0.3s; }' +
    '.sidebar-overlay.open { opacity:1; pointer-events:all; }' +
    '.sidebar { position:fixed; top:0; right:0; height:100%; width:min(320px,85vw); display:flex; flex-direction:column; overflow-y:auto; background:#0F172A; z-index:301; transform:translateX(100%); transition:transform 0.35s cubic-bezier(0.34,1.1,0.64,1); }' +
    '.sidebar.open { transform:translateX(0); }' +
    '.sidebar-head { padding:24px 20px 16px; border-bottom:1px solid rgba(255,255,255,0.08); display:flex; align-items:center; gap:12px; }' +
    '.sidebar-logo { width:36px; height:36px; border-radius:10px; object-fit:cover; }' +
    '.sidebar-head-text { flex:1; }' +
    '.sidebar-head-title { font-family:"Playfair Display",serif; font-size:1rem; font-weight:800; color:#fff; }' +
    '.sidebar-head-sub { margin-top:1px; color:rgba(255,255,255,0.45); font-size:0.7rem; }' +
    '.sidebar-close { width:30px; height:30px; border:0; border-radius:50%; display:flex; align-items:center; justify-content:center; background:rgba(255,255,255,0.08); color:rgba(255,255,255,0.65); cursor:pointer; font-size:1rem; }' +
    '.sidebar-section { padding:14px 16px 4px; }' +
    '.sidebar-section-label { padding:0 4px; margin-bottom:6px; color:rgba(255,255,255,0.3); font:700 0.65rem "IBM Plex Mono", monospace; text-transform:uppercase; letter-spacing:0.12em; }' +
    '.sidebar-item { display:flex; align-items:center; gap:12px; padding:11px 12px; margin-bottom:2px; border-radius:12px; text-decoration:none; transition:background 0.15s; }' +
    '.sidebar-item:hover { background:rgba(255,255,255,0.08); }' +
    '.sidebar-item.active { padding-left:9px; border-left:3px solid #60A5FA; background:rgba(255,255,255,0.12); }' +
    '.sidebar-item-icon { width:32px; height:32px; border-radius:9px; display:flex; align-items:center; justify-content:center; color:#fff; font:800 0.72rem "IBM Plex Mono", monospace; flex-shrink:0; }' +
    '.sidebar-item-name { flex:1; color:#fff; font-size:0.88rem; font-weight:700; }' +
    '.sidebar-item-badge { padding:2px 7px; border-radius:999px; background:#F59E0B; color:#fff; font:800 0.6rem "IBM Plex Mono", monospace; }' +
    '.sidebar-divider { height:1px; margin:8px 16px; background:rgba(255,255,255,0.07); }' +
    '.sidebar-home-btn { display:flex; align-items:center; gap:10px; margin:12px 16px 4px; padding:11px 14px; border:1px solid rgba(255,255,255,0.1); border-radius:12px; background:rgba(255,255,255,0.06); text-decoration:none; }' +
    '.sidebar-home-btn:hover { background:rgba(255,255,255,0.1); }' +
    '.sidebar-home-btn-text { color:#fff; font-size:0.85rem; font-weight:800; }' +
    '.sidebar-home-btn-sub { color:rgba(255,255,255,0.4); font-size:0.7rem; }' +
    '.sidebar-teacher { margin:12px 16px 20px; padding:12px 14px; border:1px solid rgba(37,99,235,0.35); border-radius:14px; background:rgba(37,99,235,0.2); }' +
    '.sidebar-teacher-label { margin-bottom:8px; color:#60A5FA; font:700 0.68rem "IBM Plex Mono", monospace; text-transform:uppercase; letter-spacing:0.08em; }' +
    '.sidebar-teacher-logged { display:flex; align-items:center; gap:8px; color:#60A5FA; font-size:0.82rem; font-weight:700; }';
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

  function ensureMenuButton() {
    if (document.getElementById('hamburgerBtn')) return;
    var host = document.querySelector('.page-header') || document.querySelector('header') || document.body;
    var btn = document.createElement('button');
    btn.className = 'hamburger';
    btn.id = 'hamburgerBtn';
    btn.type = 'button';
    btn.setAttribute('aria-label', 'Menu');
    btn.onclick = window.toggleSidebar;
    btn.innerHTML = '<span></span><span></span><span></span>';
    host.appendChild(btn);
  }

  document.addEventListener('DOMContentLoaded', function () {
    ensureMenuButton();
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
