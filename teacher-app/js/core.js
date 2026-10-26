/* EngQuest Teacher app: pure logic helpers (browser + Node). */
(function (root) {
  var STATUSES = ['Present', 'Absent', 'Sick', 'Permit'];

  // UI "Chapter n" lives in backend chapter n+3 (same as teacher/scores.html modes 4/5/6).
  function taskToChapterCol(code) {
    var s = String(code || '').trim().toUpperCase();
    var m = /^C(\d+)T(\d+)$/.exec(s);
    if (m) return { chapter: String(Number(m[1]) + 3), column: 'Task ' + Number(m[2]) };
    m = /^C(\d+)([FS])$/.exec(s);
    if (m) return { chapter: String(Number(m[1]) + 3), column: m[2] === 'F' ? 'Formative' : 'Summative' };
    return null;
  }

  function validateScore(value) {
    var v = String(value == null ? '' : value).trim();
    if (v === '') return { ok: true, value: '' };
    if (!/^\d+(\.\d+)?$/.test(v)) return { ok: false, value: v, error: 'Score must be a number' };
    if (Number(v) > 100) return { ok: false, value: v, error: 'Score must be between 0 and 100' };
    return { ok: true, value: v };
  }

  function isLowScore(value) {
    var v = String(value == null ? '' : value).trim();
    return v !== '' && !isNaN(Number(v)) && Number(v) < 75;
  }

  function attendanceInitial(students, meetings, date) {
    var meeting = null;
    (meetings || []).forEach(function (m) { if (String(m.date) === String(date)) meeting = m; });
    var marks = {};
    (students || []).forEach(function (s) {
      var stored = meeting && s.attendance ? s.attendance['m' + meeting.number] : '';
      marks[String(s.no)] = STATUSES.indexOf(stored) > -1 ? stored : 'Present';
    });
    return { meeting: meeting, marks: marks };
  }

  function attendanceCounts(marks) {
    var c = { Present: 0, Absent: 0, Sick: 0, Permit: 0 };
    Object.keys(marks || {}).forEach(function (k) { if (c[marks[k]] != null) c[marks[k]]++; });
    return c;
  }

  function attendanceDirty(initialMarks, marks) {
    return Object.keys(marks || {}).some(function (k) { return marks[k] !== initialMarks[k]; });
  }

  function attendanceRecords(students, marks) {
    return (students || []).map(function (s) {
      return { no: String(s.no), status: marks[String(s.no)] || 'Present' };
    });
  }

  function initials(name) {
    var out = String(name || '').split(/\s+/).filter(Boolean).slice(0, 2)
      .map(function (p) { return p.charAt(0).toUpperCase(); }).join('');
    return out || 'EQ';
  }

  function firstName(name) {
    return String(name || '').trim().split(/\s+/)[0] || '';
  }

  // Same order as teacher/submissions.html: tasks, then Formative, then Summative, per chapter.
  function taskSortKey(task) {
    var s = String(task || '').toUpperCase();
    var m = /^C(\d+)T(\d+)$/.exec(s); if (m) return [Number(m[1]), 0, Number(m[2])];
    var f = /^C(\d+)F$/.exec(s); if (f) return [Number(f[1]), 1, 0];
    var u = /^C(\d+)S$/.exec(s); if (u) return [Number(u[1]), 2, 0];
    return [999, 9, 0];
  }

  function groupSubmissions(rows, opts) {
    var map = {}, order = [];
    (rows || []).forEach(function (s) {
      if (opts.className && s.className !== opts.className) return;
      if (opts.filter !== 'all' && s.status !== 'pending') return;
      var key = s.className + '|' + s.studentNo;
      if (!map[key]) {
        map[key] = { studentNo: String(s.studentNo), studentName: s.studentName, className: s.className, items: [] };
        order.push(key);
      }
      map[key].items.push(s);
    });
    var groups = order.map(function (k) { return map[k]; });
    groups.forEach(function (g) {
      g.items.sort(function (a, b) {
        var ka = taskSortKey(a.task), kb = taskSortKey(b.task);
        return ka[0] - kb[0] || ka[1] - kb[1] || ka[2] - kb[2];
      });
    });
    groups.sort(function (a, b) { return Number(a.studentNo) - Number(b.studentNo); });
    return groups;
  }

  var core = {
    STATUSES: STATUSES,
    taskToChapterCol: taskToChapterCol,
    validateScore: validateScore,
    isLowScore: isLowScore,
    attendanceInitial: attendanceInitial,
    attendanceCounts: attendanceCounts,
    attendanceDirty: attendanceDirty,
    attendanceRecords: attendanceRecords,
    initials: initials,
    firstName: firstName,
    groupSubmissions: groupSubmissions
  };

  if (typeof module === 'object' && module.exports) module.exports = core;
  else { root.EQT = root.EQT || {}; root.EQT.core = core; }
})(this);
