/* EngQuest Chronicle page: loads the student's semester, plays the story the first time,
   then shows the records page (Share / Replay). ?demo=1 or ?demo=longname shows sample numbers. */
(function () {
  var SEEN = 'eq_chronicle_seen';
  var params = new URLSearchParams(location.search);
  var demo = params.get('demo');
  var $msg = document.getElementById('cq-msg');
  var $story = document.getElementById('cq-story');
  var $rec = document.getElementById('cq-rec');
  var D = window.EQChronicleData, S = window.EQChronicleStory;
  var esc = S.esc;
  var vm = null, story = null;

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
      store(function (ls) { ls.setItem(SEEN, '1'); });
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
      .map(function (u) { return '<div><span class="k">' + esc(u.label.toUpperCase()) + '</span><span class="num" style="font-size:26px">' + esc(u.score) + '</span></div>'; });
    if (r.asat !== '' && r.asat != null) units.push('<div><span class="k">ASAT</span><span class="num" style="font-size:26px">' + esc(r.asat) + '</span></div>');
    var dots = r.days.map(function (d) { return '<i class="dot ' + (d === 's' ? 's' : d === 'a' ? 'a' : d === '' ? 'u' : '') + '"></i>'; }).join('');
    var words = r.words.map(function (w) { return '<span>' + esc(w) + '</span>'; }).join('');
    var shareLabel = window.EQChronicleCard ? window.EQChronicleCard.primaryLabel() : 'Share';
    var instagram = window.EQChronicleCard && window.EQChronicleCard.canInstagram();

    $rec.innerHTML = '<div class="cq-rec">' +
      '<div class="mast"><i>EngQuest Chronicle</i><span>' + esc(vm.card.label.toUpperCase()) + ' · ENDED</span></div>' +
      '<div class="cq-layout"><div class="cq-cardwrap"><div class="cq-card-slot"></div></div><div style="min-width:0">' +
      '<div class="cq-hero"><div style="min-width:0"><div class="k">' + esc(vm.classLabel + ' · ' + vm.gradeLabel).toUpperCase() + '</div>' +
      '<div class="num name fitname" data-max="' + (window.innerWidth >= 900 ? 96 : 64) + '">' + esc(vm.name) + '</div></div>' +
      '<div class="cq-ranks">' + (r.rank != null ? '<div><div class="k">CLASS RANK</div><div class="num ac" style="font-size:64px;line-height:.85"><span style="font-size:.45em;vertical-align:.8em">#</span>' + r.rank + '</div></div>' : '') + '</div></div>' +
      '<div class="cq-btns">' +
      (instagram ? '<button type="button" class="btn pri" data-act="instagram">Share to Instagram Story</button><button type="button" class="btn" data-act="share">More ways to share</button>'
        : '<button type="button" class="btn pri" data-act="share">' + esc(shareLabel) + '</button>') +
      '<button type="button" class="btn" data-act="replay" style="flex:0 1 110px">Replay</button></div>' +
      '<p class="cq-note k" style="min-height:14px;margin:-8px 0 10px" aria-live="polite"></p>' +
      sec('FINAL', '<span class="num" style="font-size:34px">' + esc(r.final) + '</span> <span style="font-size:12px;font-weight:800;opacity:.6">/ 100</span>') +
      (r.gradeRank != null ? sec('GRADE', '<b>#' + r.gradeRank + '</b> of ' + r.gradeOf + ' in ' + esc(vm.gradeLabel)) : '') +
      (units.length ? sec('SCORES', '<div class="chs">' + units.join('') + '</div>') : '') +
      (r.meetings ? sec('PRESENT', '<div style="font-weight:800;margin-bottom:6px">' + r.present + ' of ' + r.meetings + ' days · ' + esc(r.percent) + '</div><div class="dots" style="gap:3px;max-width:340px">' + dots + '</div>') : '') +
      sec('LEVEL', '<b>Level ' + r.level + '</b> · ' + r.xp.toLocaleString('en-US') + ' XP · ' + r.tasksDone + ' tasks' + (r.best ? ' · best: ' + esc(r.best.label) + ', ' + esc(r.best.score) : '')) +
      (r.wordTotal ? sec('WORDS', '<div style="font-weight:800;margin-bottom:4px">' + r.wordTotal + ' collected' + (r.classTop5 ? ' · top 5 in class' : '') + '</div><div style="overflow:hidden"><div class="mqrow it" style="--t:40s;font-size:18px">' + words + words + '</div></div>') : '') +
      '</div></div></div>';

    var slot = $rec.querySelector('.cq-card-slot');
    slot.innerHTML = S.cardHtml(vm.card);
    S.fitNames($rec);
    $rec.querySelector('[data-act="replay"]').onclick = playStory;
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
    var seen = store(function (ls) { return ls.getItem(SEEN); });
    if (seen && !params.has('story')) showRecords(); else playStory();
  }

  function load() {
    if (demo) {
      start(demo === 'longname' ? D.SAMPLE_LONGNAME : demo === '11'
        ? Object.assign({}, D.SAMPLE, { student: Object.assign({}, D.SAMPLE.student, { classId: 'XIF8', grade: '11' }), gradeRank: Object.assign({}, D.SAMPLE.gradeRank, { classes: ['XIF7', 'XIF8', 'XIF9'] }) })
        : D.SAMPLE);
      return;
    }
    var p = profile();
    if (!p) { location.replace('index.html'); return; }
    message('EngQuest Chronicle', 'Getting your semester ready…');
    window.EQApi.get({ action: 'getChronicle', className: p.classId, studentNo: p.studentNo })
      .then(function (b) {
        if (!b || b.error) { message('We couldn’t find your record', 'Talk to your teacher.'); return; }
        if (!b.active) { location.replace((String(p.grade) === '11' ? 'grade11' : 'grade10') + '/index.html'); return; }
        start(b);
      })
      .catch(function () { message('Can’t reach EngQuest right now', 'Check your internet, then try again.', load); });
  }

  if (document.fonts && document.fonts.ready) document.fonts.ready.then(load); else load();
})();
