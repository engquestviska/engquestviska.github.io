/* LOCAL TESTING ONLY. Replaces EQApi.get with pretend data so the app can be
   tried without the real backend. Loaded only on localhost with ?mock=1.
   Add &fail=1 to make every write fail (to test error handling). */
(function (global) {
  var FAIL = /[?&]fail=1/.test(location.search);
  var FAIL_SCORE = /[?&]failscore=1/.test(location.search); // only score saves fail
  var EXPIRE = /[?&]expire=1/.test(location.search);        // writes say Unauthorized until a re-login
  var calls = global.__mockCalls = [];
  var WRITES = ['saveAttendance', 'removeAttendanceMeeting', 'reviewTaskSubmission', 'saveTaskStatus',
    'saveChapterScore', 'saveFinalScore', 'incrementActiveness'];

  var FIRST = ['Adit', 'Bella', 'Bima', 'Citra', 'Daffa', 'Dewi', 'Eka', 'Fajar', 'Gita', 'Hana', 'Indra', 'Jessica',
    'Kevin', 'Laras', 'Made', 'Nadia', 'Oka', 'Putri', 'Raka', 'Sinta', 'Tegar', 'Umi', 'Vino', 'Wulan', 'Yoga',
    'Zahra', 'Agung', 'Bunga', 'Cahya', 'Dimas', 'Elsa', 'Farel', 'Galih', 'Hesti', 'Ilham', 'Joko'];
  var LAST = ['Pratama', 'Kusuma', 'Andika', 'Wulandari', 'Ramadhan', 'Lestari', 'Saputra', 'Nugroho', 'Maharani'];

  function roster(n, cls) {
    var list = [];
    for (var i = 0; i < n; i++) {
      var name = FIRST[i % FIRST.length] + ' ' + LAST[i % LAST.length];
      if (cls === 'XE1' && i === 3) name = 'Andreas Marchellino Harnanto Wijayakusuma Putra';
      list.push({ no: String(i + 1), name: name, nickname: '', attendance: {} });
    }
    return list;
  }

  var db = {
    classes: { XE1: roster(36, 'XE1'), XE2: roster(12, 'XE2'), XE3: [] },
    meetings: { XE1: [{ number: 1, date: '2026-10-19' }], XE2: [], XE3: [] },
    xp: {}, scores: {}, finals: {},
    subs: []
  };
  db.classes.XE1.forEach(function (s, i) { s.attendance.m1 = i === 2 ? 'Absent' : 'Present'; });

  var TASKS = ['C1T1', 'C1T2', 'C2T1', 'C2F', 'C3S', 'ZZ9'];
  var id = 0;
  [['XE1', 2, 3], ['XE1', 5, 1], ['XE1', 11, 4], ['XE1', 21, 2], ['XE2', 1, 2]].forEach(function (r) {
    var stu = db.classes[r[0]][r[1] - 1];
    for (var k = 0; k < r[2]; k++) {
      db.subs.push({ subId: 'sub' + (++id), className: r[0], studentNo: String(r[1]), studentName: stu.name,
        task: TASKS[(k + r[1]) % TASKS.length], timestamp: '2026-10-2' + (k + 1) + ' 19:4' + k, status: 'pending' });
    }
  });
  db.subs.push({ subId: 'sub' + (++id), className: 'XE1', studentNo: '1', studentName: db.classes.XE1[0].name,
    task: 'C1T1', timestamp: '2026-10-10 08:00', status: 'reviewed' });

  var ACTS = ['Vocabulary Box', 'Answering', 'Presenting', 'Doing Task On Time', 'Helping Hand', 'Quiz'];
  var PTS = { 'Vocabulary Box': 2, 'Answering': 5, 'Presenting': 20, 'Doing Task On Time': 20, 'Helping Hand': 20, 'Quiz': 10 };
  function xpOf(cls, no) {
    var key = cls + '|' + no;
    if (!db.xp[key]) {
      var n = Number(no);
      db.xp[key] = { 'Vocabulary Box': n % 7, 'Answering': n % 5, 'Presenting': n % 3, 'Doing Task On Time': n % 4, 'Helping Hand': n % 2, 'Quiz': n % 3 };
    }
    return db.xp[key];
  }
  function total(x) { return ACTS.reduce(function (s, a) { return s + x[a] * PTS[a]; }, 0); }

  var COLS = ['Task 1', 'Task 2', 'Task 3', 'Formative', 'Summative'];
  function scoresOf(cls, ch, no) {
    var key = cls + '|' + ch + '|' + no;
    if (!db.scores[key]) {
      var n = Number(no);
      db.scores[key] = { 'Task 1': String(60 + (n * 7) % 40), 'Task 2': n % 4 ? String(70 + (n * 3) % 30) : '', 'Task 3': '', 'Formative': '', 'Summative': '' };
    }
    return db.scores[key];
  }
  function avg(sc) {
    var v = COLS.map(function (c) { return sc[c]; }).filter(function (x) { return x !== '' && x != null; }).map(Number);
    return v.length ? Math.round(v.reduce(function (a, b) { return a + b; }, 0) / v.length * 100) / 100 : '';
  }

  function photo(sub) {
    var c = document.createElement('canvas'); c.width = 720; c.height = 960;
    var g = c.getContext('2d');
    g.fillStyle = '#fdfcf7'; g.fillRect(0, 0, 720, 960);
    g.strokeStyle = '#cbd5e1'; for (var y = 120; y < 940; y += 44) { g.beginPath(); g.moveTo(40, y); g.lineTo(680, y); g.stroke(); }
    g.fillStyle = '#1e293b'; g.font = 'bold 44px sans-serif'; g.fillText('Task ' + sub.task, 60, 90);
    g.font = '30px cursive, sans-serif';
    for (var i = 0; i < 16; i++) g.fillText((i + 1) + '. ' + 'The quick brown fox jumps over the lazy dog'.slice(0, 18 + (i * 5) % 24), 60, 156 + i * 44);
    var url = c.toDataURL('image/jpeg', .7);
    // sub2 carries a hostile mime type, the way a crafted student upload could.
    var mime = sub.subId === 'sub2' ? 'image/jpeg" data-pwned="1' : 'image/jpeg';
    return { success: true, mimeType: mime, dataBase64: url.split(',')[1] };
  }

  function handle(p) {
    var cls = p.className;
    switch (p.action) {
      case 'checkLogin': return { ok: !!(p.username && p.password) };
      case 'getClassData':
        return { students: JSON.parse(JSON.stringify(db.classes[cls] || [])), meetings: (db.meetings[cls] || []).slice() };
      case 'saveAttendance': {
        var ms = db.meetings[cls], m = ms.filter(function (x) { return x.date === p.date; })[0];
        if (!m) { m = { number: ms.length + 1, date: p.date }; ms.push(m); }
        JSON.parse(p.records).forEach(function (r) {
          var s = db.classes[cls].filter(function (x) { return x.no === String(r.no); })[0];
          if (s) s.attendance['m' + m.number] = r.status;
        });
        return { success: true, meetingNumber: m.number };
      }
      case 'removeAttendanceMeeting':
        db.meetings[cls] = db.meetings[cls].filter(function (x) { return x.date !== p.date; });
        return { success: true };
      case 'getTaskSubmissions':
        return { success: true, submissions: db.subs.slice(), pending: db.subs.filter(function (s) { return s.status === 'pending'; }).length };
      case 'getSubmissionPhoto':
        return photo(db.subs.filter(function (s) { return s.subId === p.fileId; })[0] || { task: '?' });
      case 'reviewTaskSubmission':
        db.subs.forEach(function (s) { if (s.subId === p.subId) s.status = p.decision; });
        return { success: true };
      case 'saveTaskStatus': return { success: true };
      case 'getAllActiveness':
        return { students: (db.classes[cls] || []).map(function (s) {
          var x = xpOf(cls, s.no);
          return { no: s.no, name: s.name, nickname: '', Total: total(x), Indicator: total(x) > 100 ? 'Excellent' : 'Good' };
        }) };
      case 'incrementActiveness': {
        var x = xpOf(cls, p.studentNo); x[p.column] = Math.max(0, x[p.column] + Number(p.delta));
        return { success: true };
      }
      case 'getChapterScores':
        return { inputCols: COLS, students: (db.classes[cls] || []).map(function (s) {
          var sc = scoresOf(cls, p.chapter, s.no);
          return { no: s.no, name: s.name, nickname: '', scores: Object.assign({}, sc), average: avg(sc), finalScore: avg(sc), indicator: avg(sc) >= 75 ? 'Safe' : 'Not Safe' };
        }) };
      case 'saveChapterScore': scoresOf(cls, p.chapter, p.studentNo)[p.column] = p.value; return { success: true };
      case 'getFinalScores':
        return { inputCols: ['ASAT'], calcCols: ['Average', 'Indicator'], students: (db.classes[cls] || []).map(function (s) {
          var f = db.finals[cls + '|' + s.no] || { ASAT: '' };
          return { no: s.no, name: s.name, nickname: '', scores: Object.assign({}, f), calc: { Average: 80, Indicator: 'Safe' } };
        }) };
      case 'saveFinalScore':
        (db.finals[cls + '|' + p.studentNo] = db.finals[cls + '|' + p.studentNo] || {})[p.column] = p.value;
        return { success: true, calc: { Average: 81, Indicator: 'Safe' } };
    }
    return { error: 'Mock: unknown action ' + p.action };
  }

  global.EQApi.get = function (params) {
    calls.push(params);
    return new Promise(function (resolve, reject) {
      setTimeout(function () {
        if (FAIL && WRITES.indexOf(params.action) > -1) return reject(new Error('Network error'));
        if (FAIL_SCORE && /Score$/.test(params.action)) return reject(new Error('Network error'));
        if (EXPIRE && WRITES.indexOf(params.action) > -1 && !global.__mockRelogged) return resolve({ error: 'Unauthorized' });
        if (EXPIRE && params.action === 'checkLogin') global.__mockRelogged = true;
        resolve(handle(params));
      }, 250);
    });
  };
  console.info('[mock] EngQuest Teacher using pretend data');
})(window);
