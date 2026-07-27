// ============================================================
//  ENGLISH QUEST – SCORES + TASK STATUS API
//  Google Apps Script — paste into Code.gs (replace all)
//  Redeploy as new version after updating!
// ============================================================

// New-year (2026) class score sheets — created via SETUP_createClassSheets,
// each with Scores / Chapter_1-3 / Task_Status / Activeness / Strike tabs and
// 36 numbered students. Replaces last year's XE1/XE4-XE11 set.
const SCORE_SHEETS = {
  'XE1':  '1lpBQ7BYhVvKlAkqcG0GjJQfdPDEQ4wMKJvatixIxQxo',
  'XE2':  '19aIZQbgpfrDl3WOpVJ0NrYv0JM0ajy6MVdSbHZUNoWg',
  'XE3':  '1HtAgC_RtDf4ByQdUqPSTp93aLOyu486rq0x27mVeNHE',
  'XE4':  '1dW-mv6ZwZVbOfdMbji4e3ddjodl4jZ3WXrHeVPBSaRg',
  'XE5':  '1KgciwoyoDAT4h1KzhF2CAkOMfznO7ye88icEvJ-JeSQ',
  'XIF7': '1b0VcMEbVVF9fpeSrR06Ng5pJKQ3_5A1vkdWgdnRe19g',
  'XIF8': '1G5kzKlsKhXj0NrJ4St1nU7wj4dHPpBgoXiB3x9eoY54',
  'XIF9': '16oayabd-pvz8x4cXTSgS-d_HT7pTZD7T2N3ELnmu6KQ',
};

const TASK_SHEET_NAME = 'Task_Status';
const TEACHER_USER    = 'teacher';
const TEACHER_HASH_PROPERTY = 'TEACHER_PASSWORD_SHA256';

function authOk(username, password) {
  // The stored value must be a 64-char hex SHA-256. Strip ANY character that
  // isn't a hex digit (stray spaces, line-breaks, or invisible characters that
  // sneak in via copy-paste when the property is set by hand) so a paste gremlin
  // can never silently break the login again.
  const storedHash = String(
    PropertiesService.getScriptProperties().getProperty(TEACHER_HASH_PROPERTY) || ''
  ).toLowerCase().replace(/[^a-f0-9]/g, '');
  if (!storedHash || username !== TEACHER_USER) return false;

  const candidate = String(password || '').trim();
  const candidateHash = /^[a-f0-9]{64}$/i.test(candidate)
    ? candidate.toLowerCase()
    : sha256Hex(candidate);
  return candidateHash === storedHash;
}

function sha256Hex(value) {
  return Utilities.computeDigest(
    Utilities.DigestAlgorithm.SHA_256,
    String(value || ''),
    Utilities.Charset.UTF_8
  ).map(function(byte) {
    return (byte + 256).toString(16).slice(-2);
  }).join('');
}

// Lets a logged-in teacher change their own password. Verifies the current
// password, then stores the SHA-256 of the new one in Script Properties.
function setTeacherPassword(username, password, newPassword) {
  if (!authOk(username, password)) return { success: false, error: 'Current password is incorrect.' };
  const next = String(newPassword || '').trim();
  if (next.length < 6) return { success: false, error: 'New password must be at least 6 characters.' };
  PropertiesService.getScriptProperties().setProperty(TEACHER_HASH_PROPERTY, sha256Hex(next));
  return { success: true };
}


function teacherHealthCheck(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const details = {};
  const stamp = new Date().toISOString();
  try {
    const props = PropertiesService.getScriptProperties();
    props.setProperty('_launchHealthCheck', stamp);
    details.scriptPropertyWrite = props.getProperty('_launchHealthCheck') === stamp;
    props.deleteProperty('_launchHealthCheck');

    const classNames = Object.keys(SCORE_SHEETS);
    details.classSheets = classNames.length;
    details.firstClassRows = SpreadsheetApp.openById(SCORE_SHEETS[classNames[0]]).getSheets()[0].getLastRow();
    details.summativeRows = _getSummativeTopicSheet().getLastRow();

    return {
      success: true,
      checkedAt: stamp,
      details: details
    };
  } catch (e) {
    return { success: false, error: e.message, details: details };
  }
}

function doGet(e) {
  const action = e.parameter.action || '', callback = e.parameter.callback || '';
  let result;
  try {
    if      (action === 'getStudents')    result = getStudents(e.parameter.className);
    else if (action === 'getScore')       result = getScore(e.parameter.className, e.parameter.studentNo);
    else if (action === 'getChapterScores')  result = getChapterScores(e.parameter.className, e.parameter.chapter);
    else if (action === 'saveChapterScore')  result = saveChapterScore(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.chapter, e.parameter.studentNo, e.parameter.column, e.parameter.value);
    else if (action === 'getFinalScores')    result = getFinalScores(e.parameter.className);
    else if (action === 'saveFinalScore')    result = saveFinalScore(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, e.parameter.column, e.parameter.value);
    else if (action === 'getSubmissions')    result = getSubmissions();
    else if (action === 'getTaskStatus')  result = getTaskStatus(e.parameter.className, e.parameter.studentNo);
    else if (action === 'getAllTasks')    result = getAllTasks(e.parameter.className);
    else if (action === 'saveTaskStatus') result = saveTaskStatus(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, JSON.parse(e.parameter.tasks || '{}'));
    else if (action === 'syncCh5Student') result = syncCh5Student(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo);
    else if (action === 'syncCh5Class')   result = syncCh5Class(e.parameter.className, e.parameter.username, e.parameter.password);
    else if (action === 'getCh5Submissions')   result = getCh5Submissions();
    else if (action === 'getCh5StudentFiles') result = getCh5StudentFiles(e.parameter.className, e.parameter.studentNo);
    else if (action === 'checkLogin')       result = { ok: authOk(e.parameter.username, e.parameter.password) };
    else if (action === 'setTeacherPassword') result = setTeacherPassword(e.parameter.username, e.parameter.password, e.parameter.newPassword);
    else if (action === 'getSummative')     result = getSummative();
    else if (action === 'getQuizAttempt')   result = getQuizAttempt(e.parameter.className, e.parameter.studentNo, e.parameter.chapter);
    else if (action === 'getGeminiKey')      result = getGeminiKey(e.parameter.username, e.parameter.password);
    else if (action === 'getQuizResults')   result = getQuizResults(e.parameter.username, e.parameter.password, e.parameter.className);
    else if (action === 'saveQuizScore')    result = saveQuizScore(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, e.parameter.chapter, e.parameter.score);
    else if (action === 'setSummative')     result = setSummative(e.parameter.username, e.parameter.password, e.parameter.data);
    else if (action === 'clearSummative')   result = clearSummative(e.parameter.username, e.parameter.password);
    else if (action === 'getAllActiveness')    result = getAllActiveness(e.parameter.className);
    else if (action === 'getLeaderboard')      result = getLeaderboard(e.parameter.limit);
    else if (action === 'incrementActiveness') result = incrementActiveness(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, e.parameter.column, e.parameter.delta || 1);
    else if (action === 'setupQuizColumn')     result = setupQuizColumn(e.parameter.username, e.parameter.password);
    else if (action === 'setupCalcFormulas')   result = setupCalcFormulas(e.parameter.username, e.parameter.password);
    else if (action === 'setupRoster')         result = setupRoster(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.names);
    else if (action === 'setupActivenessXP')   result = setupActivenessXP(e.parameter.username, e.parameter.password);
    else if (action === 'setupTaskColumns')    result = setupTaskColumns(e.parameter.username, e.parameter.password);
    else if (action === 'setupSubmissions')     result = setupSubmissions(e.parameter.username, e.parameter.password);
    else if (action === 'getTaskSubmissions')   result = getTaskSubmissions(e.parameter.username, e.parameter.password);
    else if (action === 'getSubmissionPhoto')   result = getSubmissionPhoto(e.parameter.username, e.parameter.password, e.parameter.fileId);
    else if (action === 'reviewTaskSubmission') result = reviewTaskSubmission(e.parameter.username, e.parameter.password, e.parameter.subId, e.parameter.decision);
    else if (action === 'getMySubmissions')     result = getMySubmissions(e.parameter.className, e.parameter.studentNo);
    else if (action === 'adminClearSubmissions') result = adminClearSubmissions(e.parameter.username, e.parameter.password, e.parameter.onlyReviewed);
    else if (action === 'getVocabulary')       result = getVocabulary(e.parameter.className, e.parameter.studentNo);
    else if (action === 'addVocabulary')        result = addVocabulary(e.parameter.className, e.parameter.studentNo, e.parameter.words);
    else if (action === 'getClassVocabulary')   result = getClassVocabulary(e.parameter.className);
    else if (action === 'checkMeanings')        result = checkMeanings(e.parameter.pairs);
    else if (action === 'checkVocabulary')      result = checkVocabulary(e.parameter.className, e.parameter.studentNo);
    else if (action === 'removeVocabulary')     result = removeVocabulary(e.parameter.className, e.parameter.studentNo, e.parameter.english);
    else if (action === 'clearVocabulary')      result = clearVocabulary(e.parameter.username, e.parameter.password, e.parameter.className);
    else if (action === 'setupAttendanceSheet') result = setupAttendanceSheet(e.parameter.username, e.parameter.password);
    else if (action === 'getClassData')         result = getClassData(e.parameter.className);
    else if (action === 'saveAttendance')       result = saveAttendance(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.date, e.parameter.records);
    else if (action === 'removeAttendanceMeeting') result = removeAttendanceMeeting(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.date);
    else if (action === 'getStudentStrikes')  result = getStudentStrikes(e.parameter.className, e.parameter.studentNo);
    else if (action === 'debugStrikeHeaders')  result = debugStrikeHeaders(e.parameter.className);
    else if (action === 'getAllStrikes')       result = getAllStrikes(e.parameter.username, e.parameter.password, e.parameter.className);
    else if (action === 'addStrike')          result = addStrike(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, e.parameter.reason || '');
    else if (action === 'removeStrike')       result = removeStrike(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo);
    else if (action === 'debugCh5Names')   result = debugCh5Names(e.parameter.username, e.parameter.password);
    else if (action === 'getMaterials')    result = getMaterials(e.parameter.chapter, e.parameter.grade);
    else if (action === 'addMaterial')     result = addMaterial(e.parameter.username, e.parameter.password, e.parameter.chapter, e.parameter.title, e.parameter.fileId, e.parameter.fileType, e.parameter.grade);
    else if (action === 'deleteMaterial')  result = deleteMaterial(e.parameter.username, e.parameter.password, e.parameter.chapter, e.parameter.fileId, e.parameter.grade);
    else if (action === 'setStance')        result = setStance(e.parameter.cls, e.parameter.attNum, e.parameter.stance);
    else if (action === 'setReveal')        result = setReveal(e.parameter.username, e.parameter.password, e.parameter.cls, e.parameter.attNum, e.parameter.revealed);
    else if (action === 'getBannerSlides')  result = getBannerSlides();
    else if (action === 'setBannerSlides')  result = setBannerSlides(e.parameter.username, e.parameter.password, e.parameter.data);
    else if (action === 'teacherHealthCheck') result = teacherHealthCheck(e.parameter.username, e.parameter.password);
    else if (action === 'ping')            result = { ok: true };
    else if (action === 'getUnitNames')      result = getUnitNames(e.parameter.grade);
    else if (action === 'setUnitNames')      result = setUnitNames(e.parameter.username, e.parameter.password, e.parameter.grade, e.parameter.names);
    else if (action === 'getAnnouncement')   result = getAnnouncement();
    else if (action === 'setAnnouncement')   result = setAnnouncement(e.parameter.username, e.parameter.password, e.parameter.title, e.parameter.body, e.parameter.type, e.parameter.audience);
    else if (action === 'clearAnnouncement') result = clearAnnouncement(e.parameter.username, e.parameter.password);
    else result = { error: 'Unknown action' };
  } catch(err) { result = { error: err.message }; }
  const json = JSON.stringify(result);
  if (callback) return ContentService.createTextOutput(callback + '(' + json + ')').setMimeType(ContentService.MimeType.JAVASCRIPT);
  return ContentService.createTextOutput(json).setMimeType(ContentService.MimeType.JSON);
}

function doPost(e) {
  let body = {};
  try { body = JSON.parse(e.postData.contents); } catch(err) {}
  let result;
  try {
    if (body.action === 'saveTaskStatus') result = saveTaskStatus(body.username, body.password, body.className, body.studentNo, body.tasks || {});
    else if (body.action === 'submitTaskPhoto') result = submitTaskPhoto(body.className, body.studentNo, body.taskKey, body.images, body.mimeType, body.image);
  } catch(err) { result = { error: err.message }; }
  return ContentService.createTextOutput(JSON.stringify(result)).setMimeType(ContentService.MimeType.JSON);
}

