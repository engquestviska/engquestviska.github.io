/* Fills the student homepage: the four summary boxes and the "do this next" prompt. */
(function(global) {
  function setCard(id, value, sub) {
    var card = document.getElementById(id);
    if (!card) return;
    card.querySelector('.val').textContent = value;
    if (sub) card.querySelector('.sub').textContent = sub;
  }

  function setText(id, text) {
    var node = document.getElementById(id);
    if (node) node.textContent = text;
  }

  function quiet(promise) {
    return promise.catch(function() { return null; });
  }

  function taskName(key) {
    var m = String(key).match(/^C(\d+)T(\d+)$/i);
    return m ? 'Chapter ' + m[1] + ' Task ' + m[2] : String(key);
  }

  function plural(count, word) {
    return count + ' ' + word + (count === 1 ? '' : 's');
  }

  function renderNext(todo, waitingCount, sentBackCount) {
    var button = document.getElementById('nextBtn');
    if (todo.length) {
      setText('nextLabel', (sentBackCount ? 'Resubmit ' : 'Submit ') + taskName(todo[0]));
      setText('nextNote', sentBackCount
        ? 'Your teacher sent back ' + plural(sentBackCount, 'task') + '. You have ' + todo.length + ' left in total.'
        : 'You have ' + plural(todo.length, 'task') + ' left to submit.');
      if (button) button.href = 'submission.html';
    } else if (waitingCount) {
      setText('nextLabel', 'See my tasks');
      setText('nextNote', 'All submitted. Your teacher is checking ' + plural(waitingCount, 'task') + '.');
    } else {
      setText('nextLabel', 'See my tasks');
      setText('nextNote', 'You\'re all caught up. Nice work.');
    }
  }

  function renderTasks(taskData, submissionData) {
    if (!taskData || !taskData.tasks) {
      setCard('statTasks', '--', 'Could not load');
      setText('nextLabel', 'See my tasks');
      setText('nextNote', 'We couldn\'t load your tasks right now.');
      return;
    }
    var statuses = (submissionData && submissionData.statuses) || {};
    var tasks = EQStudent.fullTasks(taskData.tasks, statuses);
    var keys = Object.keys(tasks);
    var done = keys.filter(function(key) { return tasks[key]; }).length;
    var waiting = {};
    ((submissionData && submissionData.items) || []).forEach(function(item) {
      if (item.status === 'pending' && !tasks[item.task]) waiting[item.task] = true;
    });
    var todo = keys.filter(function(key) { return !tasks[key] && !waiting[key]; });
    var sentBack = todo.filter(function(key) { return statuses[key] === 'rejected'; });
    todo = sentBack.concat(todo.filter(function(key) { return statuses[key] !== 'rejected'; }));
    var waitingCount = Object.keys(waiting).length;
    setCard('statTasks', done + '/' + keys.length, todo.length ? todo.length + ' to do' : 'None to do');
    renderNext(todo, waitingCount, sentBack.length);
  }

  function renderSubmissions(data) {
    if (!data || !data.items) { setCard('statSubs', '--', 'Could not load'); return; }
    var sent = {}, waiting = {};
    data.items.forEach(function(item) {
      sent[item.task] = true;
      if (item.status === 'pending') waiting[item.task] = true;
    });
    var waitingCount = Object.keys(waiting).length;
    setCard('statSubs', String(Object.keys(sent).length), waitingCount ? waitingCount + ' waiting' : 'All checked');
  }

  function renderStrikes(data) {
    if (!data || data.total === undefined) { setCard('statStrikes', '--', 'Could not load'); return; }
    setCard('statStrikes', String(data.total), Number(data.total) ? 'See what to do' : 'All clear');
  }

  function renderScore(data, no) {
    var student = data && data.students && data.students.filter(function(s) {
      return String(s.no) === String(no);
    })[0];
    var score = student && student.calc ? student.calc['Final Score'] : '';
    var hasScore = score !== '' && score !== null && score !== undefined;
    setCard('statScore', hasScore ? String(score) : '--', hasScore ? 'Final score' : 'Not published yet');
  }

  // ---- "What's new": compare today's numbers with the ones this phone saw last time ----
  // XP the teacher gives (vocabulary is left out: students add those words themselves).
  var XP_REASONS = [
    { prop: 'Answering', label: 'Answering', xp: 5 },
    { prop: 'Presenting', label: 'Presenting', xp: 20 },
    { prop: 'HelpingHand', label: 'Helping Hand', xp: 20 },
    { prop: 'DoingTaskOnTime', label: 'Doing Task', xp: 20 },
    { prop: 'Quiz', label: 'Quiz', xp: 10 }
  ];
  var NEWS_KEEP_MS = 3 * 24 * 60 * 60 * 1000; // a new item stays for 3 days, so a quick glance doesn't lose it
  var NEWS_MAX = 6;

  function esc(value) { return EQStudent.escapeHtml(value); }
  function seenKey(classId, studentNo) { return 'eq_home_seen_' + classId + '_' + studentNo; }
  function readSeen(key) {
    try { return JSON.parse(localStorage.getItem(key) || 'null'); } catch (e) { return null; }
  }
  function writeSeen(key, value) {
    try { localStorage.setItem(key, JSON.stringify(value)); } catch (e) {}
  }

  function me(data, studentNo) {
    return data && data.students && data.students.filter(function(s) { return String(s.no) === String(studentNo); })[0];
  }

  // Today's numbers, or null for a part that could not be loaded (that part is then not compared).
  function snapshot(xpData, scoreData, subData, studentNo) {
    var snap = { xp: null, custom: null, scores: null, subs: null };
    var s = me(xpData, studentNo);
    if (s) {
      snap.xp = {};
      XP_REASONS.forEach(function(r) { snap.xp[r.prop] = Number(s[r.prop] || 0); });
      snap.custom = {};
      (s.CustomAwards || []).forEach(function(a) { snap.custom[a.reason + '|' + a.points] = Number(a.count || 0); });
    }
    var sc = me(scoreData, studentNo);
    if (sc && sc.calc) {
      snap.scores = {};
      Object.keys(sc.calc).forEach(function(label) {
        if (/^chapter \d+$/i.test(label) && sc.calc[label] !== '' && sc.calc[label] != null) snap.scores[label] = String(sc.calc[label]);
      });
    }
    if (subData && subData.statuses) snap.subs = Object.assign({}, subData.statuses);
    return snap;
  }

  function changes(before, now) {
    var out = [];
    if (before.xp && now.xp) XP_REASONS.forEach(function(r) {
      var d = now.xp[r.prop] - (before.xp[r.prop] || 0);
      if (d > 0) out.push({ kind: 'xp', text: '+' + d * r.xp + ' XP · ' + r.label + (d > 1 ? ' ×' + d : '') });
    });
    if (before.custom && now.custom) Object.keys(now.custom).forEach(function(key) {
      var d = now.custom[key] - (before.custom[key] || 0);
      var parts = key.split('|'), pts = Number(parts.pop()), reason = parts.join('|');
      if (d > 0) out.push({ kind: 'xp', text: '+' + d * pts + ' XP · ' + reason + (d > 1 ? ' ×' + d : '') });
    });
    if (before.scores && now.scores) Object.keys(now.scores).forEach(function(label) {
      if (before.scores[label] !== now.scores[label]) out.push({ kind: 'score', text: label + ' score: ' + now.scores[label] });
    });
    if (before.subs && now.subs) Object.keys(now.subs).forEach(function(task) {
      if (before.subs[task] === now.subs[task]) return;
      if (now.subs[task] === 'approved') out.push({ kind: 'ok', text: taskName(task) + ' approved' });
      if (now.subs[task] === 'rejected') out.push({ kind: 'back', text: taskName(task) + ' sent back. Please send it again.' });
    });
    return out;
  }

  var NEWS_ICON = { xp: 'zap', score: 'chart-no-axes-column-increasing', ok: 'circle-check-big', back: 'rotate-ccw' };

  function shortDate(ymd) {
    var d = new Date(String(ymd).slice(0, 10) + 'T00:00:00');
    return isNaN(d) ? String(ymd) : d.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short' });
  }

  function recentAttendance(data, studentNo) {
    var s = me(data, studentNo);
    if (!s || !data.meetings) return [];
    return data.meetings.slice().reverse().filter(function(m) {
      return s.attendance && s.attendance['m' + m.number];
    }).slice(0, 3).map(function(m) {
      return { date: m.date, status: s.attendance['m' + m.number] };
    });
  }

  function renderNews(box, news, firstVisit, attendance) {
    if (!box) return;
    var list = news.length
      ? '<ul class="eqh-news-list">' + news.map(function(n) {
          return '<li class="is-' + n.kind + '"><span class="ic"><i data-lucide="' + NEWS_ICON[n.kind] + '"></i></span><span>' + esc(n.text) + '</span></li>';
        }).join('') + '</ul>'
      : '<p class="eqh-news-empty">' + (firstVisit
          ? 'From now on, new XP, scores and checked tasks will show up here.'
          : 'Nothing new since your last visit.') + '</p>';
    var att = attendance.length
      ? '<ul class="eqh-att-list">' + attendance.map(function(a) {
          return '<li><span>' + esc(shortDate(a.date)) + '</span><b class="att-' + esc(String(a.status).toLowerCase()) + '">' + esc(a.status) + '</b></li>';
        }).join('') + '</ul>'
      : '<p class="eqh-news-empty">No attendance yet.</p>';
    box.innerHTML =
      '<div class="eqh-news-col"><h2>What\'s new</h2>' + list + '</div>' +
      '<div class="eqh-news-col"><h2>Recent attendance</h2>' + att + '<a class="eqh-news-link" href="attendance.html">See all attendance →</a></div>';
    box.hidden = false;
    if (global.lucide) global.lucide.createIcons();
  }

  function loadNews(classId, studentNo, xpReq, scoreReq, subReq) {
    var box = document.getElementById('homeNews');
    if (!box) return;
    Promise.all([xpReq, scoreReq, subReq, quiet(EQApi.get({ action: 'getClassData', className: classId }))]).then(function(r) {
      var key = seenKey(classId, studentNo);
      var seen = readSeen(key);
      var now = snapshot(r[0], r[1], r[2], studentNo);
      var at = Date.now();
      var fresh = seen && seen.snap ? changes(seen.snap, now) : [];
      fresh.forEach(function(n) { n.at = at; });
      var kept = ((seen && seen.news) || []).filter(function(n) { return at - n.at < NEWS_KEEP_MS; });
      var news = fresh.concat(kept).slice(0, NEWS_MAX);
      // Keep the old numbers for any part that failed to load this time.
      var prev = (seen && seen.snap) || {};
      ['xp', 'custom', 'scores', 'subs'].forEach(function(part) { if (now[part] === null) now[part] = prev[part] || null; });
      writeSeen(key, { snap: now, news: news });
      renderNews(box, news, !seen, recentAttendance(r[3], studentNo));
    });
  }

  function load(classId, studentNo, hasProfile) {
    var main = document.querySelector('.eqh-main');
    if (main) main.classList.toggle('is-guest', !hasProfile);
    var newsBox = document.getElementById('homeNews');
    if (newsBox && !hasProfile) newsBox.hidden = true;
    if (!hasProfile || !global.EQApi) return;

    var subReq = quiet(EQApi.get({ action: 'getMySubmissions', className: classId, studentNo: studentNo }));
    var scoreReq = quiet(EQApi.get({ action: 'getFinalScores', className: classId }));
    var xpReq = quiet(EQApi.get({ action: 'getAllActiveness', className: classId }));
    Promise.all([
      quiet(EQApi.get({ action: 'getTaskStatus', className: classId, studentNo: studentNo })),
      subReq
    ]).then(function(results) {
      renderTasks(results[0], results[1]);
      renderSubmissions(results[1]);
    });
    quiet(EQApi.get({ action: 'getStudentStrikes', className: classId, studentNo: studentNo })).then(renderStrikes);
    scoreReq.then(function(data) {
      renderScore(data, studentNo);
    });
    loadNews(classId, studentNo, xpReq, scoreReq, subReq);
  }

  global.EQHome = { load: load };
})(window);
