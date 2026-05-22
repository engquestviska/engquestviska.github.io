(function () {
  var SECURITY_MAINTENANCE = true;
  if (!SECURITY_MAINTENANCE) return;

  var LOCKED_PAGES = {
    'submission.html': true,
    'attendance.html': true,
    'activeness.html': true,
    'strikes.html': true,
    'gallery.html': true,
    'summative.html': true,
    'quiz.html': true,
    'jeopardyquiz.html': true,
    'chapter5_guess_who.html': true
  };

  var page = (location.pathname.split('/').pop() || 'index.html').toLowerCase();
  if (!LOCKED_PAGES[page]) return;

  var MESSAGE_TITLE = 'English Quest is temporarily locked.';
  var MESSAGE_BODY = 'This page is temporarily closed while we secure the system. Read-only pages such as scores, task status, materials, ASAT package, and ASAT result remain available.';
  var MESSAGE_NOTE = 'Please wait for the official update in your class group before using the website again.';

  document.documentElement.className += ' security-maintenance-html';

  var style = document.createElement('style');
  style.textContent =
    'html.security-maintenance-html, html.security-maintenance-html body { overflow:hidden !important; }' +
    'html.security-maintenance-html body > :not(#securityMaintenanceScreen) { pointer-events:none !important; user-select:none !important; filter:blur(2px); }' +
    '.security-maintenance-screen { position:fixed; inset:0; z-index:2147483647; display:flex; align-items:center; justify-content:center; padding:28px; background:radial-gradient(circle at 20% 20%, rgba(245,158,11,0.22), transparent 34%), radial-gradient(circle at 78% 26%, rgba(236,72,153,0.18), transparent 30%), linear-gradient(135deg,#1E1040 0%,#4C1D95 48%,#111827 100%); color:#fff; text-align:center; font-family:"IBM Plex Sans",system-ui,sans-serif; }' +
    '.security-maintenance-card { width:min(680px,100%); border:1px solid rgba(255,255,255,0.18); border-radius:28px; padding:clamp(30px,5vw,54px); background:rgba(255,255,255,0.10); box-shadow:0 24px 80px rgba(0,0,0,0.34); backdrop-filter:blur(16px); }' +
    '.security-maintenance-kicker { display:inline-flex; align-items:center; justify-content:center; min-height:34px; padding:7px 14px; border-radius:999px; background:rgba(245,158,11,0.18); border:1px solid rgba(245,158,11,0.42); color:#FDE68A; font-weight:800; font-size:0.78rem; letter-spacing:0.08em; text-transform:uppercase; margin-bottom:18px; }' +
    '.security-maintenance-card h1 { font-family:"Playfair Display",Georgia,serif; font-size:clamp(2.3rem,7vw,4.8rem); line-height:0.95; margin:0 0 18px; color:#fff; }' +
    '.security-maintenance-card p { max-width:560px; margin:0 auto; color:rgba(255,255,255,0.84); font-size:clamp(1rem,2vw,1.15rem); line-height:1.65; }' +
    '.security-maintenance-note { margin-top:24px; padding:14px 16px; border-radius:16px; background:rgba(255,255,255,0.12); color:rgba(255,255,255,0.92); font-weight:700; line-height:1.45; }';

  function appendStyle() {
    (document.head || document.documentElement).appendChild(style);
  }

  function showLock() {
    if (document.getElementById('securityMaintenanceScreen')) return;

    var screen = document.createElement('main');
    screen.className = 'security-maintenance-screen';
    screen.id = 'securityMaintenanceScreen';
    screen.setAttribute('role', 'alert');
    screen.setAttribute('aria-live', 'assertive');
    screen.innerHTML =
      '<section class="security-maintenance-card" aria-labelledby="securityMaintenanceTitle">' +
      '<div class="security-maintenance-kicker">Security Maintenance</div>' +
      '<h1 id="securityMaintenanceTitle">' + MESSAGE_TITLE + '</h1>' +
      '<p>' + MESSAGE_BODY + '</p>' +
      '<div class="security-maintenance-note">' + MESSAGE_NOTE + '</div>' +
      '</section>';

    document.body.appendChild(screen);
  }

  appendStyle();

  if (document.body) {
    showLock();
  } else {
    document.addEventListener('DOMContentLoaded', showLock);
  }
})();
