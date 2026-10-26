/* More: links to the teacher website pages this app doesn't rebuild. */
(function () {
  var LINKS = [
    { href: '../teacher/materials.html', label: 'Materials', icon: '<path d="M2 4h6a4 4 0 0 1 4 4v13a3 3 0 0 0-3-3H2zM22 4h-6a4 4 0 0 0-4 4v13a3 3 0 0 1 3-3h7z"/>' },
    { href: '../teacher/assignments.html', label: 'Assignments', icon: '<rect x="8" y="2" width="8" height="4" rx="1"/><path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2M9 12h6M9 16h6"/>' },
    { href: '../teacher/tasks.html', label: 'Task Status', icon: '<path d="m3 17 2 2 4-4M3 7l2 2 4-4M13 6h8M13 12h8M13 18h8"/>' },
    { href: '../teacher/announcements.html', label: 'Announcements', icon: '<path d="m3 11 18-5v12L3 14v-3zM11.6 16.8a3 3 0 1 1-5.8-1.6"/>' },
    { href: '../teacher/classes.html', label: 'Classes', icon: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.9M16 3.1a4 4 0 0 1 0 7.8"/>' },
    { href: '../teacher/vocabulary.html', label: 'Vocabulary', icon: '<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V2H6.5A2.5 2.5 0 0 0 4 4.5zM8 7h8"/>' },
    { href: '../teacher/flashcards.html', label: 'Flashcards', icon: '<rect x="2" y="6" width="16" height="14" rx="2"/><path d="M6 2h14a2 2 0 0 1 2 2v12"/>' },
    { href: '../teacher/strikes.html', label: 'Strikes', icon: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10zM12 8v4M12 16h.01"/>' },
    { href: '../teacher/settings.html', label: 'Settings', icon: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-2.9 1.2V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-2.9-1.2l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0-1.2-2.9H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.2-2.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 2.9-1.2V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 2.9 1.2l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0 1.2 2.9H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>' }
  ];

  function icon(paths) {
    return '<span class="ic"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + paths + '</svg></span>';
  }

  EQT.registerScreen('more', {
    title: 'More',
    render: function (el, ctx) {
      var who = ctx.session.get();
      ctx.setSubtitle('Logged in as ' + (who ? who.username : 'teacher'));
      el.innerHTML =
        '<div class="more-head">Teacher website</div>' +
        '<div class="more-list">' + LINKS.map(function (l) {
          return '<a class="more-item" href="' + l.href + '">' + icon(l.icon) + l.label + '<span class="chev">›</span></a>';
        }).join('') +
        '<a class="more-item" href="../teacher/">' + icon('<rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/>') + 'Full teacher website<span class="chev">›</span></a>' +
        '</div>' +
        '<div class="more-head">Account</div>' +
        '<button class="more-item logout" type="button" id="logoutBtn">' + icon('<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>') + 'Log out</button>' +
        '<p class="more-foot">EngQuest Teacher · pilot</p>';
      // Hand the app's login to the teacher website (shared/js/auth.js reads these
      // sessionStorage keys), so its pages open without asking for the password again.
      el.querySelector('.more-list').addEventListener('click', function (e) {
        if (!e.target.closest('a') || !who) return;
        try {
          sessionStorage.setItem('eq_tu', who.username);
          sessionStorage.setItem('eq_tp', who.password);
          sessionStorage.setItem('eq_teacher_verified_at', String(Date.now()));
          sessionStorage.setItem('eq_teacher_verified_user', who.username);
        } catch (err) { /* the website will ask for the login instead */ }
      });
      el.querySelector('#logoutBtn').onclick = function () {
        ctx.confirm('Log out?', 'You will need your password to open the app again.', 'Log out', true).then(function (ok) {
          if (ok) EQT.logout();
        });
      };
    }
  });
})();
