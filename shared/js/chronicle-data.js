/* EngQuest Chronicle: turns the getChronicle bundle into what the story, records page and
   share card draw. Pure functions only; loads in the browser (window.EQChronicleData) and in Node. */
(function (global) {
  var ACCENT = { '10': '#2450E0', '11': '#0F8A73' };
  var DASH = '—';

  function classLabel(id) {
    if (global.EQClasses && global.EQClasses.labelFor) return global.EQClasses.labelFor(id);
    var m = String(id || '').match(/^(XI|X)([A-Z])(\d+)$/);
    return m ? m[1] + ' ' + m[2] + '-' + m[3] : String(id || '');
  }

  // Big rank numbers shrink as they get longer so they never run off the slide.
  function rankFontSize(place, base) {
    var digits = String(place).length;
    if (base === 250) return digits <= 1 ? 250 : digits === 2 ? 170 : 120;
    return digits <= 2 ? 170 : 120;
  }

  function percent(part, whole) { return whole ? Math.round((part / whole) * 100) + '%' : DASH; }

  function build(b, opts) {
    var grade = String((opts && opts.grade) || (b.student && b.student.grade) || '10');
    var gradeLabel = 'Grade ' + grade;
    var st = b.student || {};
    var cls = classLabel(st.classId);
    var name = (st.nickname && String(st.nickname).trim()) || st.name || '';
    var att = b.attendance || { meetings: 0, present: 0, days: [] };
    var final = b.scores ? b.scores.final : '';
    var hasFinal = final !== '' && final != null;
    var need = 5 + 5 * (b.xp ? b.xp.level : 1);
    var into = Math.max(0, need - (b.xp ? b.xp.toNext : need));
    var classes = ((b.gradeRank && b.gradeRank.classes) || []).map(function (id) {
      return { id: id, label: classLabel(id), mine: id === st.classId };
    });

    var slides = [{ id: 'cover', name: name, classLabel: cls, gradeLabel: gradeLabel, label: b.label || '' }];
    if (att.meetings > 0) slides.push({
      id: 'attendance', present: att.present, meetings: att.meetings, percent: percent(att.present, att.meetings),
      days: (att.days || []).map(function (d) { var s = String(d.status || '').toLowerCase(); return s === 'present' ? 'p' : (s === 'sick' || s === 'permit') ? 's' : s === 'absent' ? 'a' : ''; })
    });
    if (b.tasks && (b.tasks.done > 0 || b.tasks.best)) slides.push({ id: 'tasks', done: b.tasks.done, best: b.tasks.best });
    if (b.vocab && b.vocab.total > 0) slides.push({ id: 'vocab', total: b.vocab.total, words: b.vocab.words, latest: b.vocab.latest, classTop5: !!b.vocab.classTop5 });
    if (b.xp && b.xp.total > 0) slides.push({ id: 'level', level: b.xp.level, xp: b.xp.total, toNext: b.xp.toNext, progress: need ? into / need : 0 });
    if (hasFinal) {
      slides.push({ id: 'final', final: final, units: b.scores.units, asat: b.scores.asat });
      if (b.rank && b.rank.place != null) slides.push({ id: 'rank', place: b.rank.place, of: b.rank.of, classLabel: cls, fontSize: rankFontSize(b.rank.place, 250) });
      if (b.gradeRank && b.gradeRank.place != null) slides.push({ id: 'gradeRank', place: b.gradeRank.place, of: b.gradeRank.of, gradeLabel: gradeLabel, classes: classes, fontSize: rankFontSize(b.gradeRank.place, 170) });
    }

    var rankPlace = hasFinal && b.rank && b.rank.place != null ? b.rank.place : null;
    var gradePlace = hasFinal && b.gradeRank && b.gradeRank.place != null ? b.gradeRank.place : null;
    return {
      name: name, fullName: st.name || '', classLabel: cls, gradeLabel: gradeLabel, grade: grade,
      label: b.label || '', accent: ACCENT[grade] || ACCENT['10'],
      slides: slides,
      card: {
        name: name, classLine: cls + ' · ' + gradeLabel, label: b.label || '',
        rank: rankPlace != null ? '#' + rankPlace : DASH,
        rankFontSize: rankPlace != null ? rankFontSize(rankPlace, 170) : 170,
        gradeRank: gradePlace != null ? '#' + gradePlace + ' of ' + b.gradeRank.of + ' in ' + gradeLabel : '',
        final: hasFinal ? String(final) : DASH,
        level: b.xp ? String(b.xp.level) : DASH,
        present: percent(att.present, att.meetings),
        tasks: String((b.tasks && b.tasks.done) || 0),
        words: String((b.vocab && b.vocab.total) || 0)
      },
      records: {
        final: hasFinal ? final : DASH, units: (b.scores && b.scores.units) || [], asat: b.scores ? b.scores.asat : '',
        rank: rankPlace, rankOf: b.rank ? b.rank.of : 0, gradeRank: gradePlace, gradeOf: b.gradeRank ? b.gradeRank.of : 0,
        present: att.present, meetings: att.meetings, percent: percent(att.present, att.meetings),
        days: slides[1] && slides[1].id === 'attendance' ? slides[1].days : [],
        level: b.xp ? b.xp.level : 0, xp: b.xp ? b.xp.total : 0,
        tasksDone: (b.tasks && b.tasks.done) || 0, best: b.tasks ? b.tasks.best : null,
        words: (b.vocab && b.vocab.words) || [], wordTotal: (b.vocab && b.vocab.total) || 0, classTop5: !!(b.vocab && b.vocab.classTop5)
      }
    };
  }

  var WORDS = ['brave', 'thorough', 'resilient', 'curious', 'vivid', 'journey', 'achieve', 'humble', 'ambition',
    'persuade', 'genuine', 'candid', 'diligent', 'sincere', 'eager', 'fragile', 'notorious', 'gratitude'];
  var DAYS = 'ppppppsppppppppppppppspppppppppspp'.split('').map(function (c, i) {
    return { date: 'd' + i, status: c === 's' ? (i % 2 ? 'Sick' : 'Permit') : 'Present' };
  });
  var SAMPLE = {
    success: true, active: true, start: '', label: 'Semester 1 · 2026/27',
    student: { name: 'Rafi Pratama', nickname: 'Rafi', classId: 'XE2', grade: '10', no: 5 },
    attendance: { meetings: 34, present: 31, sick: 2, permit: 1, absent: 0, days: DAYS },
    tasks: { done: 28, best: { label: 'Recount · Task 2', score: 96 } },
    vocab: { total: 142, words: WORDS, latest: 'thorough', classTop5: true },
    xp: { total: 4820, level: 12, toNext: 20 },
    scores: { units: [{ label: 'Descriptive Text', score: 90 }, { label: 'Recount', score: 85 }, { label: 'Procedure', score: 88 }], asat: 84, final: 87 },
    rank: { place: 4, of: 36 },
    gradeRank: { place: 11, of: 178, classes: ['XE1', 'XE2', 'XE3', 'XE4', 'XE5'] }
  };
  var SAMPLE_LONGNAME = JSON.parse(JSON.stringify(SAMPLE));
  SAMPLE_LONGNAME.student.name = 'Muhammad Rafi Alfarizi Pratama';
  SAMPLE_LONGNAME.student.nickname = '';

  var api = { build: build, rankFontSize: rankFontSize, classLabel: classLabel, SAMPLE: SAMPLE, SAMPLE_LONGNAME: SAMPLE_LONGNAME };
  global.EQChronicleData = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(typeof window !== 'undefined' ? window : globalThis);