function getStudents(className) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const data = SpreadsheetApp.openById(sheetId).getSheets()[0].getDataRange().getValues();
  const students = [];
  for (let r = 1; r < data.length; r++) {
    const no = data[r][0], name = data[r][1];
    if (!no || !name || String(name).trim() === '') break;
    students.push({ no, name: String(name).trim(), nickname: String(data[r][2] || '').trim() });
  }
  return { students };
}

function getScore(className, studentNo) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheets()[0];
  const data = sheet.getDataRange().getValues();
  const headers = data[0];
  let row = null;
  for (let r = 1; r < data.length; r++) { if (String(data[r][0]) === String(studentNo)) { row = data[r]; break; } }
  if (!row) return { error: 'Student not found' };
  const score = {};
  headers.forEach((h, i) => { if (h && String(h).trim()) { const v = row[i]; score[String(h).trim()] = (v === '#DIV/0!' || v === '' || v === undefined) ? '-' : v; } });
  return { no: row[0], name: String(row[1]).trim(), nickname: String(row[2] || '').trim(), score };
}

function getTaskStatus(className, studentNo) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName(TASK_SHEET_NAME);
  if (!sheet) return { error: 'Task_Status sheet not found' };
  const data = sheet.getDataRange().getValues();
  const headers = data[0];
  let row = null;
  for (let r = 1; r < data.length; r++) { if (String(data[r][0]) === String(studentNo)) { row = data[r]; break; } }
  if (!row) return { error: 'Student not found' };
  const tasks = {};
  for (let c = 3; c < headers.length; c++) {
    const key = String(headers[c] || '').trim().toUpperCase();
    if (key) tasks[key] = row[c] === true || row[c] === 'TRUE' || row[c] === 1;
  }
  return { no: row[0], name: String(row[1]).trim(), nickname: String(row[2] || '').trim(), tasks };
}

function getAllTasks(className) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName(TASK_SHEET_NAME);
  if (!sheet) return { error: 'Task_Status sheet not found' };
  const data = sheet.getDataRange().getValues();
  const headers = data[0];
  const taskKeys = [];
  for (let c = 3; c < headers.length; c++) {
    const k = String(headers[c] || '').trim().toUpperCase();
    if (k) taskKeys.push({ key: k, col: c });
  }
  const students = [];
  for (let r = 1; r < data.length; r++) {
    const no = data[r][0], name = data[r][1];
    if (!no || !name || String(name).trim() === '') break;
    const tasks = {};
    taskKeys.forEach(t => { tasks[t.key] = data[r][t.col] === true || data[r][t.col] === 'TRUE' || data[r][t.col] === 1; });
    students.push({ no, name: String(name).trim(), nickname: String(data[r][2] || '').trim(), tasks });
  }
  return { students, taskKeys: taskKeys.map(t => t.key) };
}

function saveTaskStatus(username, password, className, studentNo, tasks) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName(TASK_SHEET_NAME);
  if (!sheet) return { success: false, error: 'Task_Status sheet not found' };
  const data = sheet.getDataRange().getValues();
  const headers = data[0];
  let rowIndex = -1;
  for (let r = 1; r < data.length; r++) { if (String(data[r][0]) === String(studentNo)) { rowIndex = r; break; } }
  if (rowIndex === -1) return { success: false, error: 'Student not found' };
  for (let c = 3; c < headers.length; c++) {
    const key = String(headers[c] || '').trim().toUpperCase();
    if (!key || tasks[key] === undefined) continue;
    sheet.getRange(rowIndex + 1, c + 1).setValue(tasks[key] === true || tasks[key] === 'true');
  }
  return { success: true };
}

// ── SYNC CH5 SUBMISSION → TASK STATUS (teacher-triggered) ──
function syncCh5Student(username, password, className, studentNo) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const CH5_SS_ID = '1WqvB1SkFEh-lnZ3mLAFzArCuHWqnuxXDbGz_gLZ3zyQ';
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };

  // Get student name from Task_Status sheet
  const taskSheet = SpreadsheetApp.openById(sheetId).getSheetByName(TASK_SHEET_NAME);
  if (!taskSheet) return { success: false, error: 'Task_Status sheet not found' };
  const taskData = taskSheet.getDataRange().getValues();
  const headers = taskData[0];
  let rowIndex = -1, studentName = '';
  for (let r = 1; r < taskData.length; r++) {
    if (String(taskData[r][0]) === String(studentNo)) {
      rowIndex = r;
      studentName = String(taskData[r][1]).trim();
      break;
    }
  }
  if (rowIndex === -1) return { success: false, error: 'Student not found' };

  // Find submission row in Ch5 Responses sheet
  const respSheet = SpreadsheetApp.openById(CH5_SS_ID).getSheetByName('Responses');
  if (!respSheet) return { success: false, error: 'Ch5 Responses sheet not found' };
  const respData = respSheet.getDataRange().getValues();
  // Columns: [Timestamp, Class, Student Name, Task 1 File, Task 2 File, Task 3 File, Task 4 File, Task 5 File]
  const norm = s => String(s).toLowerCase().replace(/[-]/g, ' ').replace(/[^a-z0-9\s]/g, '').replace(/\s+/g, ' ').trim();
  const normCls = s => String(s).toLowerCase().replace(/[^a-z0-9]/g, ''); // "X E-1" → "xe1"
  let submissionRow = null;
  for (let r = 1; r < respData.length; r++) {
    if (normCls(respData[r][1]) === normCls(className) && norm(respData[r][2]) === norm(studentName)) {
      submissionRow = respData[r];
    }
  }
  if (!submissionRow) return { success: false, error: 'No Ch5 submission found — make sure you submitted via the Chapter 5 form first.' };

  // Map Task 1-5 file columns (index 3-7) → C5T1-C5T5, mark TRUE if file link exists
  const tasksToSet = {};
  for (let t = 1; t <= 5; t++) {
    const fileVal = String(submissionRow[2 + t] || '').trim();
    if (fileVal) tasksToSet['C5T' + t] = true;
  }
  if (Object.keys(tasksToSet).length === 0) return { success: false, error: 'Submission found but no files were recorded.' };

  // Write to Task_Status sheet
  for (let c = 3; c < headers.length; c++) {
    const key = String(headers[c] || '').trim().toUpperCase();
    if (!key || tasksToSet[key] === undefined) continue;
    taskSheet.getRange(rowIndex + 1, c + 1).setValue(true);
  }
  SpreadsheetApp.flush();

  return getTaskStatus(className, studentNo);
}

// ── BULK SYNC ALL CH5 STUDENTS IN A CLASS (teacher-triggered) ──
function syncCh5Class(className, username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const CH5_SS_ID = '1WqvB1SkFEh-lnZ3mLAFzArCuHWqnuxXDbGz_gLZ3zyQ';
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };

  const taskSheet = SpreadsheetApp.openById(sheetId).getSheetByName(TASK_SHEET_NAME);
  if (!taskSheet) return { success: false, error: 'Task_Status sheet not found' };
  const taskData = taskSheet.getDataRange().getValues();
  const headers  = taskData[0];

  const ch5Cols = {};
  for (let c = 3; c < headers.length; c++) {
    const key = String(headers[c] || '').trim().toUpperCase();
    if (/^C5T/.test(key)) ch5Cols[key] = c;
  }
  if (Object.keys(ch5Cols).length === 0) return { success: false, error: 'No Ch5 task columns in Task_Status' };

  const respSheet = SpreadsheetApp.openById(CH5_SS_ID).getSheetByName('Responses');
  if (!respSheet) return { success: false, error: 'Ch5 Responses sheet not found' };
  const respData = respSheet.getDataRange().getValues();

  const normCls  = s => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');
  const normName = s => String(s).toLowerCase().replace(/[-]/g, ' ').replace(/[^a-z0-9\s]/g, '').replace(/\s+/g, ' ').trim();

  const ch5Map = {};
  for (let r = 1; r < respData.length; r++) {
    if (normCls(respData[r][1]) !== normCls(className)) continue;
    const name  = String(respData[r][2] || '').trim();
    const tasks = [];
    for (let t = 0; t < 5; t++) tasks.push(!!String(respData[r][3 + t] || '').trim());
    const k = normName(name);
    if (ch5Map[k]) { tasks.forEach((v, i) => { if (v) ch5Map[k][i] = true; }); }
    else           { ch5Map[k] = tasks; }
  }

  let synced = 0, notFound = 0;
  for (let r = 1; r < taskData.length; r++) {
    const name = String(taskData[r][1] || '').trim();
    if (!name) continue;
    const tasks = ch5Map[normName(name)];
    if (!tasks) { notFound++; continue; }
    let wrote = false;
    for (const [key, col] of Object.entries(ch5Cols)) {
      const taskIdx = parseInt(key.replace('C5T', '')) - 1;
      if (tasks[taskIdx] && !taskData[r][col]) {
        taskSheet.getRange(r + 1, col + 1).setValue(true);
        wrote = true;
      }
    }
    if (wrote) synced++;
  }
  SpreadsheetApp.flush();
  return { success: true, synced, notFound };
}

// ── DEBUG: Compare Ch5 Students sheet names vs Task_Status names (teacher-only) ──
function debugCh5Names(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const CH5_SS_ID = '1WqvB1SkFEh-lnZ3mLAFzArCuHWqnuxXDbGz_gLZ3zyQ';
  const normCls  = s => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');
  const normName = s => String(s).toLowerCase().replace(/[-]/g, ' ').replace(/[^a-z0-9\s]/g, '').replace(/\s+/g, ' ').trim();

  // Load Ch5 Students sheet: headers = class names, rows = student names per class
  const ch5Sheet = SpreadsheetApp.openById(CH5_SS_ID).getSheetByName('Students');
  if (!ch5Sheet) return { success: false, error: 'Ch5 Students sheet not found' };
  const ch5Data = ch5Sheet.getDataRange().getValues();
  const ch5Headers = ch5Data[0];

  // Build map: normCls(className) → Set of normName(studentName) → original name
  const ch5NamesMap = {};
  for (let c = 0; c < ch5Headers.length; c++) {
    const cls = String(ch5Headers[c] || '').trim();
    if (!cls) continue;
    const key = normCls(cls);
    ch5NamesMap[key] = {};
    for (let r = 1; r < ch5Data.length; r++) {
      const name = String(ch5Data[r][c] || '').trim();
      if (name) ch5NamesMap[key][normName(name)] = name;
    }
  }

  const mismatches = {};
  for (const [className, sheetId] of Object.entries(SCORE_SHEETS)) {
    const taskSheet = SpreadsheetApp.openById(sheetId).getSheetByName(TASK_SHEET_NAME);
    if (!taskSheet) continue;
    const taskData = taskSheet.getDataRange().getValues();
    const ch5Names = ch5NamesMap[normCls(className)] || {};
    const clsMismatches = [];
    for (let r = 1; r < taskData.length; r++) {
      const name = String(taskData[r][1] || '').trim();
      if (!name) continue;
      const norm = normName(name);
      if (!ch5Names[norm]) {
        // Not found in Ch5 Students sheet — find closest (share first token)
        const firstToken = norm.split(' ')[0];
        const similar = Object.values(ch5Names).filter(n => normName(n).startsWith(firstToken));
        clsMismatches.push({ taskListName: name, ch5SheetName: similar[0] || null });
      }
    }
    if (clsMismatches.length) mismatches[className] = clsMismatches;
  }
  return { success: true, mismatches };
}

// ── CH5 FILE URLS FOR A SPECIFIC STUDENT (teacher viewer) ────
function getCh5StudentFiles(className, studentNo) {
  const CH5_SS_ID = '1WqvB1SkFEh-lnZ3mLAFzArCuHWqnuxXDbGz_gLZ3zyQ';
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };

  const taskSheet = SpreadsheetApp.openById(sheetId).getSheetByName(TASK_SHEET_NAME);
  if (!taskSheet) return { success: false, error: 'Task_Status sheet not found' };
  const taskData = taskSheet.getDataRange().getValues();

  let studentName = '';
  for (let r = 1; r < taskData.length; r++) {
    if (String(taskData[r][0]) === String(studentNo)) {
      studentName = String(taskData[r][1]).trim();
      break;
    }
  }
  if (!studentName) return { success: false, error: 'Student not found' };

  const respSheet = SpreadsheetApp.openById(CH5_SS_ID).getSheetByName('Responses');
  if (!respSheet) return { success: true, submitted: false, files: {}, studentName };
  const respData = respSheet.getDataRange().getValues();

  const normCls  = s => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');
  const normName = s => String(s).toLowerCase().replace(/[-]/g, ' ').replace(/[^a-z0-9\s]/g, '').replace(/\s+/g, ' ').trim();

  let submissionRow = null;
  for (let r = 1; r < respData.length; r++) {
    if (normCls(respData[r][1]) === normCls(className) && normName(respData[r][2]) === normName(studentName)) {
      submissionRow = respData[r];
    }
  }
  if (!submissionRow) return { success: true, submitted: false, files: {}, studentName };

  const files = {};
  for (let t = 1; t <= 5; t++) {
    const val = String(submissionRow[2 + t] || '').trim();
    if (val) files['C5T' + t] = val;
  }
  return { success: true, submitted: true, files, studentName, timestamp: String(submissionRow[0]) };
}

