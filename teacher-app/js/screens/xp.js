/* Give XP: pick a reason once, then tap each student who earned it. */
(function () {
  // Keys match teacher/activeness.html ACTS; points match the leaderboard view.
  var REASONS = [
    { key: 'Answering', label: 'Answering', xp: 5 },
    { key: 'Presenting', label: 'Presenting', xp: 20 },
    { key: 'Helping Hand', label: 'Helping Hand', xp: 20 },
    { key: 'Doing Task On Time', label: 'Task On Time', xp: 20 },
    { key: 'Quiz', label: 'Quiz', xp: 10 },
    { key: 'Vocabulary Box', label: 'Vocab Box', xp: 2 }
  ];
  var COLORS = ['#6366f1', '#0ea5e9', '#f59e0b', '#10b981', '#ec4899', '#8b5cf6', '#14b8a6', '#f97316', '#3b82f6', '#22c55e', '#e11d48', '#a855f7'];
  var REASON_KEY = 'eqt_xp_reason';
  var st = null;

  function reason() {
    var saved = null;
    try { saved = localStorage.getItem(REASON_KEY); } catch (e) { /* storage off */ }
    return REASONS.filter(function (r) { return r.key === saved; })[0] || REASONS[0];
  }

  function load() {
    var ctx = st.ctx;
    st.el.innerHTML = '<div class="state"><div class="spinner"></div>Loading ' + ctx.esc(ctx.classLabel) + '…</div>';
    var token = st.token;
    ctx.session.read({ action: 'getAllActiveness', className: ctx.className }).then(function (data) {
      if (token !== st.token) return;
      st.students = (data.students || []).slice().sort(function (a, b) { return Number(a.no) - Number(b.no); });
      draw();
    }).catch(function (err) {
      if (token !== st.token) return;
      st.el.innerHTML = '<div class="state"><b>Could not load the class</b>' + ctx.esc(err.message) +
        '<br><button class="btn brand" type="button" id="xpRetry">Try again</button></div>';
      st.el.querySelector('#xpRetry').onclick = load;
    });
  }

  function draw() {
    var ctx = st.ctx, esc = ctx.esc, r = reason();
    ctx.setSubtitle('Tap a student to give +' + r.xp + ' XP for ' + r.label);
    if (!st.students.length) {
      st.el.innerHTML = '<div class="state"><b>No students in ' + esc(ctx.classLabel) + '</b>Pick another class from the top.</div>';
      return;
    }
    st.el.innerHTML =
      '<div class="cats" role="radiogroup" aria-label="Reason">' + REASONS.map(function (x) {
        return '<button type="button" class="cat' + (x.key === r.key ? ' on' : '') + '" data-key="' + esc(x.key) + '" role="radio" aria-checked="' + (x.key === r.key) + '">' + esc(x.label) + '<em>+' + x.xp + '</em></button>';
      }).join('') + '</div>' +
      '<div class="xp-grid">' + st.students.map(function (s) {
        var name = s.nickname || s.name;
        return '<button type="button" class="tile" data-no="' + esc(s.no) + '">' +
          '<span class="tno">' + esc(s.no) + '</span>' +
          '<span class="av" style="background:' + COLORS[Number(s.no) % COLORS.length] + '">' + esc(ctx.core.initials(name)) + '</span>' +
          '<b>' + esc(ctx.core.firstName(name)) + '</b><small>' + Number(s.Total || 0) + ' XP</small></button>';
      }).join('') + '</div>';
    st.el.querySelector('.cats').onclick = function (e) {
      var b = e.target.closest('[data-key]'); if (!b) return;
      try { localStorage.setItem(REASON_KEY, b.getAttribute('data-key')); } catch (err) { /* storage off */ }
      var nr = reason();
      st.el.querySelectorAll('.cat').forEach(function (c) {
        var on = c === b; c.classList.toggle('on', on); c.setAttribute('aria-checked', String(on));
      });
      ctx.setSubtitle('Tap a student to give +' + nr.xp + ' XP for ' + nr.label);
    };
    st.el.querySelector('.xp-grid').onclick = function (e) {
      var t = e.target.closest('.tile'); if (!t) return;
      give(t.getAttribute('data-no'), reason(), 1);
    };
  }

  function student(no) { return st.students.filter(function (s) { return String(s.no) === String(no); })[0]; }

  function paint(no) {
    var s = student(no), t = st.el.querySelector('.tile[data-no="' + no + '"]');
    if (s && t) t.querySelector('small').textContent = Number(s.Total || 0) + ' XP';
    return t;
  }

  // Taps are queued so they reach the backend one at a time, in order.
  function give(no, r, delta) {
    var ctx = st.ctx, s = student(no); if (!s) return;
    var token = st.token;
    s.Total = Number(s.Total || 0) + r.xp * delta;
    var t = paint(no);
    if (t && delta > 0) {
      var pop = document.createElement('span');
      pop.className = 'pop'; pop.textContent = '+' + r.xp;
      t.appendChild(pop);
      setTimeout(function () { pop.remove(); }, 800);
    }
    var name = ctx.core.firstName(s.nickname || s.name);
    if (delta > 0) {
      ctx.toast('+' + r.xp + ' XP · ' + name + ' · ' + r.label, null, { label: 'Undo', onClick: function () { give(no, r, -1); } });
    } else {
      ctx.toast('Undone · ' + name, null);
    }
    st.queue = st.queue.then(function () {
      if (token !== st.token) return;
      return ctx.session.call({ action: 'incrementActiveness', className: ctx.className, studentNo: no, column: r.key, delta: delta });
    }).then(function () {
      scheduleRefresh(token);
    }, function (err) {
      if (token !== st.token) return;
      s.Total = Number(s.Total || 0) - r.xp * delta;
      paint(no);
      if (err.message !== 'Session expired') ctx.toast('Not saved for ' + name + ': ' + err.message, 'err');
    });
  }

  // Once the taps settle, re-read the totals so they match the backend.
  function scheduleRefresh(token) {
    clearTimeout(st.refreshTimer);
    st.refreshTimer = setTimeout(function () {
      st.queue.then(function () {
        if (token !== st.token) return;
        return st.ctx.session.read({ action: 'getAllActiveness', className: st.ctx.className }).then(function (data) {
          if (token !== st.token) return;
          (data.students || []).forEach(function (fresh) {
            var s = student(fresh.no); if (s) { s.Total = fresh.Total; paint(fresh.no); }
          });
        });
      }).catch(function () { /* keep what we show */ });
    }, 1500);
  }

  EQT.registerScreen('xp', {
    title: 'Give XP',
    render: function (el, ctx) {
      st = { ctx: ctx, el: el, students: [], queue: Promise.resolve(), token: {} };
      load();
    },
    leave: function () {
      if (st) clearTimeout(st.refreshTimer);
      return true;
    }
  });
})();
