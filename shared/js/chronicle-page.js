/* EngQuest Chronicle page: after the student's 6-digit PIN, shows their semester stats with
   Watch your Chronicle (the story), Share, and links to Materials and Assignments.
   ?demo=1 / ?demo=longname / ?demo=11 shows sample numbers (add &story to start the story). */
(function () {
  var params = new URLSearchParams(location.search);
  var demo = params.get('demo');
  var $msg = document.getElementById('cq-msg');
  var $story = document.getElementById('cq-story');
  var $rec = document.getElementById('cq-rec');
  var D = window.EQChronicleData, S = window.EQChronicleStory;
  var esc = S.esc;
  var vm = null, story = null;
  var opensAt = ''; // set while Chronicle is not open yet: only the PIN can be made

  function homeUrl(p) { return (String(p && p.grade) === '11' ? 'grade11' : 'grade10') + '/index.html'; }
  function dayText(iso) {
    try { return new Date(iso).toLocaleDateString('en-US', { month: 'long', day: 'numeric', timeZone: 'Asia/Jakarta' }); }
    catch (e) { return 'soon'; }
  }
  // Before Chronicle opens: the PIN is made, nothing else to see yet.
  function allSet(p, note) {
    $story.hidden = true; $rec.hidden = true; $msg.hidden = false;
    document.body.classList.remove('records-mode');
    $msg.innerHTML = '<div class="cq-pin"><h1>You’re all set</h1><p>' + esc(note || 'Your PIN is ready.') + '</p>' +
      '<p>Your EngQuest Chronicle opens on <b>' + esc(dayText(opensAt)) + '</b>. Come back then and enter your PIN.</p>' +
      (inApp() ? '' : '<a class="cq-home" href="' + homeUrl(p) + '">Back to Home</a>') + '</div>';
  }

  function store(fn) { try { return fn(window.localStorage); } catch (e) { return null; } }

  function message(title, text, retry) {
    $story.hidden = true; $rec.hidden = true; $msg.hidden = false;
    document.body.classList.remove('records-mode');
    $msg.innerHTML = '<div><h1>' + esc(title) + '</h1><p>' + esc(text) + '</p>' +
      (retry ? '<button type="button" id="cq-retry">Try again</button>' : '') + '</div>';
    if (retry) document.getElementById('cq-retry').onclick = retry;
  }

  // The app hands its profile over; the website reads the one the student pages saved.
  function profile() {
    if (window.EngQuestApp && window.EngQuestApp.profile) {
      try { return JSON.parse(window.EngQuestApp.profile()); } catch (e) {}
    }
    var p = window.EQStudent ? window.EQStudent.read() : null;
    return p && p.hasProfile ? { grade: p.grade, classId: p.classId, studentNo: p.studentNo, name: p.studentName } : null;
  }

  function playStory() {
    if (story) story.destroy();
    $msg.hidden = true; $rec.hidden = true; $story.hidden = false;
    document.body.classList.remove('records-mode');
    story = S.render($story, vm);
    story.onDone(function () {
      story.destroy(); story = null;
      showRecords();
    });
    story.play();
  }

  function sec(label, body) { return '<div class="sec"><div class="k">' + label + '</div><div>' + body + '</div></div>'; }

  function showRecords() {
    var r = vm.records;
    $story.hidden = true; $msg.hidden = true; $rec.hidden = false;
    document.body.classList.add('records-mode');
    $rec.style.setProperty('--a', vm.accent);
    var units = r.units.filter(function (u) { return u.score !== '' && u.score != null; })
      .map(function (u) { return '<div><span class="k">' + esc(String(u.label || '').toUpperCase()) + '</span><span class="num" style="font-size:26px">' + esc(u.score) + '</span></div>'; });
    if (r.asat !== '' && r.asat != null) units.push('<div><span class="k">ASAT</span><span class="num" style="font-size:26px">' + esc(r.asat) + '</span></div>');
    var dots = r.days.map(function (d) { return '<i class="dot ' + (d === 's' ? 's' : d === 'a' ? 'a' : d === '' ? 'u' : '') + '"></i>'; }).join('');
    var words = r.words.map(function (w) { return '<span>' + esc(w) + '</span>'; }).join('');
    var shareLabel = window.EQChronicleCard ? window.EQChronicleCard.primaryLabel() : 'Share';
    var instagram = window.EQChronicleCard && window.EQChronicleCard.canInstagram();
    // In the app, Materials and Assignments are their own tabs; on the website they are links.
    var folder = vm.grade === '11' ? 'grade11' : 'grade10';
    var links = inApp() ? '' : '<a href="' + folder + '/index.html">Home</a><a href="' + folder + '/lessons.html">Materials</a><a href="' + folder + '/assignments.html">Assignments</a>';

    $rec.innerHTML = '<div class="cq-rec">' +
      '<div class="mast"><i>EngQuest Chronicle</i><span>' + esc(vm.card.label.toUpperCase()) + ' · ENDED</span></div>' +
      '<div class="cq-layout"><div class="cq-cardwrap"><div class="cq-card-slot"></div></div><div style="min-width:0">' +
      '<div class="cq-hero"><div style="min-width:0"><div class="k">' + esc(vm.classLabel + ' · ' + vm.gradeLabel).toUpperCase() + '</div>' +
      '<div class="num name fitname" data-max="' + (window.innerWidth >= 900 ? 96 : 64) + '">' + esc(vm.name) + '</div></div>' +
      '<div class="cq-ranks">' + (r.rank != null ? '<div><div class="k">CLASS RANK</div><div class="num ac" style="font-size:64px;line-height:.85"><span style="font-size:.45em;vertical-align:.8em">#</span>' + r.rank + '</div></div>' : '') + '</div></div>' +
      '<div class="cq-btns"><button type="button" class="btn pri cq-watch" data-act="watch">Watch your Chronicle</button></div>' +
      '<div class="cq-btns">' +
      (instagram ? '<button type="button" class="btn pri" data-act="instagram">Share to Instagram Story</button><button type="button" class="btn" data-act="share">More ways to share</button>'
        : '<button type="button" class="btn pri" data-act="share">' + esc(shareLabel) + '</button>') +
      '</div>' +
      '<p class="cq-note k" style="min-height:14px;margin:-8px 0 10px" aria-live="polite"></p>' +
      sec('FINAL', '<span class="num" style="font-size:34px">' + esc(r.final) + '</span> <span style="font-size:12px;font-weight:800;opacity:.6">/ 100</span>') +
      (r.gradeRank != null ? sec('GRADE', '<b>#' + r.gradeRank + '</b> of ' + r.gradeOf + ' in ' + esc(vm.gradeLabel)) : '') +
      (units.length ? sec('SCORES', '<div class="chs">' + units.join('') + '</div>') : '') +
      (r.meetings ? sec('PRESENT', '<div style="font-weight:800;margin-bottom:6px">' + r.present + ' of ' + r.meetings + ' days · ' + esc(r.percent) + '</div><div class="dots" style="gap:3px;max-width:340px">' + dots + '</div>') : '') +
      sec('LEVEL', '<b>Level ' + r.level + '</b> · ' + r.xp.toLocaleString('en-US') + ' XP · ' + r.tasksDone + ' tasks' + (r.best ? ' · best: ' + esc(r.best.label) + ', ' + esc(r.best.score) : '')) +
      (r.wordTotal ? sec('WORDS', '<div style="font-weight:800;margin-bottom:4px">' + r.wordTotal + ' collected' + (r.classTop5 ? ' · top 5 in class' : '') + '</div><div style="overflow:hidden"><div class="mqrow it" style="--t:40s;font-size:18px">' + words + words + '</div></div>') : '') +
      (links ? '<div class="sec"><div class="k">LEARN</div><div class="cq-links">' + links + '</div></div>' : '') +
      '</div></div></div>';

    var slot = $rec.querySelector('.cq-card-slot');
    slot.innerHTML = S.cardHtml(vm.card);
    S.fitNames($rec);
    $rec.querySelector('[data-act="watch"]').onclick = playStory;
    if (window.EQChronicleCard && !window.EngQuestApp) window.EQChronicleCard.prepare(vm);
    $rec.querySelectorAll('[data-act="share"],[data-act="instagram"]').forEach(function (b) {
      b.onclick = function () {
        if (!window.EQChronicleCard) return;
        var note = $rec.querySelector('.cq-note');
        note.textContent = 'Making your picture…';
        window.EQChronicleCard.share(vm, b.getAttribute('data-act')).then(function (said) { note.textContent = said || ''; })
          .catch(function () { note.textContent = 'Couldn’t make the picture. Try again.'; });
      };
    });
  }

  function start(bundle) {
    vm = D.build(bundle, { grade: bundle.student && bundle.student.grade });
    if (params.has('story')) playStory(); else showRecords();
  }

  function inApp() { return !!(window.EngQuestApp && window.EngQuestApp.profile); }

  // ---- PIN: made once, kept on this device, needed by the server for the stats ----
  function pinKey(p) { return 'eq_chronicle_pin_' + p.classId + '_' + p.studentNo; }
  function savedPin(p) { return store(function (ls) { return ls.getItem(pinKey(p)); }) || ''; }
  function savePin(p, pin) { store(function (ls) { if (pin) ls.setItem(pinKey(p), pin); else ls.removeItem(pinKey(p)); }); }

  function pinForm(p, mode, note) {
    $story.hidden = true; $rec.hidden = true; $msg.hidden = false;
    document.body.classList.remove('records-mode');
    var make = mode === 'make';
    $msg.innerHTML = '<form class="cq-pin" novalidate><h1>' + (make ? 'Make your PIN' : 'Enter your PIN') + '</h1>' +
      '<p>' + (make ? 'Pick 6 numbers only you know. You need them to open your Chronicle.' : 'Your Chronicle is locked with your 6-number PIN.') + '</p>' +
      '<label for="cq-pin1">' + (make ? 'New PIN' : 'PIN') + '</label>' +
      '<input id="cq-pin1" type="password" inputmode="numeric" pattern="[0-9]*" maxlength="6" autocomplete="off">' +
      (make ? '<label for="cq-pin2">Type it again</label><input id="cq-pin2" type="password" inputmode="numeric" pattern="[0-9]*" maxlength="6" autocomplete="off">' : '') +
      '<p class="cq-pin-err" role="alert">' + esc(note || '') + '</p>' +
      '<button type="submit">' + (make ? 'Save PIN' : 'Open') + '</button>' +
      '<p class="cq-pin-help">' + (make ? 'Don’t share it. ' : '') + 'Forgot it? Ask your teacher to reset it.</p></form>';
    var form = $msg.querySelector('form'), err = $msg.querySelector('.cq-pin-err');
    var a = document.getElementById('cq-pin1'), b = document.getElementById('cq-pin2');
    [a, b].forEach(function (el) {
      if (el) el.addEventListener('input', function () { el.value = el.value.replace(/\D/g, '').slice(0, 6); err.textContent = ''; });
    });
    a.focus();
    form.onsubmit = function (e) {
      e.preventDefault();
      if (!/^\d{6}$/.test(a.value)) { err.textContent = 'Enter 6 numbers.'; return; }
      if (make && a.value !== b.value) { err.textContent = 'The two PINs don’t match. Type them again.'; b.value = ''; b.focus(); return; }
      form.querySelector('button').disabled = true;
      if (!make) { savePin(p, a.value); fetchStats(p); return; }
      window.EQApi.get({ action: 'setChroniclePin', className: p.classId, studentNo: p.studentNo, pin: a.value })
        .then(function (r) {
          if (r && r.success) { savePin(p, a.value); if (opensAt) allSet(p, 'Your PIN is saved.'); else fetchStats(p); }
          else if (r && r.error === 'PIN already set' && opensAt) allSet(p, 'This Chronicle already has a PIN. If you didn’t make it, ask your teacher to reset it.');
          else if (r && r.error === 'PIN already set') pinForm(p, 'enter', 'This Chronicle already has a PIN. Enter it, or ask your teacher to reset it.');
          else pinForm(p, 'make', (r && r.error) || 'Couldn’t save the PIN. Try again.');
        })
        .catch(function () { pinForm(p, 'make', 'Can’t reach EngQuest right now. Try again.'); });
    };
  }

  function lockedText(retryAt) {
    var mins = Math.max(1, Math.ceil((retryAt - Date.now()) / 60000));
    return 'Too many wrong tries. Try again in ' + mins + ' minute' + (mins === 1 ? '' : 's') + ', or ask your teacher to reset your PIN.';
  }

  function fetchStats(p) {
    message('EngQuest Chronicle', 'Getting your semester ready…');
    window.EQApi.get({ action: 'getChronicle', className: p.classId, studentNo: p.studentNo, pin: savedPin(p) })
      .then(function (b) {
        if (b && b.error === 'Not open yet') { opensAt = b.openAt || opensAt; allSet(p); return; }
        if (b && b.error === 'PIN not set') { savePin(p, ''); pinForm(p, 'make'); return; }
        if (b && b.error === 'Wrong PIN') { savePin(p, ''); pinForm(p, 'enter', 'Wrong PIN. ' + b.triesLeft + ' tr' + (b.triesLeft === 1 ? 'y' : 'ies') + ' left.'); return; }
        if (b && b.error === 'Locked') { savePin(p, ''); pinForm(p, 'enter', lockedText(b.retryAt)); return; }
        if (!b || b.error) { message('We couldn’t find your record', 'Talk to your teacher.'); return; }
        start(b);
      })
      .catch(function () { message('Can’t reach EngQuest right now', 'Check your internet, then try again.', function () { fetchStats(p); }); });
  }

  function load() {
    if (demo) {
      start(demo === 'longname' ? D.SAMPLE_LONGNAME : demo === '11'
        ? Object.assign({}, D.SAMPLE, { student: Object.assign({}, D.SAMPLE.student, { classId: 'XIF8', grade: '11' }), gradeRank: Object.assign({}, D.SAMPLE.gradeRank, { classes: ['XIF7', 'XIF8', 'XIF9'] }) })
        : D.SAMPLE);
      return;
    }
    var p = profile();
    if (!p) {
      if (inApp()) message('EngQuest Chronicle', 'Sign in again from the app, then come back.');
      else location.replace('index.html'); // pick a grade and sign in first
      return;
    }
    message('EngQuest Chronicle', 'Getting your semester ready…');
    window.EQApi.get({ action: 'getChronicleStatus', className: p.classId })
      .then(function (st) {
        if (!st || !st.active) {
          // Tell the student pages' switch it is off, or they would send the student straight back here.
          try { sessionStorage.setItem('eq_chronicle_status', JSON.stringify({ cls: p.classId, active: false, at: Date.now() })); } catch (e) {}
          if (inApp()) message('EngQuest Chronicle', 'Your Chronicle opens when the semester ends.');
          else location.replace(homeUrl(p));
          return;
        }
        if (!st.open) opensAt = st.openAt || 'soon';
        if (savedPin(p) && !opensAt) { fetchStats(p); return; }
        return window.EQApi.get({ action: 'getChroniclePinState', className: p.classId, studentNo: p.studentNo })
          .then(function (s2) {
            if (!s2 || s2.error) { message('We couldn’t find your record', 'Talk to your teacher.'); return; }
            if (opensAt) { if (s2.hasPin) allSet(p); else pinForm(p, 'make'); return; }
            pinForm(p, s2.hasPin ? 'enter' : 'make');
          });
      })
      .catch(function () { message('Can’t reach EngQuest right now', 'Check your internet, then try again.', load); });
  }

  if (document.fonts && document.fonts.ready) document.fonts.ready.then(load); else load();
})();