// ── CH5 SUBMISSION OVERVIEW (teacher dashboard) ───────────────
function getCh5Submissions() {
  const CH5_SS_ID = '1WqvB1SkFEh-lnZ3mLAFzArCuHWqnuxXDbGz_gLZ3zyQ';
  const normCls  = s => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');
  const normName = s => String(s).toLowerCase().replace(/[-]/g, ' ').replace(/[^a-z0-9\s]/g, '').replace(/\s+/g, ' ').trim();
  try {
    const respSheet = SpreadsheetApp.openById(CH5_SS_ID).getSheetByName('Responses');
    if (!respSheet) return { byKey: {} };
    const data  = respSheet.getDataRange().getValues();
    const byKey = {};
    for (let r = 1; r < data.length; r++) {
      const cls  = String(data[r][1] || '').trim();
      const name = String(data[r][2] || '').trim();
      if (!cls || !name) continue;
      const key   = normCls(cls) + '|' + normName(name);
      const tasks = [];
      for (let t = 0; t < 5; t++) tasks.push(!!String(data[r][3 + t] || '').trim());
      if (byKey[key]) { tasks.forEach((v, i) => { if (v) byKey[key].tasks[i] = true; }); }
      else            { byKey[key] = { tasks }; }
    }
    return { byKey };
  } catch(e) {
    return { byKey: {}, error: e.toString() };
  }
}

// ── GET ACTIVENESS FOR ALL STUDENTS ──────────────────────────
function getAllActiveness(className) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Activeness');
  if (!sheet) return { error: 'Activeness sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];

  // Find column indexes dynamically
  const cols = {};
  headers.forEach((h, i) => {
    const k = String(h || '').trim();
    if (k) cols[k] = i;
  });

  const students = [];
  for (let r = 1; r < data.length; r++) {
    const no = data[r][0], name = data[r][1];
    if (!no || !name || String(name).trim() === '') break;
    students.push({
      no, name: String(name).trim(), nickname: String(data[r][2] || '').trim(),
      VocabularyBox:   Number(data[r][cols['Vocabulary Box']]    || 0),
      Answering:       Number(data[r][cols['Answering']]         || 0),
      Presenting:      Number(data[r][cols['Presenting']]        || 0),
      DoingTask:       Number((cols['Doing Task'] !== undefined ? data[r][cols['Doing Task']] : data[r][cols['Doing Task On Time']]) || 0),
      DoingTaskOnTime: Number((cols['Doing Task'] !== undefined ? data[r][cols['Doing Task']] : data[r][cols['Doing Task On Time']]) || 0),
      HelpingHand:     Number(data[r][cols['Helping Hand']]      || 0),
      Quiz:            Number(data[r][cols['Quiz']]              || 0),
      Total:           Number(data[r][cols['Total']]             || 0),
      Indicator:       String(data[r][cols['Indicator']]         || ''),
    });
  }
  return { students, headers: Object.keys(cols) };
}

// Dashboard: rank every student across ALL classes by Total XP and return the
// top N, plus a couple of headline totals. One call for the teacher homepage.
function getLeaderboard(limit) {
  limit = Number(limit) || 5;
  var all = [], totalStudents = 0, totalTasks = 0;
  Object.keys(SCORE_SHEETS).forEach(function (cls) {
    try {
      var act = SpreadsheetApp.openById(SCORE_SHEETS[cls]).getSheetByName('Activeness');
      if (!act) return;
      var data = act.getDataRange().getValues(), headers = data[0];
      var totalCol = -1, taskCol = -1;
      for (var c = 0; c < headers.length; c++) {
        var h = String(headers[c] || '').toLowerCase().trim();
        if (h === 'total') totalCol = c;
        if (h === 'doing task' || h === 'doing task on time') taskCol = c;
      }
      for (var r = 1; r < data.length; r++) {
        var no = data[r][0], name = data[r][1];
        if (!no || !String(name).trim()) break;
        totalStudents++;
        var xp = totalCol > -1 ? Number(data[r][totalCol]) : 0;
        if (isNaN(xp)) xp = 0;
        if (taskCol > -1) { var t = Number(data[r][taskCol]); if (!isNaN(t)) totalTasks += t; }
        all.push({ name: String(name).trim(), className: cls, xp: xp });
      }
    } catch (e) {}
  });
  all.sort(function (a, b) { return b.xp - a.xp; });
  return { success: true, top: all.slice(0, limit), totalStudents: totalStudents, totalTasksCompleted: totalTasks };
}

// ── SETUP QUIZ COLUMN (run once to add Quiz col to all sheets) ──
function setupQuizColumn(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };

  var results = {};
  var classes = Object.keys(SCORE_SHEETS);

  classes.forEach(function(cls) {
    try {
      var sheet = SpreadsheetApp.openById(SCORE_SHEETS[cls]).getSheetByName('Activeness');
      if (!sheet) { results[cls] = 'Activeness sheet not found'; return; }

      var headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
      var headerStrings = headers.map(function(h) { return String(h || '').trim(); });

      // Skip if Quiz column already exists
      if (headerStrings.indexOf('Quiz') > -1) { results[cls] = 'already exists'; return; }

      // Find the Total column — insert Quiz before it
      var totalIdx = headerStrings.indexOf('Total'); // 0-based
      var insertCol = totalIdx > -1 ? totalIdx + 1 : sheet.getLastColumn() + 1; // 1-based col number

      if (totalIdx > -1) {
        // Insert a column before Total
        sheet.insertColumnBefore(insertCol);
      }

      // Set header
      sheet.getRange(1, insertCol).setValue('Quiz');

      // Set 0 for all student rows
      var lastRow = sheet.getLastRow();
      for (var r = 2; r <= lastRow; r++) {
        var no = sheet.getRange(r, 1).getValue();
        if (!no) break;
        sheet.getRange(r, insertCol).setValue(0);
      }

      // Now update the Total formula column to include Quiz
      // Total col has shifted right by 1 if we inserted before it
      var newTotalIdx = totalIdx > -1 ? totalIdx + 2 : -1; // 1-based
      if (newTotalIdx > -1) {
        var totalHeaders = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
        var totalHeaderStrings = totalHeaders.map(function(h) { return String(h || '').trim(); });

        // Find all raw input columns (VB, ANS, PRE, DTO, HH, Quiz)
        var vbCol   = totalHeaderStrings.indexOf('VB')   + 1;
        var ansCol  = totalHeaderStrings.indexOf('Answ') + 1;
        var preCol  = totalHeaderStrings.indexOf('Pre')  + 1;
        var dtoCol  = totalHeaderStrings.indexOf('DTO')  + 1;
        var hhCol   = totalHeaderStrings.indexOf('HH')   + 1;
        var qzCol   = insertCol; // 1-based

        // Update Total formula for each student row
        for (var r = 2; r <= lastRow; r++) {
          var no = sheet.getRange(r, 1).getValue();
          if (!no) break;
          if (vbCol && ansCol && preCol && dtoCol && hhCol) {
            var colLetter = function(c) {
              var letters = '';
              while (c > 0) { var mod = (c - 1) % 26; letters = String.fromCharCode(65 + mod) + letters; c = Math.floor((c - mod) / 26); }
              return letters;
            };
            var formula = '=' + colLetter(vbCol) + r + '*1+'
                            + colLetter(ansCol) + r + '*2+'
                            + colLetter(preCol) + r + '*5+'
                            + colLetter(dtoCol) + r + '*2+'
                            + colLetter(hhCol)  + r + '*1+'
                            + colLetter(qzCol)  + r + '*1';
            sheet.getRange(r, newTotalIdx).setFormula(formula);
          }
        }
      }

      SpreadsheetApp.flush();
      results[cls] = 'done';
    } catch(e) {
      results[cls] = 'error: ' + e.message;
    }
  });

  return { success: true, results: results };
}

// ── SETUP CALC FORMULAS (run once to install Average / Final Score /
//    Indicator formulas into every class sheet; replicates last-year logic) ──
function setupCalcFormulas(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var results = {};
  Object.keys(SCORE_SHEETS).forEach(function(cls) {
    try {
      var ss = SpreadsheetApp.openById(SCORE_SHEETS[cls]);
      var report = {};
      // Chapter tabs: I=Average(9), J=Final Score(10), K=Indicator(11);
      // score inputs are D:H (Task 1-3, Formative, Summative). Batched writes.
      ['Chapter_1', 'Chapter_2', 'Chapter_3'].forEach(function(tab) {
        var sh = ss.getSheetByName(tab);
        if (!sh) { report[tab] = 'missing'; return; }
        var n = _countStudentRows(sh);
        if (!n) { report[tab] = '0 rows'; return; }
        var avg = [], fin = [], ind = [];
        for (var i = 0; i < n; i++) {
          var r = i + 2;
          avg.push(['=IFERROR(ROUND(AVERAGE(D' + r + ':H' + r + '),2),"")']);
          fin.push(['=IF(I' + r + '="","",ROUND(I' + r + ',0))']);
          ind.push(['=IF(J' + r + '="","",IF(J' + r + '>=75,"Safe","Not Safe"))']);
        }
        sh.getRange(2, 9, n, 1).setFormulas(avg);
        sh.getRange(2, 10, n, 1).setFormulas(fin);
        sh.getRange(2, 11, n, 1).setFormulas(ind);
        report[tab] = n + ' rows';
      });
      // Activeness Indicator: K=11, tiered off Total (J=10). Blank at 0. Batched.
      var act = ss.getSheetByName('Activeness');
      if (act) {
        var an = _countStudentRows(act);
        if (an) {
          var arr = [];
          for (var j = 0; j < an; j++) {
            var rr = j + 2;
            arr.push(['=IF(J' + rr + '=0,"",' +
              'IF(J' + rr + '>=200,"Pro Maxxing Active (+35 bonus)",' +
              'IF(J' + rr + '>=150,"Ultra Pro Active (+30 bonus)",' +
              'IF(J' + rr + '>=100,"Plus Ultra Active (+25 bonus)",' +
              'IF(J' + rr + '>=75,"Hyperactive (+20 bonus)",' +
              'IF(J' + rr + '>=50,"Super Active (+15 bonus)",' +
              'IF(J' + rr + '>=25,"Active (+10 bonus)",' +
              'IF(J' + rr + '>=10,"Okay (+5 bonus)","Not Active (+0 bonus)"))))))))']);
          }
          act.getRange(2, 11, an, 1).setFormulas(arr);
          report['Activeness'] = an + ' rows';
        }
      }
      SpreadsheetApp.flush();
      results[cls] = report;
    } catch (e) { results[cls] = 'error: ' + e.message; }
  });
  return { success: true, results: results };
}

// ── SETUP ROSTER (write real student names into every roster tab) ──
// names = JSON array of student names in No order (index 0 -> student #1).
// Writes names as VALUES to column B of each tab the app reads, so it is
// re-runnable to update rosters later.
function setupRoster(username, password, className, namesJson) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found: ' + className };
  var names;
  try { names = JSON.parse(namesJson || '[]'); } catch (e) { return { success: false, error: 'Bad names JSON' }; }
  if (!names.length) return { success: false, error: 'No names provided' };
  var ss = SpreadsheetApp.openById(sheetId);
  var vals = names.map(function(x) { return [String(x == null ? '' : x).trim()]; });
  var n = vals.length;
  var report = { allTabs: ss.getSheets().map(function(s) { return s.getName(); }) };
  var targets = ['Scores', 'Chapter_1', 'Chapter_2', 'Chapter_3', 'Activeness', 'Task_Status', 'Strike'];
  var firstName = ss.getSheets()[0].getName();
  if (targets.indexOf(firstName) === -1) targets.unshift(firstName);
  targets.forEach(function(tab) {
    var sh = ss.getSheetByName(tab);
    if (!sh) { report[tab] = 'missing'; return; }
    sh.getRange(2, 2, n, 1).setValues(vals);
    report[tab] = n + ' names';
  });
  // Keep the separate attendance sheet's roster (Name column) in sync so the
  // attendance pages show the same students in the same order. Names only —
  // No / Nickname / meeting columns are left untouched.
  try {
    var attId = PropertiesService.getScriptProperties().getProperty('ATTENDANCE_SHEET_ID');
    if (attId) {
      var attTab = SpreadsheetApp.openById(attId).getSheetByName(className);
      if (attTab) { attTab.getRange(2, 2, n, 1).setValues(vals); report.attendance = n + ' names'; }
      else report.attendance = 'tab missing';
    } else report.attendance = 'no attendance sheet';
  } catch (e) { report.attendance = 'error: ' + e.message; }
  SpreadsheetApp.flush();
  return { success: true, className: className, count: n, report: report };
}

