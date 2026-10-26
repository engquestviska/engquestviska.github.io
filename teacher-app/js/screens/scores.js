/* Scores: pick a chapter and a column, then type scores down the list. Each score saves on its own. */
(function () {
  // UI chapter n = backend chapter n+3 (as in teacher/scores.html).
  var CHAPTERS = [
    { id: '4', label: 'Ch 1' }, { id: '5', label: 'Ch 2' }, { id: '6', label: 'Ch 3' }, { id: 'final', label: 'Final' }
  ];
  var st = null;

  function pref(key, value) {
    try {
      if (value === undefined) return localStorage.getItem(key);
      localStorage.setItem(key, value);
    } catch (e) { /* storage off */ }
    return null;
  }

  function load() {
    var ctx = st.ctx;
    st.list.innerHTML = '<div class="state"><div class="spinner"></div>Loading scores…</div>';
    st.cols.innerHTML = '';
    var token = st.token = {};
    var params = st.chapter === 'final'
      ? { action: 'getFinalScores', className: ctx.className }
      : { action: 'getChapterScores', className: ctx.className, chapter: st.chapter };
    ctx.session.read(params).then(function (data) {
      if (token !== st.token) return;
      st.students = data.students || [];
      st.inputCols = data.inputCols || [];
      st.calcCols = data.calcCols || [];
      var saved = pref('eqt_score_col_' + st.chapter);
      st.col = st.inputCols.indexOf(saved) > -1 ? saved : st.inputCols[0];
      drawCols();
      drawList();
    }).catch(function (err) {
      if (token !== st.token) return;
      st.list.innerHTML = '<div class="state"><b>Could not load scores</b>' + ctx.esc(err.message) +
        '<br><button class="btn brand" type="button" id="scRetry">Try again</button></div>';
      st.list.querySelector('#scRetry').onclick = load;
    });
  }

  function chapterLabel() {
    return st.chapter === 'final' ? 'Final scores' : 'Chapter ' + (Number(st.chapter) - 3);
  }

  function drawChips() {
    var esc = st.ctx.esc;
    st.chips.innerHTML = CHAPTERS.map(function (c) {
      return '<button type="button" class="chip' + (c.id === st.chapter ? ' on' : '') + '" data-ch="' + c.id + '">' + esc(c.label) + '</button>';
    }).join('');
  }

  function drawCols() {
    var esc = st.ctx.esc;
    st.ctx.setSubtitle(chapterLabel() + (st.col ? ' · ' + st.col : ''));
    st.cols.innerHTML = st.inputCols.length > 1 ? st.inputCols.map(function (c) {
      return '<button type="button" class="' + (c === st.col ? 'on' : '') + '" data-col="' + esc(c) + '">' + esc(short(c)) + '</button>';
    }).join('') : '';
    st.cols.hidden = st.inputCols.length < 2;
  }

  function short(col) {
    return col.replace(/^Formative$/, 'Form.').replace(/^Summative$/, 'Summ.');
  }

  function value(s, col) {
    var v = s.scores ? s.scores[col] : '';
    return v == null ? '' : String(v);
  }

  // Plain average of the entered inputs (owner decision D3), used after a save.
  function average(s) {
    if (st.chapter === 'final') {
      var c = s.calc || {};
      var key = st.calcCols[0];
      return key && c[key] !== undefined && c[key] !== '' ? c[key] : '';
    }
    var nums = st.inputCols.map(function (c) { return value(s, c); }).filter(function (v) { return v !== '' && !isNaN(Number(v)); }).map(Number);
    if (!nums.length) return '';
    return Math.round(nums.reduce(function (a, b) { return a + b; }, 0) / nums.length * 10) / 10;
  }

  function drawList() {
    var ctx = st.ctx, esc = ctx.esc;
    if (!st.students.length) {
      st.list.innerHTML = '<div class="state"><b>No students in ' + esc(ctx.classLabel) + '</b>Pick another class from the top.</div>';
      return;
    }
    if (!st.col) {
      st.list.innerHTML = '<div class="state"><b>Nothing to enter here</b>This tab has no score columns.</div>';
      return;
    }
    var avgLabel = st.chapter === 'final' ? (st.calcCols[0] || 'Avg') : 'avg';
    st.list.innerHTML = '<div class="sc-status" id="scStatus">Type a score, then tap Next. Each score saves by itself.</div>' +
      '<div class="sc-list">' + st.students.map(function (s, i) {
        var v = value(s, st.col), a = average(s);
        return '<div class="sc-row" data-no="' + esc(s.no) + '">' +
          '<span class="no">' + esc(s.no) + '</span>' +
          '<span class="nm">' + esc(s.nickname || s.name) + '</span>' +
          '<input class="sc-in' + (ctx.core.isLowScore(v) ? ' low' : '') + '" data-i="' + i + '" inputmode="decimal" enterkeyhint="' + (i === st.students.length - 1 ? 'done' : 'next') + '" autocomplete="off" value="' + esc(v) + '" placeholder="–" aria-label="' + esc(st.col + ' for ' + s.name) + '">' +
          '<span class="avg">' + esc(avgLabel) + '<b>' + (a === '' ? '–' : esc(a)) + '</b></span></div>';
      }).join('') + '</div>';
    var inputs = st.list.querySelectorAll('.sc-in');
    inputs.forEach(function (input) {
      input.addEventListener('keydown', function (e) {
        if (e.key !== 'Enter') return;
        e.preventDefault();
        var next = inputs[Number(input.getAttribute('data-i')) + 1];
        if (next) { next.focus(); next.select(); } else input.blur();
      });
      input.addEventListener('change', function () { save(input); });
      // A score that failed to save is retried when the box is left again, even if unchanged.
      input.addEventListener('blur', function () {
        if (input.classList.contains('unsaved')) save(input);
      });
      input.addEventListener('input', function () {
        input.classList.toggle('bad', !ctx.core.validateScore(input.value).ok);
      });
    });
  }

  function status(text, type) {
    var el = st.list.querySelector('#scStatus'); if (!el) return;
    el.textContent = text;
    el.className = 'sc-status' + (type ? ' ' + type : '');
  }

  function save(input) {
    var ctx = st.ctx;
    var s = st.students[Number(input.getAttribute('data-i'))]; if (!s) return;
    var col = st.col, chapter = st.chapter, token = st.token;
    var check = ctx.core.validateScore(input.value);
    if (!check.ok) {
      input.classList.add('bad');
      ctx.toast(check.error + ' · ' + ctx.core.firstName(s.name), 'err');
      return;
    }
    input.value = check.value;
    if (check.value === value(s, col)) { input.classList.remove('bad', 'unsaved'); return; }
    if (input.classList.contains('saving')) return;
    input.classList.remove('bad', 'saved', 'unsaved'); input.classList.add('saving');
    status('Saving ' + ctx.core.firstName(s.name) + '…');
    var params = { action: chapter === 'final' ? 'saveFinalScore' : 'saveChapterScore', className: ctx.className, studentNo: s.no, column: col, value: check.value };
    if (chapter !== 'final') params.chapter = chapter;
    ctx.session.call(params).then(function (res) {
      if (token !== st.token) return;
      s.scores = s.scores || {};
      s.scores[col] = check.value;
      if (chapter === 'final' && res.calc) s.calc = Object.assign(s.calc || {}, res.calc);
      input.classList.remove('saving');
      input.classList.toggle('low', ctx.core.isLowScore(check.value));
      input.classList.add('saved');
      setTimeout(function () { input.classList.remove('saved'); }, 900);
      var a = average(s);
      var row = input.closest('.sc-row');
      if (row) row.querySelector('.avg b').textContent = a === '' ? '–' : a;
      status('Saved ✓ ' + ctx.core.firstName(s.name) + ' · ' + col + (check.value === '' ? ' cleared' : ' = ' + check.value), 'ok');
    }).catch(function (err) {
      if (token !== st.token) return;
      input.classList.remove('saving'); input.classList.add('bad', 'unsaved');
      status('Not saved: ' + err.message + '. Tap the red box, then Next, to try again.', 'err');
      if (err.message !== 'Session expired') ctx.toast('Not saved for ' + ctx.core.firstName(s.name), 'err');
    });
  }

  EQT.registerScreen('scores', {
    title: 'Scores',
    render: function (el, ctx) {
      var ch = pref('eqt_score_ch');
      el.innerHTML = '<div class="pickers" id="scChips"></div><div class="colseg" id="scCols"></div><div id="scList"></div>';
      st = {
        ctx: ctx, token: {},
        chapter: CHAPTERS.some(function (c) { return c.id === ch; }) ? ch : '4',
        chips: el.querySelector('#scChips'), cols: el.querySelector('#scCols'), list: el.querySelector('#scList'),
        students: [], inputCols: [], calcCols: []
      };
      drawChips();
      st.chips.onclick = function (e) {
        var b = e.target.closest('[data-ch]'); if (!b) return;
        if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
        st.chapter = b.getAttribute('data-ch');
        pref('eqt_score_ch', st.chapter);
        drawChips(); load();
      };
      st.cols.onclick = function (e) {
        var b = e.target.closest('[data-col]'); if (!b) return;
        if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
        st.col = b.getAttribute('data-col');
        pref('eqt_score_col_' + st.chapter, st.col);
        drawCols(); drawList();
      };
      load();
    },
    leave: function () {
      // Leaving the box fires "change", which saves; nothing is lost by switching.
      if (document.activeElement && document.activeElement.blur) document.activeElement.blur();
      if (st) st.token = {};
      return true;
    }
  });
})();
