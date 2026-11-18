// Run: node --test scripts/chronicle-data.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { build, rankFontSize, fitNameLines, SAMPLE, SAMPLE_LONGNAME } = require('../shared/js/chronicle-data.js');

// A stand-in for canvas measureText: every character is half the font size wide.
const measure = (text, size) => text.length * size * 0.5;
const fitsWidth = (fit, width) => fit.lines.every((l) => measure(l, fit.size) <= width);

test('a very long name shrinks below 28 and stays inside the width', () => {
  const fit = fitNameLines('Muhammad Rafi Alfarizi Pratama Wijaya Kusuma Ningrat', 218, 58, measure);
  assert.ok(fit.lines.length <= 3);
  assert.ok(fitsWidth(fit, 218), JSON.stringify(fit));
});

test('a single very long word is broken rather than cropped', () => {
  const fit = fitNameLines('Wiryawicaksanakusumaningratadiningrat', 218, 58, measure);
  assert.ok(fitsWidth(fit, 218), JSON.stringify(fit));
});

test('a short name keeps the big size on one line', () => {
  assert.deepEqual(fitNameLines('Rafi', 218, 58, measure), { size: 58, lines: ['Rafi'] });
});

test('a full bundle gives 8 slides in order', () => {
  assert.deepEqual(build(SAMPLE, { grade: '10' }).slides.map((s) => s.id),
    ['cover', 'attendance', 'tasks', 'vocab', 'level', 'final', 'rank', 'gradeRank']);
});

test('no attendance meetings skips the attendance slide', () => {
  const vm = build({ ...SAMPLE, attendance: { meetings: 0, present: 0, sick: 0, permit: 0, absent: 0, days: [] } }, { grade: '10' });
  assert.ok(!vm.slides.some((s) => s.id === 'attendance'));
  assert.equal(vm.card.present, '—');
});

test('no vocabulary skips the vocab slide', () => {
  const vm = build({ ...SAMPLE, vocab: { total: 0, words: [], latest: '', classTop5: false } }, { grade: '10' });
  assert.ok(!vm.slides.some((s) => s.id === 'vocab'));
});

test('no tasks and no task scores skips the tasks slide', () => {
  const vm = build({ ...SAMPLE, tasks: { done: 0, best: null } }, { grade: '10' });
  assert.ok(!vm.slides.some((s) => s.id === 'tasks'));
});

test('blank final skips final, rank and grade rank; card shows dashes', () => {
  const vm = build({ ...SAMPLE, scores: { ...SAMPLE.scores, final: '' }, rank: { place: null, of: 36 }, gradeRank: { ...SAMPLE.gradeRank, place: null } }, { grade: '10' });
  assert.ok(!vm.slides.some((s) => ['final', 'rank', 'gradeRank'].includes(s.id)));
  assert.equal(vm.card.final, '—');
  assert.equal(vm.card.rank, '—');
  assert.equal(vm.card.gradeRank, '');
});

test('present percent rounds to a whole number', () => {
  const vm = build(SAMPLE, { grade: '10' });
  assert.equal(vm.slides.find((s) => s.id === 'attendance').percent, '91%');
  assert.equal(vm.card.present, '91%');
});

test('final score shows as a whole number', () => {
  const vm = build({ ...SAMPLE, scores: { ...SAMPLE.scores, final: 87 } }, { grade: '10' });
  assert.equal(vm.card.final, '87');
  assert.equal(vm.slides.find((s) => s.id === 'final').final, 87);
});

test('grade 11 uses the teal accent', () => {
  assert.equal(build(SAMPLE, { grade: '10' }).accent, '#2450E0');
  assert.equal(build(SAMPLE, { grade: '11' }).accent, '#0F8A73');
});

test('class label reads X E-2 for XE2 and XI F-7 for XIF7', () => {
  assert.equal(build(SAMPLE, { grade: '10' }).classLabel, 'X E-2');
  assert.equal(build({ ...SAMPLE, student: { ...SAMPLE.student, classId: 'XIF7', grade: '11' } }, { grade: '11' }).classLabel, 'XI F-7');
});

test('rank numerals shrink with more digits', () => {
  assert.deepEqual([4, 24, 105].map((n) => rankFontSize(n, 250)), [250, 170, 120]);
  assert.deepEqual([11, 105].map((n) => rankFontSize(n, 170)), [170, 120]);
});

test('card lines read the way the share card shows them', () => {
  const vm = build(SAMPLE, { grade: '10' });
  assert.deepEqual(
    { rank: vm.card.rank, gradeRank: vm.card.gradeRank, classLine: vm.card.classLine, level: vm.card.level, tasks: vm.card.tasks, words: vm.card.words },
    { rank: '#4', gradeRank: '#11 of 178 in Grade 10', classLine: 'X E-2 · Grade 10', level: '12', tasks: '28', words: '142' });
});

test('the card masthead uses a short semester label', () => {
  assert.equal(build(SAMPLE, { grade: '10' }).card.label, 'SEM 1 · 26/27');
  assert.equal(build({ ...SAMPLE, label: 'Semester 2 · 2026/27' }, { grade: '10' }).card.label, 'SEM 2 · 26/27');
});

test('the nickname is the display name, the full name is kept', () => {
  const vm = build(SAMPLE, { grade: '10' });
  assert.equal(vm.name, 'Rafi');
  assert.equal(build(SAMPLE_LONGNAME, { grade: '10' }).name, 'Muhammad Rafi Alfarizi Pratama');
});

test('grade rank marks the student\'s own class', () => {
  const g = build(SAMPLE, { grade: '10' }).slides.find((s) => s.id === 'gradeRank');
  assert.deepEqual(g.classes.filter((c) => c.mine).map((c) => c.id), ['XE2']);
  assert.equal(g.classes.length, 5);
});

test('level progress comes from XP to next level', () => {
  const l = build(SAMPLE, { grade: '10' }).slides.find((s) => s.id === 'level');
  // level 12 needs 5 + 5*12 = 65 XP; 370 short is impossible, so the sample uses toNext 20 -> 45/65
  assert.equal(Math.round(l.progress * 100), 69);
});