// ── SETUP ACTIVENESS XP (new weights + auto Doing Task) ──
// Activeness cols: D Vocabulary Box, E Answering, F Presenting, G Doing Task,
// H Helping Hand, I Quiz, J Total, K Indicator. XP weights: VB×2, Ans×5,
// Pres×20, Doing Task×20, Helping×20, Quiz×10. Doing Task auto-counts the
// TRUE cells in each student's Task_Status row.
function setupActivenessXP(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var results = {};
  Object.keys(SCORE_SHEETS).forEach(function(cls) {
    try {
      var act = SpreadsheetApp.openById(SCORE_SHEETS[cls]).getSheetByName('Activeness');
      if (!act) { results[cls] = 'no Activeness'; return; }
      act.getRange(1, 7).setValue('Doing Task');
      var n = _countStudentRows(act);
      if (!n) { results[cls] = '0 rows'; return; }
      var dt = [], tot = [];
      for (var i = 0; i < n; i++) {
        var r = i + 2;
        dt.push(['=COUNTIF(Task_Status!D' + r + ':AZ' + r + ', TRUE)']);
        tot.push(['=D' + r + '*2+E' + r + '*5+F' + r + '*20+G' + r + '*20+H' + r + '*20+I' + r + '*10']);
      }
      act.getRange(2, 7, n, 1).setFormulas(dt);
      act.getRange(2, 10, n, 1).setFormulas(tot);
      SpreadsheetApp.flush();
      results[cls] = n + ' rows';
    } catch (e) { results[cls] = 'error: ' + e.message; }
  });
  return { success: true, results: results };
}

// ── SETUP TASK COLUMNS (3 tasks per chapter) ──
// Rewrites each class's Task_Status header row (from col D) to C{ch}T{1..3}.
// Grade X classes (XE*) get 3 chapters (9 tasks); Grade XI classes (XIF*)
// get 2 chapters (6 tasks). Task columns are boolean (blank = not submitted).
// Frontend reads these headers dynamically, so this is the single source of
// truth for how many tasks/chapters exist per grade.
function setupTaskColumns(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var results = {};
  Object.keys(SCORE_SHEETS).forEach(function(cls) {
    try {
      var sh = SpreadsheetApp.openById(SCORE_SHEETS[cls]).getSheetByName(TASK_SHEET_NAME);
      if (!sh) { results[cls] = 'no Task_Status'; return; }
      var chapters = /^XI/i.test(cls) ? 2 : 3;  // Grade XI = 2 chapters, Grade X = 3
      var cols = [];
      for (var ch = 1; ch <= chapters; ch++) for (var t = 1; t <= 3; t++) cols.push('C' + ch + 'T' + t);
      // Clear the whole task area (headers + any old values) from col D onward,
      // then write the fresh header row. No task data exists yet, so this is safe.
      var maxCol = sh.getMaxColumns();
      if (maxCol >= 4) sh.getRange(1, 4, sh.getMaxRows(), maxCol - 3).clearContent();
      sh.getRange(1, 4, 1, cols.length).setValues([cols]);
      SpreadsheetApp.flush();
      results[cls] = cols.join(',');
    } catch (e) { results[cls] = 'error: ' + e.message; }
  });
  return { success: true, results: results };
}

// ── TASK PHOTO SUBMISSIONS ────────────────────────────────────
// Students upload a photo of their finished task; it is stored privately in a
// Drive folder (never shared), the task is auto-checked in Task_Status, and the
// submission is queued as "pending" for the teacher to view + clear. The teacher
// grades the score manually elsewhere. Data model: a central "EQ Task
// Submissions" spreadsheet (Submissions tab) + a private Drive folder, both
// referenced via Script Properties.

var SUBMISSIONS_TAB = 'Submissions';

function _tasksForClass(className) {
  var chapters = /^XI/i.test(className) ? 2 : 3;
  var keys = [];
  for (var ch = 1; ch <= chapters; ch++) for (var t = 1; t <= 3; t++) keys.push('C' + ch + 'T' + t);
  return keys;
}
function _submissionsTab() {
  var id = PropertiesService.getScriptProperties().getProperty('SUBMISSIONS_SHEET_ID');
  if (!id) return null;
  try { return SpreadsheetApp.openById(id).getSheetByName(SUBMISSIONS_TAB); } catch (e) { return null; }
}
function _fmtDateTime(v) {
  if (v instanceof Date) return Utilities.formatDate(v, Session.getScriptTimeZone(), 'yyyy-MM-dd HH:mm');
  return String(v == null ? '' : v);
}
function _markTask(className, studentNo, taskKey, value) {
  try {
    var ts = SpreadsheetApp.openById(SCORE_SHEETS[className]).getSheetByName(TASK_SHEET_NAME);
    if (!ts) return;
    var td = ts.getDataRange().getValues(), th = td[0], tr = -1;
    for (var i = 1; i < td.length; i++) { if (String(td[i][0]) === String(studentNo)) { tr = i; break; } }
    if (tr < 0) return;
    for (var c = 3; c < th.length; c++) {
      if (String(th[c]).trim().toUpperCase() === taskKey) { ts.getRange(tr + 1, c + 1).setValue(value); return; }
    }
  } catch (e) {}
}

// ⇩⇩⇩ RUN THIS ONE FROM THE APPS SCRIPT EDITOR (once) ⇩⇩⇩
// Select "AUTHORIZE_AND_SETUP_SUBMISSIONS" in the function dropdown and click Run.
// It takes NO password (so it actually runs, unlike setupSubmissions which the
// website calls with a password). The first run pops the Google permission
// screen for Drive — click Allow — then it creates the private photo folder +
// submissions sheet. Safe to run again; it reuses what already exists.
function AUTHORIZE_AND_SETUP_SUBMISSIONS() {
  return _doSetupSubmissions();
}

// One-time: create the submissions spreadsheet + private Drive folder.
// Called by the website (password-gated) via the setupSubmissions action.
function setupSubmissions(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  return _doSetupSubmissions();
}

function _doSetupSubmissions() {
  var props = PropertiesService.getScriptProperties();
  var report = {};
  var sheetId = props.getProperty('SUBMISSIONS_SHEET_ID');
  var ss = null;
  if (sheetId) { try { ss = SpreadsheetApp.openById(sheetId); } catch (e) { ss = null; } }
  if (!ss) { ss = SpreadsheetApp.create('EQ Task Submissions 2026-2027'); props.setProperty('SUBMISSIONS_SHEET_ID', ss.getId()); report.createdSheet = ss.getId(); }
  else report.sheet = ss.getId();
  var tab = ss.getSheetByName(SUBMISSIONS_TAB);
  if (!tab) {
    tab = ss.getSheets()[0]; tab.setName(SUBMISSIONS_TAB);
    tab.getRange(1, 1, 1, 10).setValues([['SubId', 'Timestamp', 'Class', 'StudentNo', 'StudentName', 'Task', 'FileId', 'FileName', 'Status', 'ReviewedAt']]);
    tab.setFrozenRows(1);
    report.createdTab = true;
  }
  var folderId = props.getProperty('SUBMISSIONS_FOLDER_ID');
  var folder = null;
  if (folderId) { try { folder = DriveApp.getFolderById(folderId); } catch (e) { folder = null; } }
  if (!folder) { folder = DriveApp.createFolder('EQ Task Submission Photos'); props.setProperty('SUBMISSIONS_FOLDER_ID', folder.getId()); report.createdFolder = folder.getId(); }
  else report.folder = folder.getId();
  return { success: true, report: report };
}

var SUBMISSION_MAX_PHOTOS = 6;

// Trash a comma-separated list of Drive file ids (used when replacing/clearing).
function _trashFiles(str) {
  String(str || '').split(',').forEach(function (id) {
    id = id.trim();
    if (id) { try { DriveApp.getFileById(id).setTrashed(true); } catch (e) {} }
  });
}

// Student submits (or replaces) a task's photos. No teacher auth — students use
// it. `images` is an array of base64 strings (1..SUBMISSION_MAX_PHOTOS); a single
// `image` is still accepted for backward-compat. All photos for a task live in
// one row: FileId / FileName hold comma-joined lists.
function submitTaskPhoto(className, studentNo, taskKey, images, mimeType, singleImage) {
  if (!SCORE_SHEETS[className]) return { success: false, error: 'Class not found' };
  taskKey = String(taskKey || '').trim().toUpperCase();
  if (_tasksForClass(className).indexOf(taskKey) === -1) return { success: false, error: 'Invalid task for this class' };
  if ((!images || !images.length) && singleImage) images = [singleImage];
  if (!images || !images.length) return { success: false, error: 'No image received' };
  if (images.length > SUBMISSION_MAX_PHOTOS) images = images.slice(0, SUBMISSION_MAX_PHOTOS);
  var name = _studentName(SpreadsheetApp.openById(SCORE_SHEETS[className]), studentNo);
  if (!name) return { success: false, error: 'Student not found' };
  var props = PropertiesService.getScriptProperties();
  var folderId = props.getProperty('SUBMISSIONS_FOLDER_ID');
  var tab = _submissionsTab();
  if (!folderId || !tab) return { success: false, error: 'Submissions not set up' };
  var folder = DriveApp.getFolderById(folderId);
  mimeType = mimeType || 'image/jpeg';
  var ext = mimeType.indexOf('png') > -1 ? 'png' : 'jpg';
  var now = new Date();
  var ids = [], names = [];
  for (var k = 0; k < images.length; k++) {
    if (!images[k]) continue;
    var safeName = className + '_no' + studentNo + '_' + taskKey + '_' + now.getTime() + '_' + (k + 1) + '.' + ext;
    var file = folder.createFile(Utilities.newBlob(Utilities.base64Decode(images[k]), mimeType, safeName));
    ids.push(file.getId()); names.push(safeName);
  }
  if (!ids.length) return { success: false, error: 'No image received' };
  var idStr = ids.join(','), nameStr = names.join(',');
  // Replace any existing not-yet-reviewed submission for the same task.
  var data = tab.getDataRange().getValues(), rowIdx = -1;
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][2]) === String(className) && String(data[r][3]) === String(studentNo) &&
        String(data[r][5]).toUpperCase() === taskKey && String(data[r][8]) !== 'reviewed') { rowIdx = r; break; }
  }
  if (rowIdx > -1) {
    _trashFiles(String(data[rowIdx][6] || ''));
    tab.getRange(rowIdx + 1, 2, 1, 9).setValues([[now, className, studentNo, name, taskKey, idStr, nameStr, 'pending', '']]);
  } else {
    var subId = 'S' + now.getTime() + Math.floor(Math.random() * 1000);
    tab.appendRow([subId, now, className, studentNo, name, taskKey, idStr, nameStr, 'pending', '']);
  }
  _markTask(className, studentNo, taskKey, true);
  SpreadsheetApp.flush();
  return { success: true, count: ids.length };
}

// Teacher: list all submissions (newest first) + pending count.
function getTaskSubmissions(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var tab = _submissionsTab();
  if (!tab) return { success: true, submissions: [], pending: 0 };
  var data = tab.getDataRange().getValues(), subs = [];
  for (var r = 1; r < data.length; r++) {
    if (!data[r][0]) continue;
    var idStr = String(data[r][6] || '');
    var fileIds = idStr ? idStr.split(',').map(function (s) { return s.trim(); }).filter(Boolean) : [];
    subs.push({ subId: String(data[r][0]), timestamp: _fmtDateTime(data[r][1]), className: String(data[r][2]),
      studentNo: data[r][3], studentName: String(data[r][4]), task: String(data[r][5]),
      fileId: fileIds[0] || '', fileIds: fileIds, photoCount: fileIds.length,
      status: String(data[r][8] || 'pending'), reviewedAt: _fmtDateTime(data[r][9]) });
  }
  subs.reverse();
  return { success: true, submissions: subs, pending: subs.filter(function (s) { return s.status === 'pending'; }).length };
}

// Teacher: fetch one photo as base64 so it renders inline (file stays private).
function getSubmissionPhoto(username, password, fileId) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  try {
    var blob = DriveApp.getFileById(fileId).getBlob();
    return { success: true, mimeType: blob.getContentType(), dataBase64: Utilities.base64Encode(blob.getBytes()) };
  } catch (e) { return { success: false, error: 'Photo not found' }; }
}

// Teacher: clear a submission. decision 'reviewed' (done) or 'rejected' (un-check
// the task so the student resubmits).
function reviewTaskSubmission(username, password, subId, decision) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var tab = _submissionsTab();
  if (!tab) return { success: false, error: 'Submissions not set up' };
  var data = tab.getDataRange().getValues();
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(subId)) {
      var status = decision === 'rejected' ? 'rejected' : 'reviewed';
      tab.getRange(r + 1, 9).setValue(status);
      tab.getRange(r + 1, 10).setValue(new Date());
      if (decision === 'rejected') _markTask(String(data[r][2]), data[r][3], String(data[r][5]).toUpperCase(), false);
      SpreadsheetApp.flush();
      return { success: true, status: status };
    }
  }
  return { success: false, error: 'Submission not found' };
}

