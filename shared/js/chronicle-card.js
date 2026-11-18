/* EngQuest Chronicle share picture: draws the share card on a canvas (1080x1920 story image,
   720x1280 Instagram sticker) and hands it to the app, the phone's share sheet, or a download. */
(function (global) {
  var W = 250, H = 444;
  var PAPER = '#F4F2ED', INK = '#141414';
  var DISP = '"Playfair Display", Georgia, serif', HEAD = '"Bricolage Grotesque", system-ui, sans-serif';

  function app() { return global.EngQuestApp || null; }

  function font(ctx, style, size, family) { ctx.font = style + ' ' + size + 'px ' + family; }
  function spaced(ctx, px) { if ('letterSpacing' in ctx) ctx.letterSpacing = px + 'px'; }

  // Lays the name out so it always fits `width` (rules in EQChronicleData.fitNameLines).
  function fitLines(ctx, text, width, max) {
    var fit = global.EQChronicleData.fitNameLines(text, width, max, function (t, size) {
      font(ctx, '800', size, DISP);
      return ctx.measureText(t).width;
    });
    font(ctx, '800', fit.size, DISP);
    return fit;
  }

  // Draws the card in 250x444 design units; `k` scales to the real pixel size.
  function draw(ctx, c, accent, k) {
    ctx.save();
    ctx.scale(k, k);
    ctx.fillStyle = PAPER; ctx.fillRect(0, 0, W, H);
    ctx.fillStyle = INK; ctx.strokeStyle = INK; ctx.textBaseline = 'alphabetic';

    // masthead
    ctx.fillRect(16, 22, 218, 1.5);
    ctx.fillRect(16, 43, 218, 1);
    font(ctx, 'italic 700', 13, DISP); spaced(ctx, 0); ctx.textAlign = 'left';
    ctx.fillText('EngQuest Chronicle', 16, 38);
    font(ctx, '800', 10, HEAD); spaced(ctx, 1); ctx.textAlign = 'right';
    ctx.fillText(String(c.label || '').toUpperCase(), 234, 37);

    // name
    ctx.textAlign = 'left';
    ctx.globalAlpha = 0.65; font(ctx, '800', 10, HEAD); spaced(ctx, 1.2);
    ctx.fillText('STARRING', 16, 74);
    ctx.globalAlpha = 1; spaced(ctx, 0);
    var fit = fitLines(ctx, c.name, 218, 58);
    var y = 78;
    fit.lines.forEach(function (line) { y += fit.size * 0.86; ctx.fillText(line, 16, y); });
    ctx.globalAlpha = 0.65; font(ctx, '800', 10, HEAD); spaced(ctx, 1.2);
    ctx.fillText(String(c.classLine).toUpperCase(), 16, y + 16);
    ctx.globalAlpha = 1;

    // class rank + final
    var rankTop = Math.max(168, y + 32);
    ctx.fillText('CLASS RANK', 16, rankTop + 9);
    ctx.fillStyle = accent; spaced(ctx, 0);
    var big = Math.round((c.rankFontSize || 170) * 0.9);
    if (c.rank === '—') { font(ctx, '800', big * 0.6, DISP); ctx.fillText('—', 12, rankTop + 12 + big * 0.62); }
    else {
      font(ctx, '800', big * 0.45, DISP); ctx.fillText('#', 0, rankTop + 12 + big * 0.42);
      var hashW = ctx.measureText('#').width;
      font(ctx, '800', big, DISP); ctx.fillText(c.rank.slice(1), hashW, rankTop + 12 + big * 0.74);
    }
    ctx.fillStyle = INK; ctx.textAlign = 'right';
    ctx.globalAlpha = 0.65; font(ctx, '800', 10, HEAD); spaced(ctx, 1.2);
    ctx.fillText('FINAL', 234, rankTop + 41);
    ctx.globalAlpha = 1; font(ctx, '800', 46, DISP); spaced(ctx, 0);
    ctx.fillText(c.final, 234, rankTop + 80);

    // grade rank line
    if (c.gradeRank) {
      var parts = c.gradeRank.split(' ');
      ctx.textAlign = 'left'; font(ctx, '800', 13, HEAD); spaced(ctx, 0);
      ctx.fillText(parts[0] + ' ', 16, 365);
      var w0 = ctx.measureText(parts[0] + ' ').width;
      ctx.globalAlpha = 0.6; ctx.fillText(parts.slice(1).join(' '), 16 + w0, 365); ctx.globalAlpha = 1;
    }

    // bottom strip
    ctx.fillRect(16, 382, 218, 1.5);
    var cells = [[c.level, 'LEVEL'], [c.present, 'PRESENT'], [c.tasks, 'TASKS'], [c.words, 'WORDS']];
    ctx.textAlign = 'center';
    cells.forEach(function (cell, i) {
      var cx = 16 + 218 / 8 + i * 218 / 4;
      if (i) { ctx.globalAlpha = 0.25; ctx.fillRect(16 + i * 218 / 4, 390, 1, 36); ctx.globalAlpha = 1; }
      font(ctx, '800', 22, DISP); spaced(ctx, 0); ctx.fillText(String(cell[0]), cx, 410);
      ctx.globalAlpha = 0.65; font(ctx, '800', 8, HEAD); spaced(ctx, 1); ctx.fillText(cell[1], cx, 424); ctx.globalAlpha = 1;
    });
    ctx.restore();
  }

  function canvasBlob(canvas) {
    return new Promise(function (res, rej) { canvas.toBlob(function (b) { b ? res(b) : rej(new Error('no image')); }, 'image/png'); });
  }

  function ready() { return document.fonts && document.fonts.ready ? document.fonts.ready : Promise.resolve(); }

  function toBlob(card, opts) {
    return ready().then(function () {
      var cv = document.createElement('canvas');
      cv.width = 1080; cv.height = 1920;
      draw(cv.getContext('2d'), card, (opts && opts.accent) || '#2450E0', 1080 / W);
      return canvasBlob(cv);
    });
  }

  // Instagram sticker: same card, rounded corners, transparent outside.
  function stickerBlob(card, opts) {
    return ready().then(function () {
      var cv = document.createElement('canvas');
      cv.width = 720; cv.height = 1280;
      var ctx = cv.getContext('2d'), k = 720 / W, r = 24 * k;
      ctx.beginPath();
      if (ctx.roundRect) ctx.roundRect(0, 0, 720, 1280, r); else ctx.rect(0, 0, 720, 1280);
      ctx.clip();
      draw(ctx, card, (opts && opts.accent) || '#2450E0', k);
      return canvasBlob(cv);
    });
  }

  function base64(blob) {
    return new Promise(function (res, rej) {
      var fr = new FileReader();
      fr.onload = function () { res(String(fr.result).split(',')[1]); };
      fr.onerror = rej;
      fr.readAsDataURL(blob);
    });
  }

  function fileName(vm) { return 'EngQuest-Chronicle-' + String(vm.name || 'me').replace(/[^A-Za-z0-9]+/g, '-') + '.png'; }

  function webShareFiles() {
    try {
      return !!(navigator.canShare && navigator.canShare({ files: [new File([''], 'x.png', { type: 'image/png' })] }));
    } catch (e) { return false; }
  }

  function canInstagram() {
    var a = app();
    try { return !!(a && a.canShareToInstagram && a.canShareToInstagram()); } catch (e) { return false; }
  }

  function primaryLabel() { return app() || webShareFiles() ? 'Share' : 'Download picture'; }

  // iPhone Safari only lets a page share right after the tap, so the picture is made in advance.
  var prepared = null;
  function prepare(vm) {
    var mine = prepared = { vm: vm, file: null };
    toBlob(vm.card, { accent: vm.accent }).then(function (b) {
      mine.file = new File([b], fileName(vm), { type: 'image/png' });
    }).catch(function () {});
  }

  // Resolves to a short note for the page ('' when the share sheet handled it).
  function share(vm, act) {
    var a = app();
    if (!a && prepared && prepared.vm === vm && prepared.file && webShareFiles()) {
      return navigator.share({ files: [prepared.file], title: 'EngQuest Chronicle' }).then(function () { return ''; })
        .catch(function (e) { if (e && e.name === 'AbortError') return ''; throw e; });
    }
    if (act === 'instagram' && a && a.shareToInstagramStory) {
      return stickerBlob(vm.card, { accent: vm.accent }).then(base64).then(function (b) { a.shareToInstagramStory(b); return ''; });
    }
    return toBlob(vm.card, { accent: vm.accent }).then(function (blob) {
      if (a && a.shareImage) return base64(blob).then(function (b) { a.shareImage(b); return ''; });
      var file = new File([blob], fileName(vm), { type: 'image/png' });
      if (webShareFiles()) {
        return navigator.share({ files: [file], title: 'EngQuest Chronicle' }).then(function () { return ''; })
          .catch(function (e) { if (e && e.name === 'AbortError') return ''; throw e; });
      }
      var url = URL.createObjectURL(blob), link = document.createElement('a');
      link.href = url; link.download = fileName(vm);
      document.body.appendChild(link); link.click(); link.remove();
      setTimeout(function () { URL.revokeObjectURL(url); }, 4000);
      return 'Saved to your downloads.';
    });
  }

  global.EQChronicleCard = { toBlob: toBlob, stickerBlob: stickerBlob, share: share, prepare: prepare, canInstagram: canInstagram, primaryLabel: primaryLabel };
})(window);
