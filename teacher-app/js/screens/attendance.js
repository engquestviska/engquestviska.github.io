/* Attendance: everyone starts Present; tap P / A / S / I for the rest, then Save. */
(function () {
  var LETTER = { Present: 'P', Absent: 'A', Sick: 'S', Permit: 'I' };
  var DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  var MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  var st = null; // state for the class on show

  function today() {
    var d = new Date();
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
  }
  function niceDate(iso) {
    var p = iso.split('-'), d = new Date(Number(p[0]), Number(p[1]) - 1, Number(p[2]));
    return DAYS[d.getDay()] + ', ' + d.getDate() + ' ' + MONTHS[d.getMonth()] + (iso === today() ? ' (today)' : '');
  }

  function dirty() { return st && st.loaded && st.ctx.core.attendanceDirty(st.initial, st.marks); }
  function changes() {
    return Object.keys(st.marks).filter(function (k) { return st.marks[k] !== st.initial[k]; }).length;
  }

  function load(date) {
    st.date = date;
    st.loaded = false;
    var ctx = st.ctx;
    ctx.setSubtitle(niceDate(date) + ' · loading…');
    st.el.innerHTML = '<div class="state"><div class="spinner"></div>Loading ' + ctx.esc(ctx.classLabel) + '…</div>';
    st.bar.hidden = true;
    var token = st.token = {};
    ctx.session.read({ action: 'getClassData', className: ctx.className }).then(function (data) {
      if (token !== st.token) return;
      st.students = data.students || [];
      st.meetings = data.meetings || [];
      var init = ctx.core.attendanceInitial(st.students, st.meetings, date);
      st.meeting = init.meeting;
      st.initial = init.marks;
      st.marks = Object.assign({}, init.marks);
      st.loaded = true;
      st.failed = false;
      draw();
    }).catch(function (err) {
      if (token !== st.token) return;
      st.el.innerHTML = '<div class="state"><b>Could not load the class</b>' + ctx.esc(err.message) +
        '<br><button class="btn brand" type="button" id="attRetry">Try again</button></div>';
      st.el.querySelector('#attRetry').onclick = function () { load(date); };
    });
  }

  function subtitle() {
    var ctx = st.ctx;
    ctx.setSubtitle(niceDate(st.date) + ' · ' + (st.meeting ? 'Meeting ' + st.meeting.number : 'New meeting') + ' ▾', openDateSheet);
  }

  function draw() {
    var ctx = st.ctx, esc = ctx.esc;
    subtitle();
    ctx.setAction(openMenu);
    if (!st.students.length) {
      st.el.innerHTML = '<div class="state"><b>No students in ' + esc(ctx.classLabel) + '</b>Pick another class from the top.</div>';
      st.bar.hidden = true;
      return;
    }
    st.el.innerHTML =
      '<div class="att-summary" id="attSummary"></div>' +
      '<div class="att-hint">P Present · A Absent · S Sick · I Permit</div>' +
      '<div class="att-list">' + st.students.map(function (s) {
        var no = String(s.no);
        return '<div class="att-row" data-no="' + esc(no) + '">' +
          '<span class="no">' + esc(no) + '</span>' +
          '<span class="nm">' + esc(s.nickname || s.name) + '</span>' +
          '<div class="seg" role="radiogroup" aria-label="' + esc(s.name) + '">' + ctx.core.STATUSES.map(function (k) {
            return '<button type="button" class="' + k.toLowerCase() + (st.marks[no] === k ? ' on' : '') + '" data-k="' + k + '" role="radio" aria-checked="' + (st.marks[no] === k) + '" aria-label="' + k + '">' + LETTER[k] + '</button>';
          }).join('') + '</div></div>';
      }).join('') + '</div>';
    st.el.querySelector('.att-list').onclick = onTap;
    refresh();
  }

  function onTap(e) {
    var b = e.target.closest('button[data-k]'); if (!b || st.saving) return;
    var row = b.closest('.att-row'), no = row.getAttribute('data-no');
    st.marks[no] = b.getAttribute('data-k');
    row.querySelectorAll('button').forEach(function (x) {
      var on = x === b; x.classList.toggle('on', on); x.setAttribute('aria-checked', String(on));
    });
    st.failed = false;
    refresh();
  }

  function refresh() {
    var c = st.ctx.core.attendanceCounts(st.marks);
    var sum = st.el.querySelector('#attSummary');
    if (sum) {
      sum.innerHTML = ['Present', 'Absent', 'Sick', 'Permit'].map(function (k) {
        return '<div class="att-sum ' + k.toLowerCase() + '"><b>' + c[k] + '</b><span>' + k + '</span></div>';
      }).join('');
    }
    var bar = st.bar, n = changes();
    var unsaved = !st.meeting || n > 0;
    bar.hidden = false;
    bar.classList.toggle('done', !unsaved && !st.failed);
    var title, sub;
    if (st.saving) { title = 'Saving…'; sub = st.students.length + ' students'; }
    else if (st.failed) { title = 'Not saved'; sub = 'Check the internet, then tap Save again'; }
    else if (!st.meeting) { title = 'Not saved yet'; sub = n ? n + ' marked not present' : 'Everyone is Present'; }
    else if (n) { title = 'Not saved yet'; sub = n + ' change' + (n > 1 ? 's' : ''); }
    else { title = 'Saved ✓'; sub = 'Meeting ' + st.meeting.number + ' · ' + c.Present + ' present'; }
    bar.querySelector('b').textContent = title;
    bar.querySelector('small').textContent = sub;
    var btn = bar.querySelector('button');
    btn.disabled = st.saving || (!unsaved && !st.failed);
    btn.textContent = st.saving ? 'Saving…' : 'Save';
  }

  function save() {
    var ctx = st.ctx;
    if (st.saving || !st.loaded) return;
    st.saving = true; refresh();
    var records = ctx.core.attendanceRecords(st.students, st.marks);
    var token = st.token;
    ctx.session.call({ action: 'saveAttendance', className: ctx.className, date: st.date, records: JSON.stringify(records) }).then(function (res) {
      if (token !== st.token) return;
      var num = res.meetingNumber != null ? res.meetingNumber : (st.meeting && st.meeting.number);
      st.meeting = { number: num, date: st.date };
      if (!st.meetings.some(function (m) { return String(m.date) === st.date; })) st.meetings.push(st.meeting);
      st.students.forEach(function (s) { s.attendance = s.attendance || {}; s.attendance['m' + num] = st.marks[String(s.no)]; });
      st.initial = Object.assign({}, st.marks);
      st.saving = false; st.failed = false;
      subtitle(); refresh();
      ctx.toast('Attendance saved for ' + ctx.classLabel, 'ok');
    }).catch(function (err) {
      if (token !== st.token) return;
      st.saving = false; st.failed = true; refresh();
      if (err.message !== 'Session expired') ctx.toast('Not saved: ' + err.message, 'err');
    });
  }

  function confirmDiscard() {
    if (!dirty()) return Promise.resolve(true);
    return st.ctx.confirm('Leave without saving?', 'Your attendance changes for ' + st.ctx.classLabel + ' are not saved yet.', 'Leave', true);
  }

  function openDateSheet() {
    var ctx = st.ctx;
    ctx.openSheet('<h2>Which day?</h2><p>Pick the date of the meeting to mark or check.</p>' +
      '<input type="date" id="attDate" max="' + today() + '" value="' + st.date + '">' +
      '<div class="sheet-actions"><button class="btn ghost" type="button" data-a="today">Today</button>' +
      '<button class="btn brand" type="button" data-a="go">Show</button></div>', function (el) {
      el.onclick = function (e) {
        var b = e.target.closest('[data-a]'); if (!b) return;
        var date = b.getAttribute('data-a') === 'today' ? today() : el.querySelector('#attDate').value;
        if (!date) return;
        ctx.closeSheet();
        if (date === st.date) return;
        confirmDiscard().then(function (ok) { if (ok) load(date); });
      };
      document.getElementById('sheetBackdrop').onclick = ctx.closeSheet;
    });
  }

  function openMenu() {
    var ctx = st.ctx;
    if (!st.loaded) return;
    ctx.openSheet('<h2>' + ctx.esc(ctx.classLabel) + ' · ' + niceDate(st.date) + '</h2>' +
      '<div class="sheet-list"><button class="sheet-row" type="button" data-a="all">✓ Mark everyone Present</button>' +
      (st.meeting ? '<button class="sheet-row danger" type="button" data-a="remove">Remove this meeting</button>' : '') +
      '</div>', function (el) {
      el.onclick = function (e) {
        var b = e.target.closest('[data-a]'); if (!b) return;
        ctx.closeSheet();
        if (b.getAttribute('data-a') === 'all') {
          Object.keys(st.marks).forEach(function (k) { st.marks[k] = 'Present'; });
          draw();
        } else removeMeeting();
      };
      document.getElementById('sheetBackdrop').onclick = ctx.closeSheet;
    });
  }

  function removeMeeting() {
    var ctx = st.ctx, date = st.date;
    ctx.confirm('Remove this meeting?', 'This deletes everyone’s marks for ' + niceDate(date) + ' in ' + ctx.classLabel + '. It cannot be undone.', 'Remove', true).then(function (ok) {
      if (!ok) return;
      ctx.session.call({ action: 'removeAttendanceMeeting', className: ctx.className, date: date }).then(function () {
        ctx.toast('Meeting removed', 'ok');
        load(date);
      }).catch(function (err) {
        if (err.message !== 'Session expired') ctx.toast('Not removed: ' + err.message, 'err');
      });
    });
  }

  EQT.registerScreen('attendance', {
    title: 'Attendance',
    render: function (el, ctx) {
      var keepDate = st && st.ctx && st.date;
      el.innerHTML = '<div id="attBody"></div>';
      var bar = document.getElementById('attBar');
      if (!bar) {
        bar = document.createElement('div');
        bar.id = 'attBar'; bar.className = 'savebar';
        bar.innerHTML = '<div><b></b><small></small></div><button class="btn brand" type="button">Save</button>';
        document.getElementById('app').appendChild(bar);
      }
      st = { ctx: ctx, el: el.querySelector('#attBody'), bar: bar, token: {} };
      bar.querySelector('button').onclick = save;
      load(keepDate || today());
    },
    leave: function () {
      return confirmDiscard().then(function (ok) {
        if (ok && st) { st.token = {}; st.bar.hidden = true; }
        return ok;
      });
    }
  });
})();
