/* Submissions: one student at a time, big photos, score + approve, then the next student. */
(function () {
  var PHOTOS = {};   // subId -> data URL, kept for the whole app session
  var LOADING = {};  // subId -> promise
  var st = null;

  function photo(ctx, subId) {
    if (PHOTOS[subId]) return Promise.resolve(PHOTOS[subId]);
    if (!LOADING[subId]) {
      LOADING[subId] = ctx.session.call({ action: 'getSubmissionPhoto', fileId: subId }).then(function (res) {
        if (!res.dataBase64) throw new Error('No photo');
        PHOTOS[subId] = 'data:' + (res.mimeType || 'image/jpeg') + ';base64,' + res.dataBase64;
        return PHOTOS[subId];
      }).finally(function () { delete LOADING[subId]; });
    }
    return LOADING[subId];
  }

  function load() {
    var ctx = st.ctx;
    st.el.innerHTML = '<div class="state"><div class="spinner"></div>Loading submissions…</div>';
    ctx.setSubtitle('Loading…');
    var token = st.token;
    ctx.session.call({ action: 'getTaskSubmissions' }).then(function (res) {
      if (token !== st.token) return;
      st.rows = res.submissions || [];
      st.idx = 0;
      regroup();
      draw();
    }).catch(function (err) {
      if (token !== st.token || err.message === 'Session expired') return;
      st.el.innerHTML = '<div class="state"><b>Could not load submissions</b>' + ctx.esc(err.message) +
        '<br><button class="btn brand" type="button" id="subRetry">Try again</button></div>';
      st.el.querySelector('#subRetry').onclick = load;
    });
  }

  function regroup() {
    var ctx = st.ctx;
    st.groups = ctx.core.groupSubmissions(st.rows, { className: ctx.className, filter: st.filter });
    var pending = st.rows.filter(function (r) { return r.className === ctx.className && r.status === 'pending'; }).length;
    ctx.setBadge('submissions', pending);
    st.pending = pending;
    if (st.idx >= st.groups.length) st.idx = Math.max(0, st.groups.length - 1);
  }

  function pendingItems(g) { return g.items.filter(function (i) { return i.status === 'pending'; }); }

  function draw() {
    var ctx = st.ctx, esc = ctx.esc;
    var g = st.groups[st.idx];
    var waitingStudents = ctx.core.groupSubmissions(st.rows, { className: ctx.className, filter: 'pending' }).length;
    ctx.setSubtitle(st.pending + ' photo' + (st.pending === 1 ? '' : 's') + ' waiting · ' + waitingStudents + ' student' + (waitingStudents === 1 ? '' : 's'));
    var toggle = '<div class="sub-toggle" role="tablist">' +
      '<button type="button" data-f="pending" class="' + (st.filter === 'pending' ? 'on' : '') + '">Waiting</button>' +
      '<button type="button" data-f="all" class="' + (st.filter === 'all' ? 'on' : '') + '">All</button></div>';
    if (!g) {
      st.el.innerHTML = toggle + '<div class="state"><b>' + (st.filter === 'pending' ? '🎉 All caught up in ' + esc(ctx.classLabel) : 'No submissions in ' + esc(ctx.classLabel) + ' yet') + '</b>' +
        (st.filter === 'pending' ? 'Nothing waiting to check.' : '') + '</div>';
      bindToggle();
      return;
    }
    st.slide = Math.min(st.slide || 0, g.items.length - 1);
    var pend = pendingItems(g);
    st.el.innerHTML = toggle +
      '<div class="sub-who"><div class="sub-name"><b>' + esc(g.studentName) + '</b><small>No. ' + esc(g.studentNo) + ' · ' + g.items.length + ' photo' + (g.items.length > 1 ? 's' : '') +
        (pend.length ? ' · ' + pend.length + ' to check' : '') + '</small></div>' +
        '<span class="sub-pos">' + (st.idx + 1) + ' / ' + st.groups.length + '</span></div>' +
      '<div class="viewer"><div class="slides" id="slides">' + g.items.map(function (it, i) {
        return '<div class="slide" data-i="' + i + '" data-id="' + esc(it.subId) + '"><div class="ph-load"><div class="spinner"></div></div></div>';
      }).join('') + '</div>' +
      (g.items.length > 1 ? '<div class="dots">' + g.items.map(function (_, i) { return '<i class="' + (i === st.slide ? 'on' : '') + '"></i>'; }).join('') + '</div>' : '') +
      '</div>' +
      '<div id="slideInfo"></div>' +
      '<div class="sub-actions" id="subActions"></div>' +
      (st.groups.length > 1 ? '<div class="queue">' + st.groups.map(function (q, i) {
        var n = pendingItems(q).length;
        return '<button type="button" class="q' + (i === st.idx ? ' on' : '') + '" data-q="' + i + '">' + esc(ctx.core.firstName(q.studentName)) + (n ? '<em>' + n + '</em>' : '') + '</button>';
      }).join('') + '</div>' : '');
    bindToggle();
    var slides = st.el.querySelector('#slides');
    slides.scrollLeft = st.slide * slides.clientWidth;
    slides.addEventListener('scroll', function () {
      var i = Math.round(slides.scrollLeft / slides.clientWidth);
      if (i !== st.slide) { st.slide = i; drawSlideInfo(); }
    }, { passive: true });
    slides.onclick = function (e) {
      var img = e.target.closest('img'); if (img) openViewer(img.src);
    };
    var queue = st.el.querySelector('.queue');
    if (queue) {
      queue.onclick = function (e) {
        var b = e.target.closest('[data-q]'); if (!b || st.busy) return;
        st.idx = Number(b.getAttribute('data-q')); st.slide = 0; draw();
      };
      var on = queue.querySelector('.on'); if (on) queue.scrollLeft = on.offsetLeft - 12;
    }
    g.items.forEach(function (it) { fillSlide(it.subId); });
    var next = st.groups[st.idx + 1];
    if (next) photo(ctx, next.items[0].subId).catch(function () {});
    drawSlideInfo();
  }

  function fillSlide(subId) {
    photo(st.ctx, subId).then(function (src) {
      var box = st.el.querySelector('.slide[data-id="' + cssEsc(subId) + '"]');
      if (box) box.innerHTML = '<img alt="Submitted work" src="' + src + '">';
    }).catch(function (err) {
      if (err.message === 'Session expired') return;
      var box = st.el.querySelector('.slide[data-id="' + cssEsc(subId) + '"]');
      if (box) {
        box.innerHTML = '<div class="ph-load">Could not load photo<br><button class="btn ghost" type="button">Retry</button></div>';
        box.querySelector('button').onclick = function (e) { e.stopPropagation(); fillSlide(subId); };
      }
    });
  }

  function cssEsc(v) { return String(v).replace(/["\\]/g, '\\$&'); }

  function drawSlideInfo() {
    var ctx = st.ctx, esc = ctx.esc;
    var g = st.groups[st.idx]; if (!g) return;
    var it = g.items[st.slide] || g.items[0];
    st.el.querySelectorAll('.dots i').forEach(function (d, i) { d.classList.toggle('on', i === st.slide); });
    var map = ctx.core.taskToChapterCol(it.task);
    var statusPill = it.status === 'pending' ? '<span class="code">' + esc(it.task || '—') + '</span>'
      : '<span class="code ' + (it.status === 'rejected' ? 'rej' : 'ok') + '">' + esc(it.task || '—') + ' · ' + (it.status === 'rejected' ? 'Sent back' : 'Approved') + '</span>';
    var info = '<div class="slide-info"><div><small>Photo ' + (st.slide + 1) + ' of ' + g.items.length + ' · ' + esc(it.timestamp || '') + '</small></div>' + statusPill + '</div>';
    if (it.status === 'pending' && map) {
      var chap = Number(map.chapter) - 3;
      var typed = st.scores[it.subId] != null ? st.scores[it.subId] : '';
      info += '<label class="scorebox"><span>Score · Chapter ' + chap + ' ' + esc(map.column) + '<small>Leave empty to approve without a score</small></span>' +
        '<input id="scoreIn" inputmode="decimal" enterkeyhint="done" autocomplete="off" value="' + esc(typed) + '" placeholder="–"' +
        (ctx.core.validateScore(typed).ok ? '' : ' class="bad"') + '></label>';
    } else if (it.status === 'pending') {
      info += '<p class="sub-note">This task code has no score column, so approving only ticks it.</p>';
    }
    st.el.querySelector('#slideInfo').innerHTML = info;
    var input = st.el.querySelector('#scoreIn');
    if (input) {
      input.oninput = function () {
        st.scores[it.subId] = input.value;
        input.classList.toggle('bad', !ctx.core.validateScore(input.value).ok);
      };
      input.onkeydown = function (e) { if (e.key === 'Enter') input.blur(); };
    }
    var pend = pendingItems(g);
    var acts = st.el.querySelector('#subActions');
    if (pend.length) {
      acts.innerHTML = (it.status === 'pending' ? '<button class="btn bad" type="button" data-a="back">Send back</button>' : '<span></span>') +
        '<button class="btn ok" type="button" data-a="approve">✓ Approve' + (pend.length > 1 ? ' all ' + pend.length : '') + (st.idx < st.groups.length - 1 ? ' &amp; next' : '') + '</button>';
    } else {
      acts.innerHTML = '<span></span><button class="btn ghost" type="button" data-a="next"' + (st.idx >= st.groups.length - 1 ? ' disabled' : '') + '>Next student →</button>';
    }
    acts.onclick = function (e) {
      var b = e.target.closest('[data-a]'); if (!b || st.busy) return;
      var a = b.getAttribute('data-a');
      if (a === 'approve') approve(g);
      else if (a === 'back') sendBack(it);
      else { st.idx++; st.slide = 0; draw(); }
    };
  }

  function setBusy(on) {
    st.busy = on;
    st.el.querySelectorAll('#subActions button').forEach(function (b) { b.disabled = on; });
    var ap = st.el.querySelector('[data-a="approve"]'); if (ap && on) ap.textContent = 'Saving…';
  }

  function approve(g) {
    var ctx = st.ctx;
    var pend = pendingItems(g);
    for (var i = 0; i < pend.length; i++) {
      var v = ctx.core.validateScore(st.scores[pend[i].subId] || '');
      if (!v.ok) {
        st.slide = g.items.indexOf(pend[i]); draw();
        ctx.toast(v.error + ' (' + pend[i].task + ')', 'err');
        return;
      }
    }
    setBusy(true);
    var token = st.token, done = 0;
    var chain = Promise.resolve();
    pend.forEach(function (it) {
      chain = chain.then(function () {
        return ctx.session.call({ action: 'reviewTaskSubmission', subId: it.subId, decision: 'reviewed' });
      }).then(function () {
        it.status = 'reviewed';
        if (!it.task) return;
        var tasks = {}; tasks[it.task] = true;
        return ctx.session.call({ action: 'saveTaskStatus', className: it.className, studentNo: it.studentNo, tasks: JSON.stringify(tasks) });
      }).then(function () {
        var score = ctx.core.validateScore(st.scores[it.subId] || '').value;
        var map = ctx.core.taskToChapterCol(it.task);
        if (score === '' || !map) return;
        return ctx.session.call({ action: 'saveChapterScore', className: it.className, chapter: map.chapter, studentNo: it.studentNo, column: map.column, value: score });
      }).then(function () { done++; delete st.scores[it.subId]; });
    });
    chain.then(function () {
      if (token !== st.token) return;
      st.busy = false;
      ctx.toast('Approved ' + done + ' · ' + ctx.core.firstName(g.studentName), 'ok');
      advanceFrom(g);
    }).catch(function (err) {
      if (token !== st.token) return;
      st.busy = false;
      regroup(); draw();
      if (err.message !== 'Session expired') ctx.toast('Stopped after ' + done + ': ' + err.message, 'err');
    });
  }

  function sendBack(it) {
    var ctx = st.ctx;
    ctx.confirm('Send this photo back?', 'The student will see ' + (it.task || 'this task') + ' as sent back and can upload it again.', 'Send back', true).then(function (ok) {
      if (!ok) return;
      setBusy(true);
      var token = st.token;
      var g = st.groups[st.idx];
      ctx.session.call({ action: 'reviewTaskSubmission', subId: it.subId, decision: 'rejected' }).then(function () {
        if (token !== st.token) return;
        it.status = 'rejected';
        st.busy = false;
        ctx.toast('Sent back · ' + (it.task || 'photo'), 'ok');
        if (pendingItems(g).length) { regroup(); draw(); } else advanceFrom(g);
      }).catch(function (err) {
        if (token !== st.token) return;
        st.busy = false; drawSlideInfo();
        if (err.message !== 'Session expired') ctx.toast('Not sent back: ' + err.message, 'err');
      });
    });
  }

  // After finishing a student, show the next one (in Waiting mode the finished student drops out).
  function advanceFrom(g) {
    var key = g.className + '|' + g.studentNo;
    var oldIdx = st.idx;
    regroup();
    var stillThere = st.groups.findIndex(function (x) { return x.className + '|' + x.studentNo === key; });
    st.idx = stillThere > -1 ? Math.min(stillThere + 1, st.groups.length - 1) : Math.min(oldIdx, Math.max(0, st.groups.length - 1));
    st.slide = 0;
    draw();
  }

  function bindToggle() {
    st.el.querySelector('.sub-toggle').onclick = function (e) {
      var b = e.target.closest('[data-f]'); if (!b || st.busy) return;
      st.filter = b.getAttribute('data-f'); st.idx = 0; st.slide = 0;
      regroup(); draw();
    };
  }

  function openViewer(src) {
    var v = document.createElement('div');
    v.className = 'photo-viewer';
    v.innerHTML = '<button type="button" class="pv-close" aria-label="Close">✕</button><div class="pv-scroll"><img alt="Submitted work, full size" src="' + src + '"></div><p class="pv-hint">Tap the photo to zoom</p>';
    document.body.appendChild(v);
    var img = v.querySelector('img');
    img.onclick = function () { v.classList.toggle('zoom'); };
    v.querySelector('.pv-close').onclick = close;
    function close() { v.remove(); window.removeEventListener('hashchange', close); }
    window.addEventListener('hashchange', close);
  }

  EQT.registerScreen('submissions', {
    title: 'Submissions',
    render: function (el, ctx) {
      var keepFilter = st ? st.filter : 'pending';
      st = { ctx: ctx, el: el, rows: [], groups: [], idx: 0, slide: 0, scores: {}, filter: keepFilter, token: {}, busy: false };
      load();
    },
    leave: function () {
      var typed = st && Object.keys(st.scores).some(function (k) { return String(st.scores[k]).trim() !== ''; });
      var go = typed ? st.ctx.confirm('Leave without approving?', 'Scores you typed here are only saved when you tap Approve.', 'Leave', true) : Promise.resolve(true);
      return go.then(function (ok) { if (ok && st) st.token = {}; return ok; });
    }
  });
})();