// Student: their own submission statuses per task { C1T1:'pending', ... }.
function getMySubmissions(className, studentNo) {
  var tab = _submissionsTab();
  if (!tab) return { success: true, statuses: {} };
  var data = tab.getDataRange().getValues(), out = {};
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][2]) === String(className) && String(data[r][3]) === String(studentNo))
      out[String(data[r][5]).toUpperCase()] = String(data[r][8] || 'pending');
  }
  return { success: true, statuses: out };
}

// Teacher: clear submissions. onlyReviewed='true' clears just the already-checked
// ones; otherwise clears ALL. For each cleared row it un-checks the task and
// trashes the photo file, then removes the row. Use at semester-end or to tidy up.
function adminClearSubmissions(username, password, onlyReviewed) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var tab = _submissionsTab();
  if (!tab) return { success: false, error: 'Submissions not set up' };
  var data = tab.getDataRange().getValues();
  var reviewedOnly = String(onlyReviewed) === 'true';
  var cleared = 0;
  for (var r = data.length - 1; r >= 1; r--) {
    if (!data[r][0]) continue;
    if (reviewedOnly && String(data[r][8]) !== 'reviewed') continue;
    _trashFiles(String(data[r][6] || ''));
    _markTask(String(data[r][2]), data[r][3], String(data[r][5]).toUpperCase(), false);
    tab.deleteRow(r + 1);
    cleared++;
  }
  SpreadsheetApp.flush();
  return { success: true, cleared: cleared };
}

// ── VOCABULARY BOX ────────────────────────────────────────────
var VOCAB_TAB = 'Vocabulary';
var VOCAB_WEEKLY_CAP = 30;

function _vocabSheet(ss) {
  var sh = ss.getSheetByName(VOCAB_TAB);
  if (!sh) {
    sh = ss.insertSheet(VOCAB_TAB);
    sh.getRange(1, 1, 1, 7).setValues([['No', 'Name', 'English', 'Indonesian', 'Timestamp', 'Status', 'Type']]);
  }
  return sh;
}

function _studentName(ss, studentNo) {
  var first = ss.getSheets()[0];
  var data = first.getRange(1, 1, first.getLastRow(), 2).getValues();
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) return String(data[r][1] || '').trim();
  }
  return '';
}

function getVocabulary(className, studentNo) {
  var sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  var sh = _vocabSheet(SpreadsheetApp.openById(sheetId));
  var data = sh.getDataRange().getValues();
  var words = [], weekMs = 7 * 24 * 60 * 60 * 1000, weekAgo = Date.now() - weekMs, weekCount = 0, oldestInWindow = 0;
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][0]) !== String(studentNo)) continue;
    var ts = data[r][4] ? new Date(data[r][4]).getTime() : 0;
    if (ts >= weekAgo) { weekCount++; if (!oldestInWindow || ts < oldestInWindow) oldestInWindow = ts; }
    words.push({ english: String(data[r][2] || ''), indonesian: String(data[r][3] || ''), at: data[r][4],
                 status: String(data[r][5] || ''), type: String(data[r][6] || '') });
  }
  var remaining = Math.max(0, VOCAB_WEEKLY_CAP - weekCount);
  // When the weekly cap is full, the next slot frees when the oldest word in the
  // 7-day window ages out — that's the countdown target the student sees.
  var nextSlotAt = (remaining === 0 && oldestInWindow) ? (oldestInWindow + weekMs) : 0;
  return { words: words, total: words.length, weekCount: weekCount,
           weeklyCap: VOCAB_WEEKLY_CAP, weeklyRemaining: remaining, nextSlotAt: nextSlotAt };
}

// Judge whether `id` is an acceptable Indonesian meaning of English `en`, using
// the free built-in Google Translate. Two-way check so synonyms pass: accept if
// the student's answer ~ the machine translation, OR translating the student's
// answer back to English ~ the original word. Returns 'correct' | 'wrong' |
// 'unknown' (unknown when translation is unavailable — never shown as wrong).
function _checkMeaning(en, id) {
  en = String(en || '').trim(); id = String(id || '').trim();
  if (!en || !id) return 'unknown';
  try {
    var norm = function (s) { return String(s).toLowerCase().replace(/[^a-z\s]/g, ' ').replace(/\s+/g, ' ').trim(); };
    var near = function (a, b) { if (!a || !b) return false; return a === b || (' ' + a + ' ').indexOf(' ' + b + ' ') > -1 || (' ' + b + ' ').indexOf(' ' + a + ' ') > -1; };
    var sid = norm(id), sen = norm(en);
    if (near(sid, norm(LanguageApp.translate(en, 'en', 'id')))) return 'correct';
    if (near(norm(LanguageApp.translate(id, 'id', 'en')), sen)) return 'correct';
    return 'wrong';
  } catch (e) { return 'unknown'; }
}

// Student-triggered pre-submit check: judge typed pairs WITHOUT saving them, so a
// student can verify before submitting. pairs = JSON [{english, indonesian}, ...].
function checkMeanings(pairsJson) {
  var pairs;
  try { pairs = JSON.parse(pairsJson || '[]'); } catch (e) { return { success: false, error: 'Bad pairs' }; }
  if (!pairs.length) return { success: true, results: [] };
  if (pairs.length > 10) pairs = pairs.slice(0, 10);
  var results = pairs.map(function (p) {
    var en = String((p && p.english) || ''), id = String((p && p.indonesian) || '');
    return { english: en, indonesian: id, status: _checkMeaning(en, id) };
  });
  return { success: true, results: results };
}

// Teacher overview: every student in the class + their vocabulary in ONE call,
// so the teacher can see who submitted (and how much) without clicking each one.
function getClassVocabulary(className) {
  var sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  var ss = SpreadsheetApp.openById(sheetId);
  var first = ss.getSheets()[0];
  var rdata = first.getRange(1, 1, first.getLastRow(), 3).getValues();
  var vdata = _vocabSheet(ss).getDataRange().getValues();
  var byNo = {};
  for (var r = 1; r < vdata.length; r++) {
    var sn = String(vdata[r][0]); if (!sn) continue;
    if (!byNo[sn]) byNo[sn] = { words: [], c: 0, w: 0, u: 0 };
    var st = String(vdata[r][5] || '');
    byNo[sn].words.push({ english: String(vdata[r][2] || ''), indonesian: String(vdata[r][3] || ''), at: vdata[r][4], status: st, type: String(vdata[r][6] || '') });
    if (st === 'correct') byNo[sn].c++; else if (st === 'wrong') byNo[sn].w++; else byNo[sn].u++;
  }
  var students = [];
  for (var i = 1; i < rdata.length; i++) {
    var no = rdata[i][0], name = rdata[i][1];
    if (!no || !String(name).trim()) break;
    var v = byNo[String(no)] || { words: [], c: 0, w: 0, u: 0 };
    students.push({ no: no, name: String(name).trim(), nickname: String(rdata[i][2] || '').trim(),
      total: v.words.length, correct: v.c, wrong: v.w, unchecked: v.u, words: v.words });
  }
  return { success: true, students: students };
}

// Student-triggered: check any of this student's not-yet-checked words and store
// the verdict in column F. Capped per call to avoid timeouts; the frontend calls
// again if words remain unchecked.
function checkVocabulary(className, studentNo) {
  var sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  var ss = SpreadsheetApp.openById(sheetId);
  var sh = _vocabSheet(ss);
  var data = sh.getDataRange().getValues();
  var checked = 0, LIMIT = 10;
  for (var r = 1; r < data.length && checked < LIMIT; r++) {
    if (String(data[r][0]) !== String(studentNo)) continue;
    var status = String(data[r][5] || '').trim();
    if (status === 'correct' || status === 'wrong') continue;
    sh.getRange(r + 1, 6).setValue(_checkMeaning(data[r][2], data[r][3]));
    checked++;
  }
  if (checked) SpreadsheetApp.flush();
  var res = getVocabulary(className, studentNo);
  res.checked = checked;
  return res;
}

// Student-triggered: delete one of their own words and roll back its XP (the
// Vocabulary Box counter in Activeness, ×2 = XP). No teacher auth — self-service.
function removeVocabulary(className, studentNo, english) {
  var sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  if (!studentNo || !english) return { success: false, error: 'Missing word' };
  var ss = SpreadsheetApp.openById(sheetId);
  var sh = _vocabSheet(ss);
  var data = sh.getDataRange().getValues();
  var target = String(english).trim().toLowerCase(), removed = 0;
  for (var r = data.length - 1; r >= 1; r--) {
    if (String(data[r][0]) === String(studentNo) && String(data[r][2] || '').trim().toLowerCase() === target) {
      sh.deleteRow(r + 1); removed++;
    }
  }
  if (removed) {
    var act = ss.getSheetByName('Activeness');
    if (act) {
      var adata = act.getDataRange().getValues();
      for (var ar = 1; ar < adata.length; ar++) {
        if (String(adata[ar][0]) === String(studentNo)) {
          act.getRange(ar + 1, 4).setValue(Math.max(0, Number(adata[ar][3] || 0) - removed));
          break;
        }
      }
    }
    SpreadsheetApp.flush();
  }
  var res = getVocabulary(className, studentNo);
  res.removed = removed; res.success = true;
  return res;
}

// Student self-submit (no teacher auth). Guarded by: max 10/call, no duplicate
// English word per student, and a 30-word rolling weekly cap. Awards 2 XP/word
// by bumping the Vocabulary Box counter in the Activeness sheet.
function addVocabulary(className, studentNo, wordsJson) {
  var sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  if (!studentNo) return { success: false, error: 'No student selected' };
  var words;
  try { words = JSON.parse(wordsJson || '[]'); } catch (e) { return { success: false, error: 'Bad words' }; }
  if (!words.length) return { success: false, error: 'No words submitted' };
  if (words.length > 10) words = words.slice(0, 10);
  var ss = SpreadsheetApp.openById(sheetId);
  var sh = _vocabSheet(ss);
  var data = sh.getDataRange().getValues();
  var existing = {}, weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000, weekCount = 0;
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][0]) !== String(studentNo)) continue;
    existing[String(data[r][2] || '').trim().toLowerCase()] = true;
    var ts = data[r][4] ? new Date(data[r][4]).getTime() : 0;
    if (ts >= weekAgo) weekCount++;
  }
  var name = _studentName(ss, studentNo), now = new Date();
  var accepted = [], rejected = [], seen = {}, remaining = VOCAB_WEEKLY_CAP - weekCount;
  var VALID_TYPES = { noun: 1, verb: 1, adjective: 1, adverb: 1, other: 1 };
  words.forEach(function(w) {
    var en = String((w && w.english) || '').trim();
    var id = String((w && w.indonesian) || '').trim();
    var type = String((w && w.type) || '').trim().toLowerCase();
    if (!VALID_TYPES[type]) type = '';
    var key = en.toLowerCase();
    if (!en || !id) { rejected.push({ english: en, reason: 'empty' }); return; }
    if (existing[key] || seen[key]) { rejected.push({ english: en, reason: 'duplicate' }); return; }
    if (accepted.length >= remaining) { rejected.push({ english: en, reason: 'weekly-cap' }); return; }
    seen[key] = true;
    accepted.push([studentNo, name, en, id, now, '', type]);  // cols A–G (F=Status blank, G=Type)
  });
  if (accepted.length) {
    sh.getRange(sh.getLastRow() + 1, 1, accepted.length, 7).setValues(accepted);
    var act = ss.getSheetByName('Activeness');
    if (act) {
      var adata = act.getDataRange().getValues();
      for (var ar = 1; ar < adata.length; ar++) {
        if (String(adata[ar][0]) === String(studentNo)) {
          act.getRange(ar + 1, 4).setValue(Number(adata[ar][3] || 0) + accepted.length);
          break;
        }
      }
    }
    SpreadsheetApp.flush();
  }
  return { success: true, added: accepted.length, xp: accepted.length * 2,
           rejected: rejected, weeklyRemaining: Math.max(0, remaining - accepted.length) };
}

// Teacher-only: wipe a class's vocabulary submissions (e.g. new semester).
// Does NOT reset the Vocabulary Box XP counter in Activeness.
function clearVocabulary(username, password, className) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  var sh = SpreadsheetApp.openById(sheetId).getSheetByName(VOCAB_TAB);
  if (sh && sh.getLastRow() > 1) sh.getRange(2, 1, sh.getLastRow() - 1, sh.getLastColumn()).clearContent();
  return { success: true };
}

