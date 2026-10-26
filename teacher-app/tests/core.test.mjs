import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const core = require('../js/core.js');

test('taskToChapterCol maps task codes to backend chapter n+3', () => {
  assert.deepEqual(core.taskToChapterCol('C1T2'), { chapter: '4', column: 'Task 2' });
  assert.deepEqual(core.taskToChapterCol('c3f'), { chapter: '6', column: 'Formative' });
  assert.deepEqual(core.taskToChapterCol('C2S'), { chapter: '5', column: 'Summative' });
});

test('taskToChapterCol returns null for codes it cannot map', () => {
  assert.equal(core.taskToChapterCol('XYZ'), null);
  assert.equal(core.taskToChapterCol(''), null);
  assert.equal(core.taskToChapterCol(null), null);
});

test('validateScore accepts empty or 0-100 and trims', () => {
  assert.equal(core.validateScore('85').ok, true);
  assert.equal(core.validateScore('').ok, true);
  assert.equal(core.validateScore(' 70 ').value, '70');
  assert.equal(core.validateScore('101').ok, false);
  assert.equal(core.validateScore('-1').ok, false);
  assert.equal(core.validateScore('abc').ok, false);
});

test('isLowScore flags non-empty scores under 75', () => {
  assert.equal(core.isLowScore('74'), true);
  assert.equal(core.isLowScore('75'), false);
  assert.equal(core.isLowScore(''), false);
});

const students = [
  { no: '1', name: 'Adit', attendance: { m3: 'Sick' } },
  { no: '2', name: 'Bella', attendance: {} }
];

test('attendanceInitial starts everyone Present when the date has no meeting', () => {
  const r = core.attendanceInitial(students, [{ number: 3, date: '2026-10-20' }], '2026-10-26');
  assert.equal(r.meeting, null);
  assert.deepEqual(r.marks, { 1: 'Present', 2: 'Present' });
});

test('attendanceInitial loads stored marks for an existing meeting', () => {
  const r = core.attendanceInitial(students, [{ number: 3, date: '2026-10-26' }], '2026-10-26');
  assert.equal(r.meeting.number, 3);
  assert.deepEqual(r.marks, { 1: 'Sick', 2: 'Present' });
});

test('attendanceCounts, attendanceDirty and attendanceRecords', () => {
  const initial = { 1: 'Present', 2: 'Present' };
  const marks = { 1: 'Absent', 2: 'Present' };
  assert.deepEqual(core.attendanceCounts(marks), { Present: 1, Absent: 1, Sick: 0, Permit: 0 });
  assert.equal(core.attendanceDirty(initial, { ...initial }), false);
  assert.equal(core.attendanceDirty(initial, marks), true);
  assert.deepEqual(core.attendanceRecords(students, marks), [
    { no: '1', status: 'Absent' },
    { no: '2', status: 'Present' }
  ]);
});

test('initials and firstName', () => {
  assert.equal(core.initials('Adit Pratama'), 'AP');
  assert.equal(core.initials(''), 'EQ');
  assert.equal(core.firstName('Adit Pratama'), 'Adit');
});

test('groupSubmissions filters by class and status and sorts tasks', () => {
  const rows = [
    { subId: 'a', className: 'XE1', studentNo: '2', studentName: 'B', task: 'C2T1', status: 'pending' },
    { subId: 'b', className: 'XE1', studentNo: '2', studentName: 'B', task: 'C1S', status: 'pending' },
    { subId: 'c', className: 'XE1', studentNo: '2', studentName: 'B', task: 'C1T2', status: 'pending' },
    { subId: 'd', className: 'XE1', studentNo: '2', studentName: 'B', task: 'C1F', status: 'pending' },
    { subId: 'e', className: 'XE1', studentNo: '2', studentName: 'B', task: 'C1T1', status: 'pending' },
    { subId: 'f', className: 'XE1', studentNo: '1', studentName: 'A', task: 'C1T1', status: 'reviewed' },
    { subId: 'g', className: 'XE2', studentNo: '1', studentName: 'Z', task: 'C1T1', status: 'pending' }
  ];
  const groups = core.groupSubmissions(rows, { className: 'XE1', filter: 'pending' });
  assert.equal(groups.length, 1);
  assert.deepEqual(groups[0].items.map(i => i.task), ['C1T1', 'C1T2', 'C1F', 'C1S', 'C2T1']);
  const all = core.groupSubmissions(rows, { className: 'XE1', filter: 'all' });
  assert.deepEqual(all.map(g => g.studentNo), ['1', '2']);
});
