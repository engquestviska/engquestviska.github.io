/* EngQuest Chronicle story: builds each slide from the view model (chronicle-data.js) and plays
   them like a phone story. Exposes window.EQChronicleStory. */
(function (global) {
  var DUR = 4500;

  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }
  function mast(topic) { return '<div class="mast"><i>EngQuest Chronicle</i><span>' + esc(topic) + '</span></div>'; }
  function upper(s) { return esc(String(s || '').toUpperCase()); }

  var SLIDES = {
    cover: function (s) {
      return '<div class="ph" data-kind="paper">' +
        '<div class="mast"><i>EngQuest</i><span>NO. 01</span></div>' +
        '<div class="blob grow" style="width:280px;height:280px;right:-120px;top:150px"></div>' +
        '<div style="position:absolute;left:14px;right:0;top:58px">' +
        '<div class="ch num" style="font-size:48px;font-style:italic;line-height:.9;white-space:nowrap">' +
        'Chronicle'.split('').map(function (c, i) { return '<span style="--i:' + i + '">' + c + '</span>'; }).join('') + '</div>' +
        '<div class="rule" style="margin:14px 16px 8px 2px"></div>' +
        '<div class="k r" style="--d:.7s;padding-left:2px">' + upper(s.label) + ' · ' + upper(s.gradeLabel) + '</div></div>' +
        '<div style="position:absolute;left:16px;right:16px;bottom:58px;z-index:2">' +
        '<div class="k r" style="--d:.9s">STARRING</div>' +
        '<div class="num float fitname" data-max="84" style="--d:1s;font-size:84px">' + esc(s.name) + '</div></div>' +
        '<div class="r" style="--d:1.3s;position:absolute;left:16px;right:16px;bottom:20px;display:flex;justify-content:space-between;font-size:12px;font-weight:800"><span>' + esc(s.classLabel) + '</span><span>Tap to begin →</span></div>' +
        '</div>';
    },
    attendance: function (s) {
      var dots = s.days.map(function (d, i) {
        return '<i class="dot ' + (d === 's' ? 's' : d === 'a' ? 'a' : d === '' ? 'u' : '') + '" style="--i:' + (i * 0.5) + '"></i>';
      }).join('');
      return '<div class="ph" data-kind="paper">' + mast('ATTENDANCE') +
        '<div class="num sl ac" style="position:absolute;right:-6px;top:76px;font-size:150px">' + s.present + '</div>' +
        '<div style="position:absolute;left:16px;right:16px;top:250px">' +
        '<div class="lead r" style="--d:.3s">days you showed up,<br>out of ' + s.meetings + '.</div>' +
        '<div class="dots" style="margin-top:14px">' + dots + '</div>' +
        '<div class="r" style="--d:.6s;font-size:11px;font-weight:800;margin-top:9px;display:flex;gap:12px"><span>● present</span><span class="ac">● sick / permit</span><span>○ absent</span></div></div>' +
        '<div class="sticker" style="left:16px;top:70px;--d:1.4s">' + esc(s.percent) + '</div></div>';
    },
    tasks: function (s) {
      var n = Math.min(s.done, 35), bestAt = Math.floor(n * 0.7), tiles = '';
      for (var i = 0; i < n; i++) tiles += '<i class="tile' + (s.best && i === bestAt ? ' best' : '') + '" style="--i:' + i + '"></i>';
      return '<div class="ph" data-kind="paper">' + mast('TASKS') +
        '<div class="num sl" style="position:absolute;left:8px;top:76px;font-size:150px">' + s.done + '</div>' +
        '<div style="position:absolute;left:16px;right:16px;top:222px">' +
        '<div class="lead r" style="--d:.3s">' + (s.done === 1 ? 'task finished.' : 'tasks finished.') + ' Every one counted.</div>' +
        '<div class="tiles" style="margin-top:14px">' + tiles + '</div>' +
        (s.best ? '<div class="rule" style="margin-top:14px"></div>' +
          '<div class="r" style="--d:2s;display:flex;justify-content:space-between;align-items:baseline;gap:8px;margin-top:8px"><span class="k" style="opacity:1;flex:none">YOUR BEST</span><span style="font-weight:800;font-size:12px;text-align:right">' + esc(s.best.label) + ' · <span class="ac">' + esc(s.best.score) + '</span></span></div>' : '') +
        '</div></div>';
    },
    vocab: function (s) {
      var words = s.words.slice();
      while (words.length && words.length < 18) words = words.concat(s.words);
      var rows = '';
      for (var r = 0; r < 5; r++) {
        var ws = words.slice(r * 3).concat(words.slice(0, r * 3));
        var spans = ws.map(function (w, i) {
          return '<span' + ((i + r) % 6 === 0 ? ' style="color:color-mix(in srgb,var(--a) 55%,#fff)"' : '') + '>' + esc(w) + '</span>';
        }).join('');
        rows += '<div class="mqrow' + (r % 2 ? ' rev' : '') + '" style="--t:' + (22 + r * 4) + 's">' + spans + spans + '</div>';
      }
      return '<div class="ph dark" data-kind="dark">' + mast('VOCABULARY') +
        '<div style="position:absolute;left:0;right:0;top:64px;font-family:var(--disp);font-style:italic;font-weight:700;font-size:26px;line-height:1.35;opacity:.9">' + rows + '</div>' +
        '<div style="position:absolute;left:0;right:0;bottom:0;height:178px;background:var(--paper);color:var(--ink);padding:14px 16px">' +
        '<div style="display:flex;align-items:flex-end;gap:10px"><div class="num count" data-to="' + s.total + '" style="font-size:86px">' + s.total + '</div>' +
        '<div class="lead r" style="--d:.4s;padding-bottom:4px">' + (s.total === 1 ? 'word' : 'words') + '<br>collected</div></div>' +
        '<div class="rule" style="margin:12px 0 8px"></div>' +
        '<div class="r" style="--d:.7s;font-size:13px;display:flex;justify-content:space-between;gap:8px"><span>Latest: <span class="it ac">' + esc(s.latest) + '</span></span>' +
        (s.classTop5 ? '<span class="k" style="opacity:1">TOP 5 IN CLASS</span>' : '') + '</div></div></div>';
    },
    level: function (s) {
      return '<div class="ph" data-kind="paper">' + mast('LEVEL') +
        '<div class="k r" style="position:absolute;left:16px;top:66px">YOU REACHED</div>' +
        '<div class="num sl ac" style="position:absolute;right:-8px;top:96px;font-size:160px;--d:.1s">' + s.level + '</div>' +
        '<div style="position:absolute;left:16px;right:16px;bottom:30px">' +
        '<div class="lead r" style="--d:.4s">Level ' + s.level + '.<br><span class="count" data-to="' + s.xp + '" data-dur="2600">' + s.xp.toLocaleString('en-US') + '</span> XP earned.</div>' +
        '<div style="position:relative;height:26px;margin-top:16px;border:1.5px solid var(--ink)">' +
        '<div class="fillbar" style="position:absolute;left:0;top:0;bottom:0;width:' + Math.round(s.progress * 100) + '%;background:var(--ink)"></div>' +
        '<div style="position:absolute;inset:0;background:repeating-linear-gradient(90deg,transparent 0 9px,rgba(128,128,128,.6) 9px 10px)"></div></div>' +
        '<div style="display:flex;justify-content:space-between;font-size:11px;font-weight:800;margin-top:6px"><span>LV ' + s.level + '</span><span>' + s.toNext + ' XP to LV ' + (s.level + 1) + '</span></div></div></div>';
    },
    final: function (s) {
      var rows = s.units.filter(function (u) { return u.score !== '' && u.score != null; })
        .map(function (u) { return { label: u.label, score: u.score }; });
      if (s.asat !== '' && s.asat != null) rows.push({ label: 'ASAT', score: s.asat });
      return '<div class="ph dark" data-kind="dark">' + mast('FINAL SCORE') +
        '<div class="k r" style="position:absolute;left:16px;top:72px">YOUR FINAL SCORE</div>' +
        '<div class="num count" data-to="' + s.final + '" style="position:absolute;left:12px;top:96px;font-size:140px">' + s.final + '</div>' +
        '<div style="position:absolute;left:16px;right:16px;top:244px">' +
        '<div style="position:relative;height:40px">' +
        '<div style="position:absolute;left:0;right:0;top:19px;height:2px;background:currentColor;opacity:.4"></div>' +
        '<div style="position:absolute;left:0;right:0;top:14px;height:12px;background:repeating-linear-gradient(90deg,currentColor 0 1px,transparent 1px 10%);opacity:.5"></div>' +
        '<div class="pin" style="position:absolute;left:' + Math.max(0, Math.min(100, s.final)) + '%;top:6px;width:16px;height:28px;margin-left:-8px;background:var(--a);border-radius:3px"></div></div>' +
        '<div style="display:flex;justify-content:space-between;font-size:11px;font-weight:800;opacity:.7"><span>0</span><span>50</span><span>100</span></div>' +
        '<div class="rule" style="margin:16px 0 8px"></div>' +
        '<div class="r" style="--d:1.6s;font-size:12px;line-height:1.55">' +
        rows.map(function (u) { return '<div style="display:flex;justify-content:space-between;gap:8px"><span>' + esc(u.label) + '</span><b>' + esc(u.score) + '</b></div>'; }).join('') +
        '</div></div></div>';
    },
    rank: function (s) {
      return '<div class="ph blue" data-kind="blue">' + mast('CLASS RANK') +
        '<div class="k r" style="position:absolute;left:16px;top:72px;opacity:1">IN ' + upper(s.classLabel) + ' YOU FINISHED</div>' +
        '<div class="num" style="position:absolute;left:4px;bottom:-4px;font-size:' + s.fontSize + 'px;line-height:.9"><span style="font-size:.5em;vertical-align:.7em">#</span><span class="count" data-from="' + s.of + '" data-to="' + s.place + '">' + s.place + '</span></div>' +
        '<div class="r" style="--d:1.8s;position:absolute;right:16px;top:110px;text-align:right;font-weight:800;font-size:15px;line-height:1.3">out of<br><span class="num" style="font-size:44px">' + s.of + '</span></div></div>';
    },
    gradeRank: function (s) {
      return '<div class="ph dark" data-kind="dark">' + mast('GRADE RANK') +
        '<div class="k r" style="position:absolute;left:16px;top:72px;opacity:1">ACROSS ALL OF ' + upper(s.gradeLabel) + '</div>' +
        '<div class="r" style="--d:.3s;position:absolute;left:16px;right:16px;top:96px;display:flex;gap:6px;flex-wrap:wrap;font-size:11px;font-weight:800">' +
        s.classes.map(function (c) { return '<span class="chip' + (c.mine ? ' mine' : '') + '">' + esc(c.label) + '</span>'; }).join('') + '</div>' +
        '<div class="num ac" style="position:absolute;left:6px;bottom:52px;font-size:' + s.fontSize + 'px;line-height:.9"><span style="font-size:.5em;vertical-align:.7em">#</span><span class="count" data-from="' + s.of + '" data-to="' + s.place + '" data-dur="2400">' + s.place + '</span></div>' +
        '<div class="r" style="--d:2.2s;position:absolute;left:16px;right:16px;bottom:22px;font-weight:800;font-size:14px">out of ' + s.of + ' students in ' + esc(s.gradeLabel) + '</div></div>';
    }
  };

  function cardHtml(c) {
    var stat = function (v, k, first) {
      return '<div' + (first ? '' : ' style="border-left:1px solid var(--line)"') + '><div class="num" style="font-size:22px">' + esc(v) + '</div><div class="k" style="font-size:8px;margin-top:3px">' + k + '</div></div>';
    };
    return '<div class="ph" data-kind="paper"><div class="shine"></div>' +
      '<div class="mast"><i>EngQuest Chronicle</i><span>' + upper(c.label) + '</span></div>' +
      '<div style="position:absolute;left:16px;right:16px;top:64px"><div class="k">STARRING</div><div class="num fitname" data-max="58" style="font-size:58px;margin-top:4px">' + esc(c.name) + '</div><div class="k" style="margin-top:6px">' + upper(c.classLine) + '</div></div>' +
      '<div class="k" style="position:absolute;left:16px;top:168px;opacity:1">CLASS RANK</div>' +
      '<div class="num ac" style="position:absolute;left:-4px;top:184px;font-size:' + Math.round(c.rankFontSize * 0.9) + 'px;line-height:1">' +
      (c.rank === '—' ? '—' : '<span style="font-size:.45em;vertical-align:.75em">#</span>' + esc(c.rank.slice(1))) + '</div>' +
      '<div style="position:absolute;right:16px;top:200px;text-align:right"><div class="k">FINAL</div><div class="num" style="font-size:46px">' + esc(c.final) + '</div></div>' +
      (c.gradeRank ? '<div style="position:absolute;left:16px;right:16px;top:352px;font-size:13px;font-weight:800">' + esc(c.gradeRank.split(' ')[0]) + ' <span style="opacity:.6">' + esc(c.gradeRank.split(' ').slice(1).join(' ')) + '</span></div>' : '') +
      '<div style="position:absolute;left:16px;right:16px;bottom:18px;border-top:1.5px solid var(--ink);padding-top:8px;display:grid;grid-template-columns:repeat(4,1fr);text-align:center">' +
      stat(c.level, 'LEVEL', true) + stat(c.present, 'PRESENT') + stat(c.tasks, 'TASKS') + stat(c.words, 'WORDS') + '</div></div>';
  }

  // Shrink a long name until it fits two lines of its box (never below 28px).
  function fitNames(root) {
    root.querySelectorAll('.fitname').forEach(function (el) {
      var size = +el.getAttribute('data-max') || 60;
      el.style.fontSize = size + 'px';
      var limit = function () { return el.scrollHeight > size * 0.95 * 2 + 2 || el.scrollWidth > el.clientWidth + 1; };
      while (size > 28 && limit()) { size -= 2; el.style.fontSize = size + 'px'; }
    });
  }

  function counts(root) {
    root.querySelectorAll('.count').forEach(function (el) {
      var to = +el.getAttribute('data-to'), hasFrom = el.hasAttribute('data-from');
      var from = hasFrom ? +el.getAttribute('data-from') : 0;
      var d = +(el.getAttribute('data-dur') || (hasFrom ? 1800 : 1400)), t0 = performance.now();
      (function f(t) {
        var p = Math.min((t - t0) / d, 1), e = 1 - Math.pow(1 - p, 3);
        el.textContent = Math.round(from + (to - from) * e).toLocaleString('en-US');
        if (p < 1) requestAnimationFrame(f);
      })(t0);
    });
  }

  function play(el) { el.classList.remove('play'); void el.offsetWidth; el.classList.add('play'); counts(el); }

  function toNode(html) { var t = document.createElement('template'); t.innerHTML = html.trim(); return t.content.firstChild; }

  function render(container, vm) {
    container.innerHTML = '';
    container.style.setProperty('--a', vm.accent);
    var frame = document.createElement('div');
    frame.className = 'cq-frame';
    var bars = document.createElement('div');
    bars.className = 'cq-pbars';
    var close = document.createElement('button');
    close.className = 'cq-close';
    close.type = 'button';
    close.textContent = 'CLOSE';
    frame.appendChild(bars);
    container.appendChild(close);
    var slides = vm.slides.map(function (s) { return toNode(SLIDES[s.id](s)); });
    slides.push(toNode(cardHtml(vm.card)));
    slides.forEach(function (s) { frame.appendChild(s); });
    container.appendChild(frame);
    bars.innerHTML = slides.map(function () { return '<i><b></b></i>'; }).join('');
    var fills = [].slice.call(bars.querySelectorAll('b'));
    fitNames(frame);

    var cur = -1, t0 = 0, elapsed = 0, paused = false, raf = 0, done = null, finished = false;
    function fit() {
      var s = Math.min(window.innerWidth / 250, window.innerHeight / 444);
      frame.style.transform = 'scale(' + s + ')';
    }
    function kind(el) { return el.getAttribute('data-kind'); }
    function show(i) {
      if (i >= slides.length) { finish(); return; }
      i = Math.max(0, i);
      var prev = slides[cur], next = slides[i];
      cur = i;
      slides.forEach(function (s) { s.classList.remove('on', 'leaving', 'push', 'wipe'); });
      if (prev && prev !== next) {
        prev.classList.add('leaving');
        setTimeout(function () { prev.classList.remove('leaving'); }, 800);
      }
      next.classList.add('on', prev && prev !== next && kind(prev) !== kind(next) ? 'wipe' : 'push');
      play(next);
      bars.classList.toggle('lt', kind(next) !== 'paper');
      fills.forEach(function (f, j) { f.style.width = j < cur ? '100%' : '0'; });
      elapsed = 0; t0 = performance.now();
    }
    function finish() {
      if (finished) return;
      finished = true;
      cancelAnimationFrame(raf);
      if (done) done();
    }
    function tick(t) {
      if (!paused) {
        elapsed = t - t0;
        fills[cur].style.width = Math.min(elapsed / DUR * 100, 100) + '%';
        // The share card is the last slide: it waits for a tap instead of moving on.
        if (elapsed >= DUR && cur < slides.length - 1) show(cur + 1);
      } else t0 = t - elapsed;
      raf = requestAnimationFrame(tick);
    }
    function onTap(e) {
      if (e.target === close) { finish(); return; }
      var r = frame.getBoundingClientRect();
      if (e.clientX - r.left < r.width / 3) show(cur - 1); else show(cur + 1);
    }
    function onVis() { paused = document.hidden; }
    function onKey(e) {
      if (e.key === 'ArrowRight' || e.key === ' ') show(cur + 1);
      else if (e.key === 'ArrowLeft') show(cur - 1);
      else if (e.key === 'Escape') finish();
    }
    container.addEventListener('click', onTap);
    window.addEventListener('resize', fit);
    document.addEventListener('visibilitychange', onVis);
    document.addEventListener('keydown', onKey);
    fit();

    return {
      play: function () { finished = false; show(0); cancelAnimationFrame(raf); raf = requestAnimationFrame(tick); },
      onDone: function (fn) { done = fn; },
      destroy: function () {
        cancelAnimationFrame(raf);
        container.removeEventListener('click', onTap);
        window.removeEventListener('resize', fit);
        document.removeEventListener('visibilitychange', onVis);
        document.removeEventListener('keydown', onKey);
        container.innerHTML = '';
      }
    };
  }

  global.EQChronicleStory = { render: render, cardHtml: cardHtml, fitNames: fitNames, counts: counts, play: play, esc: esc };
})(window);