// ── ATTENDANCE (new-period sheet) ─────────────────────────────
// Creates ONE new spreadsheet "EQ Attendance 2026-2027" with a tab per class
// (Grade 10 XE1-5 + Grade 11 XIF7-9), each filled with that class's roster.
// Meeting/date columns get added later (col D onward) as attendance is taken.
// Stores the new spreadsheet id in Script Property ATTENDANCE_SHEET_ID.
function setupAttendanceSheet(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var props = PropertiesService.getScriptProperties();
  var ss = null;
  var existing = props.getProperty('ATTENDANCE_SHEET_ID');
  if (existing) { try { ss = SpreadsheetApp.openById(existing); } catch (e) { ss = null; } }
  if (!ss) {
    ss = SpreadsheetApp.create('EQ Attendance 2026-2027');
    props.setProperty('ATTENDANCE_SHEET_ID', ss.getId());
  }
  var report = {};
  Object.keys(SCORE_SHEETS).forEach(function(cls) {
    var tab = ss.getSheetByName(cls) || ss.insertSheet(cls);
    tab.clear();
    var src = SpreadsheetApp.openById(SCORE_SHEETS[cls]).getSheets()[0].getDataRange().getValues();
    var rows = [['No', 'Name', 'Nickname']];
    for (var r = 1; r < src.length; r++) {
      var no = src[r][0], name = src[r][1];
      if (!no || !name || String(name).trim() === '') break;
      rows.push([no, String(name).trim(), String(src[r][2] || '').trim()]);
    }
    tab.getRange(1, 1, rows.length, 3).setValues(rows);
    tab.setFrozenRows(1);
    report[cls] = (rows.length - 1) + ' students';
  });
  var def = ss.getSheetByName('Sheet1');
  if (def && !SCORE_SHEETS['Sheet1'] && ss.getSheets().length > 1) ss.deleteSheet(def);
  SpreadsheetApp.flush();
  return { success: true, spreadsheetId: ss.getId(), url: ss.getUrl(), report: report };
}

// Attendance read for the student pages. Returns { students, meetings } in the
// shape the frontend expects: meetings = [{number, date}], each student has an
// attendance map { m1:'Present', ... }. Empty (but valid) until meetings exist.
function getClassData(className) {
  var id = PropertiesService.getScriptProperties().getProperty('ATTENDANCE_SHEET_ID');
  if (!id) return { students: [], meetings: [] };
  var ss;
  try { ss = SpreadsheetApp.openById(id); } catch (e) { return { students: [], meetings: [] }; }
  var tab = ss.getSheetByName(className);
  if (!tab) return { students: [], meetings: [] };
  var data = tab.getDataRange().getValues();
  if (!data.length) return { students: [], meetings: [] };
  var headers = data[0];
  var meetings = [];
  for (var c = 3; c < headers.length; c++) {
    if (headers[c] !== '' && headers[c] !== null) meetings.push({ number: c - 2, date: _fmtDate(headers[c]) });
  }
  var students = [];
  for (var r = 1; r < data.length; r++) {
    var no = data[r][0], name = data[r][1];
    if (!no || !name || String(name).trim() === '') break;
    var att = {};
    meetings.forEach(function(m) { var v = data[r][m.number + 2]; if (v !== '' && v !== null) att['m' + m.number] = String(v); });
    students.push({ no: no, name: String(name).trim(), nickname: String(data[r][2] || '').trim(), attendance: att });
  }
  return { students: students, meetings: meetings };
}

// Format a sheet cell that may be a Date or string into a plain yyyy-MM-dd.
function _fmtDate(v) {
  if (v instanceof Date) return Utilities.formatDate(v, Session.getScriptTimeZone(), 'yyyy-MM-dd');
  return String(v == null ? '' : v);
}

// Teacher writes attendance for one class on one date. records = JSON array of
// {no, status}. Finds the column whose header === date (or appends a new one),
// then writes each student's status. Blank status clears (removes) that mark.
function saveAttendance(username, password, className, date, recordsJson) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  if (!date) return { success: false, error: 'No date provided' };
  var id = PropertiesService.getScriptProperties().getProperty('ATTENDANCE_SHEET_ID');
  if (!id) return { success: false, error: 'Attendance sheet not set up' };
  var tab = SpreadsheetApp.openById(id).getSheetByName(className);
  if (!tab) return { success: false, error: 'Class not found: ' + className };
  var records;
  try { records = JSON.parse(recordsJson || '[]'); } catch (e) { return { success: false, error: 'Bad records' }; }
  var data = tab.getDataRange().getValues();
  var headers = data[0];
  var col = -1;
  for (var c = 3; c < headers.length; c++) { if (_fmtDate(headers[c]) === String(date)) { col = c; break; } }
  if (col === -1) { col = Math.max(headers.length, 3); tab.getRange(1, col + 1).setValue(date); }
  var n = 0;
  for (var r = 1; r < data.length; r++) { if (data[r][0] === '' || data[r][0] === null) break; n++; }
  if (!n) return { success: false, error: 'No students' };
  var existing = (col < headers.length) ? tab.getRange(2, col + 1, n, 1).getValues() : null;
  var out = [], noToIdx = {};
  for (var i = 0; i < n; i++) { out.push([existing ? existing[i][0] : '']); noToIdx[String(data[i + 1][0])] = i; }
  var written = 0;
  records.forEach(function(rec) {
    var idx = noToIdx[String(rec && rec.no)];
    if (idx !== undefined) { out[idx] = [String(rec.status == null ? '' : rec.status)]; written++; }
  });
  tab.getRange(2, col + 1, n, 1).setValues(out);
  SpreadsheetApp.flush();
  return { success: true, meetingNumber: col - 2, date: date, written: written };
}

// Teacher deletes a whole meeting (date column) from a class.
function removeAttendanceMeeting(username, password, className, date) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var id = PropertiesService.getScriptProperties().getProperty('ATTENDANCE_SHEET_ID');
  if (!id) return { success: false, error: 'Attendance sheet not set up' };
  var tab = SpreadsheetApp.openById(id).getSheetByName(className);
  if (!tab) return { success: false, error: 'Class not found: ' + className };
  var lastCol = tab.getLastColumn();
  var headers = tab.getRange(1, 1, 1, lastCol).getValues()[0];
  for (var c = 3; c < headers.length; c++) {
    if (_fmtDate(headers[c]) === String(date)) { tab.deleteColumn(c + 1); SpreadsheetApp.flush(); return { success: true }; }
  }
  return { success: false, error: 'Meeting not found' };
}

// Count contiguous student rows (col A = No) starting at row 2.
function _countStudentRows(sh) {
  var last = sh.getLastRow();
  if (last < 2) return 0;
  var col = sh.getRange(2, 1, last - 1, 1).getValues();
  var n = 0;
  for (var i = 0; i < col.length; i++) {
    if (col[i][0] === '' || col[i][0] === null) break;
    n++;
  }
  return n;
}

// ── INCREMENT ACTIVENESS ──────────────────────────────────────
function incrementActiveness(username, password, className, studentNo, column, delta) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Activeness');
  if (!sheet) return { success: false, error: 'Activeness sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];

  // Find column index
  let colIndex = -1;
  for (let c = 0; c < headers.length; c++) {
    if (String(headers[c]).trim() === column) { colIndex = c; break; }
  }
  if (colIndex === -1) return { success: false, error: 'Column not found: ' + column };

  // Find student row
  let rowIndex = -1;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) { rowIndex = r; break; }
  }
  if (rowIndex === -1) return { success: false, error: 'Student not found' };

  const current  = Number(data[rowIndex][colIndex] || 0);
  const newValue = Math.max(0, current + Number(delta));
  sheet.getRange(rowIndex + 1, colIndex + 1).setValue(newValue);
  return { success: true, newValue };
}

// ── GET STRIKES FOR ONE STUDENT ───────────────────────────────
function getStrikeColMap(headers) {
  const m = {};
  headers.forEach((h, i) => {
    const k = String(h||'').trim().toLowerCase().replace(/\s+/g,'');
    m[k] = i;
  });
  return m;
}

function getStudentStrikes(className, studentNo) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Strike');
  if (!sheet) return { error: 'Strike sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];
  const m       = getStrikeColMap(headers);
  let row = null;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) { row = data[r]; break; }
  }
  if (!row) return { error: 'Student not found' };
  const strikes = [];
  for (let s = 1; s <= 4; s++) {
    const ci = m['strike'+s];
    if (ci !== undefined && (row[ci] === true || row[ci] === 'TRUE' || row[ci] === 1)) strikes.push(s);
  }
  const totalCi  = m['total'];
  const dateCi   = m['date/time'] !== undefined ? m['date/time'] : m['date'] !== undefined ? m['date'] : m['datetime'];
  const reasonCi = m['reason'];
  return {
    no: row[0], name: String(row[1]).trim(), nickname: String(row[2]||'').trim(),
    total: totalCi !== undefined ? Number(row[totalCi]||0) : strikes.length,
    date: dateCi !== undefined ? String(row[dateCi]||'') : '',
    reason: reasonCi !== undefined ? String(row[reasonCi]||'') : '',
    strikes
  };
}

// ── GET ALL STRIKES FOR A CLASS ───────────────────────────────
function getAllStrikes(username, password, className) {
  if (!authOk(username, password)) return { error: 'Unauthorized' };
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Strike');
  if (!sheet) return { error: 'Strike sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];
  const m       = getStrikeColMap(headers);
  const totalCi  = m['total'];
  const dateCi   = m['date/time'] !== undefined ? m['date/time'] : m['date'] !== undefined ? m['date'] : m['datetime'];
  const reasonCi = m['reason'];
  const students = [];
  for (let r = 1; r < data.length; r++) {
    const no = data[r][0], name = data[r][1];
    if (!no || !name || String(name).trim() === '') break;
    const strikes = [];
    for (let s = 1; s <= 4; s++) {
      const ci = m['strike'+s];
      if (ci !== undefined && (data[r][ci] === true || data[r][ci] === 'TRUE' || data[r][ci] === 1)) strikes.push(s);
    }
    students.push({
      no, name: String(name).trim(), nickname: String(data[r][2]||'').trim(),
      total: totalCi !== undefined ? Number(data[r][totalCi]||0) : strikes.length,
      date: dateCi !== undefined ? String(data[r][dateCi]||'') : '',
      reason: reasonCi !== undefined ? String(data[r][reasonCi]||'') : '',
      strikes
    });
  }
  return { students };
}

// ── ADD STRIKE ────────────────────────────────────────────────
function addStrike(username, password, className, studentNo, reason) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Strike');
  if (!sheet) return { success: false, error: 'Strike sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];

  // Find student row
  let rowIndex = -1;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) { rowIndex = r; break; }
  }
  if (rowIndex === -1) return { success: false, error: 'Student not found: ' + studentNo };

  // Find strike columns by scanning headers (handles "Strike 1", "Strike1", etc.)
  const strikeColIndexes = [];
  for (let c = 0; c < headers.length; c++) {
    const h = String(headers[c]||'').trim().toLowerCase().replace(/\s+/g,'');
    if (h === 'strike1' || h === 'strike2' || h === 'strike3' || h === 'strike4') {
      strikeColIndexes.push({ name: h, col: c });
    }
  }
  strikeColIndexes.sort((a,b) => a.name.localeCompare(b.name)); // strike1, strike2, strike3, strike4

  // Find date/time and reason columns
  let dateCol = -1, reasonCol = -1;
  for (let c = 0; c < headers.length; c++) {
    const h = String(headers[c]||'').trim().toLowerCase().replace(/[\s\/]+/g,'');
    if (h === 'datetime' || h === 'date' || h === 'datetime') dateCol = c;
    if (h === 'reason') reasonCol = c;
  }

  // Find first unchecked strike column
  let nextCol = -1;
  for (const s of strikeColIndexes) {
    const val = data[rowIndex][s.col];
    const isChecked = val === true || val === 'TRUE' || val === 1 || String(val).toLowerCase() === 'true';
    if (!isChecked) { nextCol = s.col; break; }
  }
  if (nextCol === -1) return { success: false, error: 'Maximum 4 strikes reached' };

  // Write checkbox (TRUE), date, reason
  const now = new Date();
  const pad = n => String(n).padStart(2,'0');
  const dateStr = now.getDate() + '/' + (now.getMonth()+1) + '/' + now.getFullYear() + ' ' + pad(now.getHours()) + ':' + pad(now.getMinutes());

  // Checkboxes in Google Sheets need the boolean true (not string)
  const strikeRange = sheet.getRange(rowIndex + 1, nextCol + 1);
  strikeRange.setValue(true);
  SpreadsheetApp.flush(); // force write immediately
  if (dateCol   >= 0) sheet.getRange(rowIndex + 1, dateCol   + 1).setValue(dateStr);
  if (reasonCol >= 0) sheet.getRange(rowIndex + 1, reasonCol + 1).setValue(reason);
  SpreadsheetApp.flush();

  // Count new total from what we just wrote
  let newTotal = 0;
  for (const s of strikeColIndexes) {
    const col = s.col === nextCol ? true : data[rowIndex][s.col];
    if (col === true || col === 'TRUE' || col === 1 || String(col).toLowerCase() === 'true') newTotal++;
  }

  return { 
    success: true, newTotal, date: dateStr, 
    debug: { rowIndex, nextCol, dateCol, reasonCol, strikeColIndexes, studentNo: String(studentNo) }
  };
}

