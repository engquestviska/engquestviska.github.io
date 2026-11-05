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

  function renderNext(todo, waitingCount) {
    var button = document.getElementById('nextBtn');
    if (todo.length) {
      setText('nextLabel', 'Submit ' + taskName(todo[0]));
      setText('nextNote', 'You have ' + plural(todo.length, 'task') + ' left to submit.');
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
    var tasks = taskData.tasks;
    var keys = Object.keys(tasks);
    var done = keys.filter(function(key) { return tasks[key]; }).length;
    var waiting = {};
    ((submissionData && submissionData.items) || []).forEach(function(item) {
      if (item.status === 'pending' && !tasks[item.task]) waiting[item.task] = true;
    });
    var todo = keys.filter(function(key) { return !tasks[key] && !waiting[key]; });
    var waitingCount = Object.keys(waiting).length;
    setCard('statTasks', done + '/' + keys.length, todo.length ? todo.length + ' to do' : 'None to do');
    renderNext(todo, waitingCount);
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

  function load(classId, studentNo, hasProfile) {
    var main = document.querySelector('.eqh-main');
    if (main) main.classList.toggle('is-guest', !hasProfile);
    if (!hasProfile || !global.EQApi) return;

    Promise.all([
      quiet(EQApi.get({ action: 'getTaskStatus', className: classId, studentNo: studentNo })),
      quiet(EQApi.get({ action: 'getMySubmissions', className: classId, studentNo: studentNo }))
    ]).then(function(results) {
      renderTasks(results[0], results[1]);
      renderSubmissions(results[1]);
    });
    quiet(EQApi.get({ action: 'getStudentStrikes', className: classId, studentNo: studentNo })).then(renderStrikes);
    quiet(EQApi.get({ action: 'getFinalScores', className: classId })).then(function(data) {
      renderScore(data, studentNo);
    });
  }

  global.EQHome = { load: load };
})(window);