// ── REMOVE STRIKE ─────────────────────────────────────────────
function removeStrike(username, password, className, studentNo) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Strike');
  if (!sheet) return { success: false, error: 'Strike sheet not found' };
  const data = sheet.getDataRange().getValues();
  let rowIndex = -1;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) { rowIndex = r; break; }
  }
  if (rowIndex === -1) return { success: false, error: 'Student not found' };
  // Find strike columns
  const strikeColIndexes2 = [];
  for (let c = 0; c < data[0].length; c++) {
    const h = String(data[0][c]||'').trim().toLowerCase().replace(/\s+/g,'');
    if (h === 'strike1' || h === 'strike2' || h === 'strike3' || h === 'strike4') {
      strikeColIndexes2.push({ name: h, col: c });
    }
  }
  strikeColIndexes2.sort((a,b) => b.name.localeCompare(a.name)); // reverse: strike4, strike3, strike2, strike1
  // Find last checked strike
  let lastStrikeCol = -1;
  for (const s of strikeColIndexes2) {
    const val = data[rowIndex][s.col];
    const isChecked = val === true || val === 'TRUE' || val === 1 || String(val).toLowerCase() === 'true';
    if (isChecked) { lastStrikeCol = s.col; break; }
  }
  if (lastStrikeCol === -1) return { success: false, error: 'No strikes to remove' };
  sheet.getRange(rowIndex+1, lastStrikeCol+1).setValue(false);
  SpreadsheetApp.flush();
  const newTotal = Math.max(0, (Number(data[rowIndex][7]||0)) - 1);
  return { success: true, newTotal };
}

// ── DEBUG STRIKE HEADERS ──────────────────────────────────────
function debugStrikeHeaders(className) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Strike');
  if (!sheet) return { error: 'Strike sheet not found' };
  const headers = sheet.getDataRange().getValues()[0];
  const mapped  = {};
  headers.forEach((h, i) => {
    const k = String(h||'').trim().toLowerCase().replace(/\s+/g,'');
    mapped[k] = i;
  });
  return { raw: headers, mapped };
}

// ── SUMMATIVE INFO STORE ──────────────────────────────────────
// Uses Script Properties to store a single summative config
function getSummative() {
  const props = PropertiesService.getScriptProperties();
  const raw   = props.getProperty('summative');
  if (!raw) return { exists: false };
  try { return { exists: true, data: JSON.parse(raw) }; }
  catch(e) { return { exists: false }; }
}

function setSummative(username, password, dataStr) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  try {
    const data = JSON.parse(dataStr);
    PropertiesService.getScriptProperties().setProperty('summative', JSON.stringify(data));
    return { success: true };
  } catch(e) { return { success: false, error: 'Invalid data: ' + e.message }; }
}

function clearSummative(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  PropertiesService.getScriptProperties().deleteProperty('summative');
  return { success: true };
}

// ── QUIZ FUNCTIONS ────────────────────────────────────────────
function getQuizAttempt(className, studentNo, chapter) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Activeness');
  if (!sheet) return { error: 'Activeness sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];

  const colName = 'Quiz Ch' + chapter;
  let colIndex  = -1;
  for (let c = 0; c < headers.length; c++) {
    if (String(headers[c]).trim() === colName) { colIndex = c; break; }
  }
  // Column doesn't exist yet = no attempt
  if (colIndex === -1) return { attempted: false };

  let rowIndex = -1;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) { rowIndex = r; break; }
  }
  if (rowIndex === -1) return { error: 'Student not found' };

  const val = data[rowIndex][colIndex];
  if (val === '' || val === null || val === undefined) return { attempted: false };
  return { attempted: true, score: Number(val) };
}

function saveQuizScore(username, password, className, studentNo, chapter, score) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Activeness');
  if (!sheet) return { success: false, error: 'Activeness sheet not found' };

  const data    = sheet.getDataRange().getValues();
  const headers = data[0];
  const colName = 'Quiz Ch' + chapter;

  // Find or create column
  let colIndex = -1;
  for (let c = 0; c < headers.length; c++) {
    if (String(headers[c]).trim() === colName) { colIndex = c; break; }
  }
  if (colIndex === -1) {
    // Add new column at end
    colIndex = headers.length;
    sheet.getRange(1, colIndex + 1).setValue(colName);
  }

  // Find student row
  let rowIndex = -1;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) { rowIndex = r; break; }
  }
  if (rowIndex === -1) return { success: false, error: 'Student not found' };

  // Check already attempted
  const existing = data[rowIndex][colIndex];
  if (existing !== '' && existing !== null && existing !== undefined) {
    return { success: false, error: 'Already attempted' };
  }

  sheet.getRange(rowIndex + 1, colIndex + 1).setValue(Number(score));
  SpreadsheetApp.flush();
  return { success: true, score: Number(score) };
}

// ── GET GEMINI API KEY FOR BROWSER CALL ──────────────────────
// Browser calls Gemini directly (no CORS issues with Gemini)
// Apps Script just securely provides the key
function getGeminiKey(username, password) {
  // Gated: only an authenticated teacher may retrieve the key (no page uses it,
  // and it must never be handed to anonymous callers).
  if (!authOk(username, password)) return { error: 'Unauthorized' };
  const key = PropertiesService.getScriptProperties().getProperty('GEMINI_API_KEY');
  if (!key) return { error: 'Gemini API key not configured' };
  return { key };
}

// ── GET QUIZ RESULTS (TEACHER) ───────────────────────────────
function getQuizResults(username, password, className) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Activeness');
  if (!sheet) return { success: false, error: 'Activeness sheet not found' };

  const data    = sheet.getDataRange().getValues();
  const headers = data[0];

  // Find quiz columns
  const quizCols = {};
  headers.forEach((h, i) => {
    const k = String(h||'').trim();
    if (k.startsWith('Quiz Ch')) quizCols[k] = i;
  });

  const results = [];
  for (let r = 1; r < data.length; r++) {
    const no   = data[r][0];
    const name = data[r][1];
    if (!no || !name || String(name).trim() === '') break;
    const studentQuizzes = {};
    Object.keys(quizCols).forEach(col => {
      const val = data[r][quizCols[col]];
      if (val !== '' && val !== null && val !== undefined) {
        studentQuizzes[col] = Number(val);
      }
    });
    if (Object.keys(studentQuizzes).length > 0) {
      results.push({ no, name: String(name).trim(), nickname: String(data[r][2]||'').trim(), quizzes: studentQuizzes });
    }
  }
  return { success: true, results, quizCols: Object.keys(quizCols) };
}

// ── GET ALL CHAPTER SCORES FOR A CLASS ───────────────────────
function getChapterScores(className, chapter) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheetName = 'Chapter_' + chapter;
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName(sheetName);
  if (!sheet) return { error: sheetName + ' sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];

  // Find inputtable columns (Task 1..N, Formative, Summative)
  const inputCols = [];
  headers.forEach((h, i) => {
    const k = String(h || '').trim();
    if (k.match(/^Task\s*\d+$/i) || k === 'Formative' || k === 'Summative') {
      inputCols.push({ name: k, col: i });
    }
  });

  const students = [];
  for (let r = 1; r < data.length; r++) {
    const no = data[r][0], name = data[r][1];
    if (!no || !name || String(name).trim() === '') break;
    const scores = {};
    inputCols.forEach(c => {
      const v = data[r][c.col];
      scores[c.name] = (v === '' || v === null || v === undefined || v === '#DIV/0!') ? '' : v;
    });
    // Also read calculated cols
    const avgCol   = headers.findIndex(h => String(h).trim() === 'Average');
    const finalCol = headers.findIndex(h => String(h).trim() === 'Final Score');
    const indCol   = headers.findIndex(h => String(h).trim().toLowerCase().startsWith('ind'));
    students.push({
      no, name: String(name).trim(), nickname: String(data[r][2] || '').trim(),
      scores,
      average:    avgCol   >= 0 ? data[r][avgCol]   : '',
      finalScore: finalCol >= 0 ? data[r][finalCol] : '',
      indicator:  indCol   >= 0 ? data[r][indCol]   : '',
    });
  }
  return { students, inputCols: inputCols.map(c => c.name) };
}

// ── SAVE ONE SCORE CELL ───────────────────────────────────────
function saveChapterScore(username, password, className, chapter, studentNo, column, value) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  const sheetName = 'Chapter_' + chapter;
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName(sheetName);
  if (!sheet) return { success: false, error: sheetName + ' sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];

  let colIndex = -1;
  for (let c = 0; c < headers.length; c++) {
    if (String(headers[c]).trim() === column) { colIndex = c; break; }
  }
  if (colIndex === -1) return { success: false, error: 'Column not found: ' + column };

  let rowIndex = -1;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) { rowIndex = r; break; }
  }
  if (rowIndex === -1) return { success: false, error: 'Student not found' };

  const num = value === '' ? '' : Number(value);
  sheet.getRange(rowIndex + 1, colIndex + 1).setValue(num);
  SpreadsheetApp.flush();
  return { success: true };
}

// ── FINAL SCORES (Scores sheet — overall/ASAT) ────────────────
function getFinalScores(className) {
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Scores');
  if (!sheet) return { error: 'Scores sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];

  // Only show these specific columns — editable inputs (Semester 1 = Ch 1-3)
  const INPUT_WHITELIST  = ['Chapter 1', 'Chapter 2', 'Chapter 3', 'ASAT'];
  // Only show these as read-only calculated columns
  const CALC_WHITELIST   = ['Average', 'Final Score', 'Indicator'];

  const inputCols = [], calcCols = [];
  headers.forEach(function(h, i) {
    const k = String(h || '').trim();
    if (INPUT_WHITELIST.includes(k)) inputCols.push({ name: k, col: i });
    if (CALC_WHITELIST.includes(k))  calcCols.push({ name: k, col: i });
  });

  // Sort to match whitelist order
  inputCols.sort(function(a, b) { return INPUT_WHITELIST.indexOf(a.name) - INPUT_WHITELIST.indexOf(b.name); });
  calcCols.sort(function(a, b)  { return CALC_WHITELIST.indexOf(a.name)  - CALC_WHITELIST.indexOf(b.name); });

  const students = [];
  for (let r = 1; r < data.length; r++) {
    const no = data[r][0], name = data[r][1];
    if (!no || !name || String(name).trim() === '') break;
    const scores = {}, calc = {};
    inputCols.forEach(function(c) {
      const v = data[r][c.col];
      scores[c.name] = (v === '' || v === null || v === undefined || v === '#DIV/0!') ? '' : v;
    });
    calcCols.forEach(function(c) {
      const v = data[r][c.col];
      calc[c.name] = (v === '' || v === null || v === undefined || v === '#DIV/0!') ? '' : v;
    });
    students.push({ no, name: String(name).trim(), nickname: String(data[r][2] || '').trim(), scores, calc });
  }
  return { students, inputCols: inputCols.map(function(c) { return c.name; }), calcCols: calcCols.map(function(c) { return c.name; }) };
}

function saveFinalScore(username, password, className, studentNo, column, value) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  // Only allow saving whitelisted columns (Semester 1 = Ch 1-3)
  const ALLOWED = ['Chapter 1', 'Chapter 2', 'Chapter 3', 'ASAT'];
  if (!ALLOWED.includes(column)) return { success: false, error: 'Column not editable: ' + column };
  const sheetId = SCORE_SHEETS[className];
  if (!sheetId) return { success: false, error: 'Class not found' };
  const sheet = SpreadsheetApp.openById(sheetId).getSheetByName('Scores');
  if (!sheet) return { success: false, error: 'Scores sheet not found' };
  const data    = sheet.getDataRange().getValues();
  const headers = data[0];

  let colIndex = -1;
  for (let c = 0; c < headers.length; c++) {
    if (String(headers[c]).trim() === column) { colIndex = c; break; }
  }
  if (colIndex === -1) return { success: false, error: 'Column not found: ' + column };

  let rowIndex = -1;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) { rowIndex = r; break; }
  }
  if (rowIndex === -1) return { success: false, error: 'Student not found' };

  const num = value === '' ? '' : Number(value);
  sheet.getRange(rowIndex + 1, colIndex + 1).setValue(num);
  SpreadsheetApp.flush();

  // Re-read calculated cols to return updated values
  const CALCULATED = /average|final|indicator|grade|indikator/i;
  const updatedRow = sheet.getRange(rowIndex + 1, 1, 1, headers.length).getValues()[0];
  const calc = {};
  headers.forEach(function(h, i) {
    if (CALCULATED.test(String(h || '').trim())) {
      const v = updatedRow[i];
      calc[String(h).trim()] = (v === '' || v === null || v === undefined || v === '#DIV/0!') ? '' : v;
    }
  });
  return { success: true, calc };
}

// ── SUBMISSION STATUS (Chapter 4) ────────────────────────────
function getSubmissions() {
  var SUBMISSION_SS_ID = '1VsAtQHxNcajOAuasAe5Aagr4XelqtO3xtN8TvQOcrNE';

  // Normalize name for comparison — lowercase, strip apostrophes/punctuation, collapse spaces
  function normName(s) {
    return String(s).toLowerCase()
      .replace(/[-]/g, ' ')         // hyphens → spaces before stripping
      .replace(/['\u2018\u2019\u201A\u201B\u0060\u00B4]/g, '') // all apostrophe variants
      .replace(/[^a-z0-9\s]/g, '')  // remove other punctuation
      .replace(/\s+/g, ' ')         // collapse spaces
      .trim();
  }

  // Read submitted students — track which tasks each student submitted
  // submitted[normKey] = { tasksWithFile: [col3, col4...], totalTaskCols: N }
  var submitted = {};
  var taskCols  = [];
  try {
    var subSS     = SpreadsheetApp.openById(SUBMISSION_SS_ID);
    var respSheet = subSS.getSheetByName('Responses') || subSS.getSheetByName('Response') || subSS.getSheets()[0];
    if (respSheet) {
      var respData = respSheet.getDataRange().getValues();
      var headers  = respData[0].map(function(h) { return String(h).trim().toLowerCase(); });
      var clsCol   = headers.indexOf('class');
      var nameCol  = headers.indexOf('student name');
      if (clsCol  === -1) clsCol  = 1;
      if (nameCol === -1) nameCol = 2;

      // Find task file columns dynamically from header names
      headers.forEach(function(h, i) {
        if (i > nameCol && (h.indexOf('task') > -1 || h.indexOf('file') > -1)) {
          taskCols.push(i);
        }
      });
      if (!taskCols.length) {
        // Fallback: columns after student name until end
        for (var c = nameCol + 1; c < headers.length; c++) taskCols.push(c);
      }

      for (var r = 1; r < respData.length; r++) {
        var cls  = String(respData[r][clsCol]  || '').trim();
        var name = String(respData[r][nameCol] || '').trim();
        if (!cls || !name) continue;
        var key = normName(cls) + '|' + normName(name);

        // Track per-task file presence
        var taskFilled = taskCols.map(function(col) {
          return String(respData[r][col] || '').trim().length > 5;
        });

        // Merge with existing entry (student may have resubmitted)
        if (submitted[key]) {
          taskFilled.forEach(function(v, i) {
            if (v) submitted[key][i] = true;
          });
        } else {
          var entry = {};
          taskFilled.forEach(function(v, i) { entry[i] = v; });
          submitted[key] = entry;
        }
      }
    }
  } catch(e) {
    return { error: 'Could not read Response sheet: ' + e.message };
  }

  // Get student list from ScoresAPI's own class sheets
  var classes = Object.keys(SCORE_SHEETS);
  var result  = {};

  classes.forEach(function(cls) {
    try {
      var sheet = SpreadsheetApp.openById(SCORE_SHEETS[cls]).getSheets()[0];
      var data  = sheet.getDataRange().getValues();
      var students = [];
      for (var r = 1; r < data.length; r++) {
        var name = String(data[r][1] || '').trim();
        if (!name) break;
        var clsForLookup = cls.replace('XE', 'XE-'); // XE10 → XE-10
        var key = normName(clsForLookup) + '|' + normName(name);
        var entry = submitted[key] || null;
        // Only fully submitted if ALL task columns have a file
        var allDone = entry !== null && taskCols.length > 0 &&
          taskCols.every(function(col, i) { return !!entry[i]; });
        students.push({ name: name, submitted: allDone, tasksDone: entry });
      }
      result[cls] = students;
    } catch(e) {
      result[cls] = [];
    }
  });

  var classOrder = ['XE1','XE4','XE5','XE6','XE7','XE8','XE9','XE10','XE11'];
  var sortedClasses = classOrder.filter(function(c) { return classes.indexOf(c) > -1; });

  return { classes: sortedClasses, students: result, totalTasks: taskCols.length };
}

// ── LESSON MATERIALS + ASSIGNMENTS (stored in Script Properties) ───────────
// Keyed per grade: materials_{grade}_ch{chapter}. Assignments reuse this with
// namespaced chapter keys (assign4/assign5). Legacy pre-grade data lived under
// materials_ch{chapter}; Grade 10 reads fall back to it and the first write for
// a (grade,chapter) migrates it, so nothing is lost.
function _materialsKey(grade, chapter) { return 'materials_' + (grade || '10') + '_ch' + chapter; }

function _readMaterials(grade, chapter) {
  const props = PropertiesService.getScriptProperties();
  let raw = props.getProperty(_materialsKey(grade, chapter));
  if (raw == null && String(grade || '10') === '10') raw = props.getProperty('materials_ch' + chapter); // legacy
  let list = [];
  try { if (raw) list = JSON.parse(raw); } catch(e) {}
  return list;
}

function getMaterials(chapter, grade) {
  return { materials: _readMaterials(grade, chapter) };
}

function addMaterial(username, password, chapter, title, fileId, fileType, grade) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const props = PropertiesService.getScriptProperties();
  let materials = _readMaterials(grade, chapter); // includes legacy for Grade 10
  if (materials.find(function(m) { return m.fileId === fileId; })) return { success: false, error: 'Already exists' };
  materials.push({ title: title, fileId: fileId, fileType: fileType || 'PDF', addedAt: new Date().toISOString() });
  props.setProperty(_materialsKey(grade, chapter), JSON.stringify(materials));
  return { success: true, materials: materials };
}

function deleteMaterial(username, password, chapter, fileId, grade) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const props = PropertiesService.getScriptProperties();
  let materials = _readMaterials(grade, chapter).filter(function(m) { return m.fileId !== fileId; });
  props.setProperty(_materialsKey(grade, chapter), JSON.stringify(materials));
  return { success: true, materials: materials };
}

// ── UNIT NAMES (teacher-editable, per grade) ──────────────────
// Stored in Script Properties as unit_names_{grade} = JSON array. Grade 10 has
// 3 units, Grade 11 has 2. Falls back to these defaults until the teacher edits
// them, so the site always shows sensible names.
function _unitCount(grade) { return String(grade) === '11' ? 2 : 3; }
function _defaultUnitNames(grade) {
  return String(grade) === '11'
    ? ['Narrative Text', 'Analytical & Hortatory Exposition Text']
    : ['Descriptive Text', 'Recount', 'Procedure'];
}

// Public — students read this to label their Units.
function getUnitNames(grade) {
  grade = String(grade || '10');
  var raw = PropertiesService.getScriptProperties().getProperty('unit_names_' + grade);
  var names = null;
  try { if (raw) names = JSON.parse(raw); } catch (e) {}
  var fallback = _defaultUnitNames(grade);
  var count = _unitCount(grade), out = [];
  for (var i = 0; i < count; i++) {
    var v = (names && names[i] != null) ? String(names[i]).trim() : '';
    out.push(v || fallback[i] || '');
  }
  return { success: true, grade: grade, names: out };
}

function setUnitNames(username, password, grade, namesJson) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  grade = String(grade || '10');
  var names;
  try { names = JSON.parse(namesJson || '[]'); } catch (e) { return { success: false, error: 'Bad names' }; }
  var count = _unitCount(grade), out = [];
  for (var i = 0; i < count; i++) out.push(String(names[i] == null ? '' : names[i]).trim().slice(0, 60));
  PropertiesService.getScriptProperties().setProperty('unit_names_' + grade, JSON.stringify(out));
  return getUnitNames(grade);
}

// ── ANNOUNCEMENTS ─────────────────────────────────────────────
function getAnnouncement() {
  var raw = PropertiesService.getScriptProperties().getProperty('announcement');
  if (!raw) return { announcement: null };
  try { return { announcement: JSON.parse(raw) }; }
  catch(e) { return { announcement: null }; }
}

function setAnnouncement(username, password, title, body, type, audience) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var aud = String(audience || 'both');
  if (aud !== '10' && aud !== '11') aud = 'both'; // 10 | 11 | both
  var ann = {
    title: title || '',
    body: body || '',
    type: type || 'info',  // info | warning | success
    audience: aud,
    postedAt: new Date().toISOString(),
    postedBy: username
  };
  PropertiesService.getScriptProperties().setProperty('announcement', JSON.stringify(ann));
  return { success: true, announcement: ann };
}

function clearAnnouncement(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  PropertiesService.getScriptProperties().deleteProperty('announcement');
  return { success: true };
}

// ── SUMMATIVE TOPIC — setStance & setReveal ──────────────────
// Sheet IDs hardcoded so Apps Script can access them directly
const SUMMATIVE_TOPIC_SHEET_ID  = '1hzFDt3RyW1JuK3JJE7nuXAtVjiKG6btXD6RxEcHDYbk';
const SUMMATIVE_TOPIC_TAB       = 'SummativeTopic';

function _getSummativeTopicSheet() {
  var ss    = SpreadsheetApp.openById(SUMMATIVE_TOPIC_SHEET_ID);
  var sheet = ss.getSheetByName(SUMMATIVE_TOPIC_TAB);
  if (!sheet) throw new Error('Sheet "' + SUMMATIVE_TOPIC_TAB + '" not found');
  return sheet;
}

function _findSummativeRow(sheet, cls, attNum) {
  var data     = sheet.getDataRange().getValues();
  var clsCount = 0;
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][0] || '').trim() === cls) {
      clsCount++;
      if (clsCount === parseInt(attNum)) return r + 1; // 1-indexed sheet row
    }
  }
  return -1;
}

// Student writes Agree or Disagree — no teacher auth needed
// Column D (col 4, 1-indexed) = Stance
function setStance(cls, attNum, stance) {
  try {
    if (!cls || !attNum || !stance)
      return { success: false, error: 'Missing parameters: cls=' + cls + ' attNum=' + attNum + ' stance=' + stance };
    if (stance !== 'Agree' && stance !== 'Disagree')
      return { success: false, error: 'Invalid stance. Must be Agree or Disagree.' };
    var sheet  = _getSummativeTopicSheet();
    var rowNum = _findSummativeRow(sheet, cls, attNum);
    if (rowNum === -1) return { success: false, error: 'Student not found: ' + cls + ' #' + attNum };
    sheet.getRange(rowNum, 4).setValue(stance);
    SpreadsheetApp.flush();
    return { success: true, cls: cls, attNum: parseInt(attNum), stance: stance, rowNum: rowNum };
  } catch(e) { return { success: false, error: e.message }; }
}

// Teacher reveals or hides a topic — teacher auth required
// Column E (col 5, 1-indexed) = Revealed
// Works for both checkbox cells and plain text cells
function setReveal(username, password, cls, attNum, revealed) {
  try {
    if (!authOk(username, password))
      return { success: false, error: 'Unauthorized' };
    if (!cls || !attNum)
      return { success: false, error: 'Missing parameters: cls=' + cls + ' attNum=' + attNum };
    var sheet  = _getSummativeTopicSheet();
    var rowNum = _findSummativeRow(sheet, cls, attNum);
    if (rowNum === -1) return { success: false, error: 'Student not found: ' + cls + ' #' + attNum };

    var cell = sheet.getRange(rowNum, 5);

    // Detect if the cell is a checkbox by checking its data validation
    var isCheckbox = false;
    try {
      var dv = cell.getDataValidation();
      if (dv && dv.getCriteriaType() === SpreadsheetApp.DataValidationCriteria.CHECKBOX) {
        isCheckbox = true;
      }
    } catch(ve) { isCheckbox = false; }

    var boolVal = (revealed === 'TRUE' || revealed === true || revealed === 'true');

    if (isCheckbox) {
      cell.setValue(boolVal);
    } else {
      // Plain text cell — write string so GViz reads it correctly
      cell.setValue(boolVal ? 'TRUE' : 'FALSE');
    }

    SpreadsheetApp.flush();
    return { success: true, cls: cls, attNum: parseInt(attNum), revealed: boolVal, rowNum: rowNum, isCheckbox: isCheckbox };
  } catch(e) { return { success: false, error: e.message }; }
}

// ── BANNER SLIDES ─────────────────────────────────────────────
function getBannerSlides() {
  var raw = PropertiesService.getScriptProperties().getProperty('bannerSlides');
  if (!raw) return { slides: [] };
  try { return { slides: JSON.parse(raw) }; }
  catch(e) { return { slides: [] }; }
}

function setBannerSlides(username, password, dataStr) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  try {
    var slides = JSON.parse(dataStr);
    PropertiesService.getScriptProperties().setProperty('bannerSlides', JSON.stringify(slides));
    return { success: true, count: slides.length };
  } catch(e) { return { success: false, error: e.message }; }
}
