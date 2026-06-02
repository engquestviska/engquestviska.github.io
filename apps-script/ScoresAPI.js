// ============================================================
//  ENGLISH QUEST – SCORES + TASK STATUS API
//  Google Apps Script — paste into Code.gs (replace all)
//  Redeploy as new version after updating!
// ============================================================

const SCORE_SHEETS = {
  'XE1':  '1X3PBpMCEvglTA92HST8286dY_OWe5lw7tdJHPpNjggk',
  'XE4':  '16CT_wxAGV0mLRhvlIFpXRzKobdnpnj2-DcAJSiz14JY',
  'XE5':  '1-GMJ8amrcE-r_N1qJL8rsrxEItRraN-97FgyySJgOxg',
  'XE6':  '1MxqzK2JOwMyc2yfUSwCgIW4siWCsJe2R_o_cF11mOHU',
  'XE7':  '1D9iYhBLNPhFsgTZ15vQOHFn22RYCI6HFW10aC4HwJK4',
  'XE8':  '1skj7KBc11zaFa07FGu0r4bEHvDrV-oHZm4qKhBLimpg',
  'XE9':  '19Pn0JyIZRAgzhUGs_W519vjmwcnivair6hsqkIVyOnE',
  'XE10': '1UEgm1spqzNGPbYl9a9CmLavwALVLG_6o2gEvN3R_LKM',
  'XE11': '1yt6km9wnBYRe61bHaSjDfJWkmqz884v1cogp836Ioa0',
};

const TASK_SHEET_NAME = 'Task_Status';
const FINAL_COMPARISON_SHEET_ID = '1UjlcC2zOy5usrkzwL1gqgt3BDfNFb-heak-jdeIxIcM';
const FINAL_COMPARISON_SHEET_NAME = 'Sem 1 vs Sem 2';
const FINAL_DASHBOARD_SHEET_GID = 1145427024;
const REMEDIAL_LINKS = {
  enrichment: 'https://chat.whatsapp.com/L9isr7aTbeJKZh5YeMCnBE',
  gap1to15: 'https://chat.whatsapp.com/JTrmrhnBz1v0yhBpMGatpZ',
  gap16to25: 'https://chat.whatsapp.com/GIIRbntJuQYK32dwI3xnEV',
  gap26plus: 'https://chat.whatsapp.com/BnUByekB5QR3wvMb3Vkmf4'
};
const TEACHER_USER    = 'teacher';
const TEACHER_HASH    = '39de0394764747426b997b945770fd60661cbd072051131da2cb99d6d8dd8430';
const WRITES_ENABLED  = true;
const SECURITY_AUDIT_KEY = 'securityAuditLog';
const TEACHER_SESSIONS_KEY = 'teacherSessions';
const TEACHER_SESSION_TTL_MS = 6 * 60 * 60 * 1000;
const WRITE_ACTIONS = {
  saveChapterScore: true,
  saveFinalScore: true,
  saveTaskStatus: true,
  syncCh5Student: true,
  syncCh5Class: true,
  saveQuizScore: true,
  setSummative: true,
  clearSummative: true,
  incrementActiveness: true,
  setupQuizColumn: true,
  addStrike: true,
  removeStrike: true,
  addMaterial: true,
  deleteMaterial: true,
  setStance: true,
  setReveal: true,
  setBannerSlides: true,
  setAnnouncement: true,
  clearAnnouncement: true
};

function sha256Hex_(value) {
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(value || ''), Utilities.Charset.UTF_8);
  return bytes.map(function(byte) {
    var v = byte;
    if (v < 0) v += 256;
    return ('0' + v.toString(16)).slice(-2);
  }).join('');
}

function getTeacherSessions_() {
  var raw = PropertiesService.getScriptProperties().getProperty(TEACHER_SESSIONS_KEY);
  var sessions = [];
  try { if (raw) sessions = JSON.parse(raw); } catch(e) { sessions = []; }
  var now = Date.now();
  return sessions.filter(function(s) {
    return s && s.hash && Number(s.expiresAt || 0) > now;
  });
}

function saveTeacherSessions_(sessions) {
  PropertiesService.getScriptProperties().setProperty(TEACHER_SESSIONS_KEY, JSON.stringify(sessions.slice(-10)));
}

function teacherPasswordOk_(username, password) {
  return username === TEACHER_USER && sha256Hex_(password) === TEACHER_HASH;
}

function teacherSessionOk_(username, token) {
  if (username !== TEACHER_USER || !token) return false;
  var hash = sha256Hex_(token);
  var sessions = getTeacherSessions_();
  return sessions.some(function(s) { return s.hash === hash; });
}

function issueTeacherToken_(username) {
  var token = Utilities.getUuid() + ':' + Utilities.getUuid();
  var sessions = getTeacherSessions_();
  sessions.push({
    hash: sha256Hex_(token),
    username: username,
    createdAt: new Date().toISOString(),
    expiresAt: Date.now() + TEACHER_SESSION_TTL_MS
  });
  saveTeacherSessions_(sessions);
  return token;
}

function authOk(username, secret) {
  return teacherPasswordOk_(username, secret) || teacherSessionOk_(username, secret);
}

function checkTeacherLogin(username, secret) {
  if (teacherPasswordOk_(username, secret)) {
    var token = issueTeacherToken_(username);
    logSecurityEvent_('checkLogin', username, 'login_success', { issuedToken: true });
    return { ok: true, token: token, expiresIn: TEACHER_SESSION_TTL_MS };
  }
  if (teacherSessionOk_(username, secret)) {
    return { ok: true, expiresIn: TEACHER_SESSION_TTL_MS };
  }
  logSecurityEvent_('checkLogin', username || '', 'login_failed', { usernameProvided: !!username });
  return { ok: false };
}

function teacherLogout(username, secret) {
  if (username !== TEACHER_USER || !secret) return { success: true };
  var hash = sha256Hex_(secret);
  var sessions = getTeacherSessions_().filter(function(s) { return s.hash !== hash; });
  saveTeacherSessions_(sessions);
  logSecurityEvent_('teacherLogout', username, 'logout', {});
  return { success: true };
}

function isWriteAction_(action) {
  return !!WRITE_ACTIONS[action];
}

function isMaintenanceAllowedWrite_(action) {
  return action === 'syncCh5Student' || action === 'syncCh5Class';
}

function sanitizeParams_(params) {
  var clean = {};
  params = params || {};
  Object.keys(params).forEach(function(key) {
    if (key === 'password' || key === 'callback') return;
    var value = params[key];
    if (value === undefined || value === null) return;
    value = String(value);
    clean[key] = value.length > 240 ? value.slice(0, 240) + '...' : value;
  });
  return clean;
}

function logSecurityEvent_(action, username, status, details) {
  try {
    var props = PropertiesService.getScriptProperties();
    var raw = props.getProperty(SECURITY_AUDIT_KEY);
    var log = [];
    try { if (raw) log = JSON.parse(raw); } catch(e) { log = []; }
    log.push({
      at: new Date().toISOString(),
      action: action || '',
      username: username || '',
      status: status || '',
      details: details || {}
    });
    if (log.length > 200) log = log.slice(log.length - 200);
    props.setProperty(SECURITY_AUDIT_KEY, JSON.stringify(log));
  } catch(e) {}
}

function getSecurityAudit(username, password) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var raw = PropertiesService.getScriptProperties().getProperty(SECURITY_AUDIT_KEY);
  var log = [];
  try { if (raw) log = JSON.parse(raw); } catch(e) { log = []; }
  return { success: true, log: log };
}

function output_(result, callback) {
  var json = JSON.stringify(result);
  if (callback) return ContentService.createTextOutput(callback + '(' + json + ')').setMimeType(ContentService.MimeType.JAVASCRIPT);
  return ContentService.createTextOutput(json).setMimeType(ContentService.MimeType.JSON);
}

function doGet(e) {
  e = e || { parameter: {} };
  const action = e.parameter.action || '', callback = e.parameter.callback || '';
  let result;
  try {
    if (isWriteAction_(action) && !WRITES_ENABLED && !isMaintenanceAllowedWrite_(action)) {
      result = { success: false, error: 'Security maintenance: write actions are temporarily disabled.' };
      logSecurityEvent_(action, e.parameter.username || '', 'blocked_maintenance', sanitizeParams_(e.parameter));
      return output_(result, callback);
    }
    if      (action === 'getStudents')    result = getStudents(e.parameter.className);
    else if (action === 'getScore')       result = getScore(e.parameter.className, e.parameter.studentNo);
    else if (action === 'getChapterScores')  result = getChapterScores(e.parameter.className, e.parameter.chapter);
    else if (action === 'saveChapterScore')  result = saveChapterScore(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.chapter, e.parameter.studentNo, e.parameter.column, e.parameter.value);
    else if (action === 'getFinalScores')    result = getFinalScores(e.parameter.className);
    else if (action === 'getSemComparisonStudents') result = getSemComparisonStudents(e.parameter.className);
    else if (action === 'getSemComparison')  result = getSemComparison(e.parameter.className, e.parameter.studentNo);
    else if (action === 'getFinalDashboardStudents') result = getFinalDashboardStudents(e.parameter.className);
    else if (action === 'getFinalDashboard') result = getFinalDashboard(e.parameter.className, e.parameter.studentNo);
    else if (action === 'saveFinalScore')    result = saveFinalScore(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, e.parameter.column, e.parameter.value);
    else if (action === 'getSubmissions')    result = getSubmissions();
    else if (action === 'getTaskStatus')  result = getTaskStatus(e.parameter.className, e.parameter.studentNo);
    else if (action === 'getAllTasks')    result = getAllTasks(e.parameter.className);
    else if (action === 'saveTaskStatus') result = saveTaskStatus(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, JSON.parse(e.parameter.tasks || '{}'));
    else if (action === 'syncCh5Student') result = syncCh5Student(e.parameter.className, e.parameter.studentNo);
    else if (action === 'syncCh5Class')   result = syncCh5Class(e.parameter.className, e.parameter.username, e.parameter.password);
    else if (action === 'getCh5Submissions')   result = getCh5Submissions();
    else if (action === 'getCh4StudentFiles') result = getCh4StudentFiles(e.parameter.className, e.parameter.studentNo);
    else if (action === 'getCh5StudentFiles') result = getCh5StudentFiles(e.parameter.className, e.parameter.studentNo);
    else if (action === 'checkLogin')       result = checkTeacherLogin(e.parameter.username, e.parameter.password);
    else if (action === 'teacherLogout')    result = teacherLogout(e.parameter.username, e.parameter.password);
    else if (action === 'getSecurityAudit') result = getSecurityAudit(e.parameter.username, e.parameter.password);
    else if (action === 'getSummative')     result = getSummative();
    else if (action === 'getQuizAttempt')   result = getQuizAttempt(e.parameter.className, e.parameter.studentNo, e.parameter.chapter);
    else if (action === 'getGeminiKey')      result = getGeminiKey(e.parameter.username, e.parameter.password);
    else if (action === 'getQuizResults')   result = getQuizResults(e.parameter.username, e.parameter.password, e.parameter.className);
    else if (action === 'saveQuizScore')    result = saveQuizScore(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, e.parameter.chapter, e.parameter.score);
    else if (action === 'setSummative')     result = setSummative(e.parameter.username, e.parameter.password, e.parameter.data);
    else if (action === 'clearSummative')   result = clearSummative(e.parameter.username, e.parameter.password);
    else if (action === 'getAllActiveness')    result = getAllActiveness(e.parameter.className);
    else if (action === 'incrementActiveness') result = incrementActiveness(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, e.parameter.column, e.parameter.delta || 1);
    else if (action === 'setupQuizColumn')     result = setupQuizColumn(e.parameter.username, e.parameter.password);
    else if (action === 'getStudentStrikes')  result = getStudentStrikes(e.parameter.className, e.parameter.studentNo);
    else if (action === 'debugStrikeHeaders')  result = debugStrikeHeaders(e.parameter.className);
    else if (action === 'getAllStrikes')       result = getAllStrikes(e.parameter.className);
    else if (action === 'addStrike')          result = addStrike(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo, e.parameter.reason || '');
    else if (action === 'removeStrike')       result = removeStrike(e.parameter.username, e.parameter.password, e.parameter.className, e.parameter.studentNo);
    else if (action === 'debugCh5Names')   result = debugCh5Names(e.parameter.username, e.parameter.password);
    else if (action === 'getMaterials')    result = getMaterials(e.parameter.chapter);
    else if (action === 'addMaterial')     result = addMaterial(e.parameter.username, e.parameter.password, e.parameter.chapter, e.parameter.title, e.parameter.fileId, e.parameter.fileType);
    else if (action === 'deleteMaterial')  result = deleteMaterial(e.parameter.username, e.parameter.password, e.parameter.chapter, e.parameter.fileId);
    else if (action === 'setStance')        result = setStance(e.parameter.cls, e.parameter.attNum, e.parameter.stance);
    else if (action === 'setReveal')        result = setReveal(e.parameter.username, e.parameter.password, e.parameter.cls, e.parameter.attNum, e.parameter.revealed);
    else if (action === 'getBannerSlides')  result = getBannerSlides();
    else if (action === 'setBannerSlides')  result = setBannerSlides(e.parameter.username, e.parameter.password, e.parameter.data);
    else if (action === 'ping')            result = { ok: true };
    else if (action === 'getAnnouncement')   result = getAnnouncement();
    else if (action === 'setAnnouncement')   result = setAnnouncement(e.parameter.username, e.parameter.password, e.parameter.title, e.parameter.body, e.parameter.type);
    else if (action === 'clearAnnouncement') result = clearAnnouncement(e.parameter.username, e.parameter.password);
    else result = { error: 'Unknown action' };
  } catch(err) { result = { error: err.message }; }
  if (isWriteAction_(action)) {
    var status = result && result.success ? 'write_success' : (result && result.error === 'Unauthorized' ? 'unauthorized_write' : 'write_failed');
    logSecurityEvent_(action, e.parameter.username || '', status, sanitizeParams_(e.parameter));
  }
  return output_(result, callback);
}

function doPost(e) {
  let body = {};
  try { body = JSON.parse(e.postData.contents); } catch(err) {}
  let result;
  try {
    if (isWriteAction_(body.action) && !WRITES_ENABLED && !isMaintenanceAllowedWrite_(body.action)) {
      result = { success: false, error: 'Security maintenance: write actions are temporarily disabled.' };
      logSecurityEvent_(body.action, body.username || '', 'blocked_maintenance', sanitizeParams_(body));
    }
    else if (body.action === 'saveTaskStatus') result = saveTaskStatus(body.username, body.password, body.className, body.studentNo, body.tasks || {});
  } catch(err) { result = { error: err.message }; }
  if (isWriteAction_(body.action) && (WRITES_ENABLED || isMaintenanceAllowedWrite_(body.action))) {
    var status = result && result.success ? 'write_success' : (result && result.error === 'Unauthorized' ? 'unauthorized_write' : 'write_failed');
    logSecurityEvent_(body.action, body.username || '', status, sanitizeParams_(body));
  }
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

// ── SYNC CH5 SUBMISSION → TASK STATUS (no auth, student-triggered) ──
function syncCh5Student(className, studentNo) {
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
  const tasksToSet = {};
  let hasSubmission = false;
  for (let r = 1; r < respData.length; r++) {
    if (normCls(respData[r][1]) === normCls(className) && norm(respData[r][2]) === norm(studentName)) {
      hasSubmission = true;
      for (let t = 1; t <= 5; t++) {
        const fileVal = String(respData[r][2 + t] || '').trim();
        if (fileVal) tasksToSet['C5T' + t] = true;
      }
    }
  }
  if (!hasSubmission) return { success: false, error: 'No Ch5 submission found — make sure you submitted via the Chapter 5 form first.' };

  // Map Task 1-5 file columns (index 3-7) → C5T1-C5T5, mark TRUE if any matching row has a file.
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

function drivePreviewUrl(value) {
  const raw = String(value || '').trim();
  if (!raw) return '';
  const idMatch = raw.match(/[-\w]{25,}/);
  if (!idMatch) return raw;
  return 'https://drive.google.com/file/d/' + idMatch[0] + '/preview';
}

function timestampMs(value, fallback) {
  if (value instanceof Date) return value.getTime();
  const parsed = Date.parse(String(value || ''));
  return isNaN(parsed) ? fallback : parsed;
}

// ── CH4 FILE URLS FOR A SPECIFIC STUDENT ─────────────────────
function getCh4StudentFiles(className, studentNo) {
  const SUBMISSION_SS_ID = '1VsAtQHxNcajOAuasAe5Aagr4XelqtO3xtN8TvQOcrNE';
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

  const subSS = SpreadsheetApp.openById(SUBMISSION_SS_ID);
  const respSheet = subSS.getSheetByName('Responses') || subSS.getSheetByName('Response') || subSS.getSheets()[0];
  if (!respSheet) return { success: true, submitted: false, files: {}, studentName };
  const respData = respSheet.getDataRange().getValues();
  if (!respData.length) return { success: true, submitted: false, files: {}, studentName };

  const headers = respData[0].map(h => String(h).trim().toLowerCase());
  let clsCol = headers.indexOf('class');
  let nameCol = headers.indexOf('student name');
  if (clsCol === -1) clsCol = 1;
  if (nameCol === -1) nameCol = 2;

  const taskCols = [];
  headers.forEach(function(h, i) {
    if (i > nameCol && (h.indexOf('task') > -1 || h.indexOf('file') > -1)) taskCols.push(i);
  });
  if (!taskCols.length) {
    for (let c = nameCol + 1; c < headers.length; c++) taskCols.push(c);
  }

  const normCls = s => String(s).toLowerCase().replace(/[^a-z0-9]/g, '');
  const normName = s => String(s).toLowerCase()
    .replace(/[-]/g, ' ')
    .replace(/['\u2018\u2019\u201A\u201B\u0060\u00B4]/g, '')
    .replace(/[^a-z0-9\s]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

  const latestByTask = {};
  let submitted = false;
  let timestamp = '';
  for (let r = 1; r < respData.length; r++) {
    if (normCls(respData[r][clsCol]) !== normCls(className) || normName(respData[r][nameCol]) !== normName(studentName)) continue;
    submitted = true;
    const rowTimestamp = String(respData[r][0] || '');
    const rowMs = timestampMs(respData[r][0], r);
    taskCols.forEach(function(col, i) {
      const val = String(respData[r][col] || '').trim();
      if (!val || !/\.pdf|drive\.google|[-\w]{25,}/i.test(val)) return;
      const taskKey = 'C4T' + (i + 1);
      if (!latestByTask[taskKey] || rowMs >= latestByTask[taskKey].ms) {
        latestByTask[taskKey] = {
          ms: rowMs,
          url: drivePreviewUrl(val),
          timestamp: rowTimestamp
        };
      }
    });
  }

  const files = {};
  const fileTimestamps = {};
  Object.keys(latestByTask).forEach(function(taskKey) {
    files[taskKey] = latestByTask[taskKey].url;
    fileTimestamps[taskKey] = latestByTask[taskKey].timestamp;
    if (!timestamp || latestByTask[taskKey].ms >= timestampMs(timestamp, 0)) {
      timestamp = latestByTask[taskKey].timestamp;
    }
  });

  return { success: true, submitted, files, fileTimestamps, studentName, timestamp };
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

  const latestByTask = {};
  let submitted = false;
  let timestamp = '';
  for (let r = 1; r < respData.length; r++) {
    if (normCls(respData[r][1]) === normCls(className) && normName(respData[r][2]) === normName(studentName)) {
      submitted = true;
      const rowTimestamp = String(respData[r][0] || '');
      const rowMs = timestampMs(respData[r][0], r);
      for (let t = 1; t <= 5; t++) {
        const val = String(respData[r][2 + t] || '').trim();
        if (!val) continue;
        const taskKey = 'C5T' + t;
        if (!latestByTask[taskKey] || rowMs >= latestByTask[taskKey].ms) {
          latestByTask[taskKey] = {
            ms: rowMs,
            url: drivePreviewUrl(val),
            timestamp: rowTimestamp
          };
        }
      }
    }
  }
  if (!submitted && Object.keys(latestByTask).length === 0) return { success: true, submitted: false, files: {}, studentName };
  if (Object.keys(latestByTask).length > 0) submitted = true;

  const files = {};
  const fileTimestamps = {};
  Object.keys(latestByTask).forEach(function(taskKey) {
    files[taskKey] = latestByTask[taskKey].url;
    fileTimestamps[taskKey] = latestByTask[taskKey].timestamp;
    if (!timestamp || latestByTask[taskKey].ms >= timestampMs(timestamp, 0)) {
      timestamp = latestByTask[taskKey].timestamp;
    }
  });
  return { success: true, submitted: true, files, fileTimestamps, studentName, timestamp };
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
      DoingTaskOnTime: Number(data[r][cols['Doing Task On Time']]|| 0),
      HelpingHand:     Number(data[r][cols['Helping Hand']]      || 0),
      Quiz:            Number(data[r][cols['Quiz']]              || 0),
      Total:           Number(data[r][cols['Total']]             || 0),
      Indicator:       String(data[r][cols['Indicator']]         || ''),
    });
  }
  return { students, headers: Object.keys(cols) };
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
function getAllStrikes(className) {
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
  if (!authOk(username, password)) {
    // For quiz generation, we allow any call since students need it
    // Key is read-only and rate-limited by Google
  }
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

  // Only show these specific columns — editable inputs
  const INPUT_WHITELIST  = ['Chapter 4', 'Chapter 5', 'ASAT'];
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

// ── SEMESTER FINAL SCORE COMPARISON (read-only) ──────────────
function getSemComparisonRows_() {
  const sheet = SpreadsheetApp.openById(FINAL_COMPARISON_SHEET_ID).getSheetByName(FINAL_COMPARISON_SHEET_NAME);
  if (!sheet) throw new Error(FINAL_COMPARISON_SHEET_NAME + ' sheet not found');

  const lastRow = sheet.getLastRow();
  if (lastRow < 4) return [];

  return sheet.getRange(4, 1, lastRow - 3, 8).getValues()
    .filter(function(row) { return row[1] && row[3]; })
    .map(function(row) {
      return {
        globalNo: row[0],
        className: String(row[1] || '').trim(),
        studentNo: row[2],
        name: String(row[3] || '').trim(),
        sem1: normalizeSemScore_(row[4]),
        sem2: normalizeSemScore_(row[5]),
        difference: normalizeSemScore_(row[6]),
        result: String(row[7] || '').trim()
      };
    });
}

function normalizeSemScore_(value) {
  if (value === '' || value === null || value === undefined) return '';
  const num = Number(value);
  return isNaN(num) ? value : Math.round(num * 100) / 100;
}

function averageSemScores_(values) {
  const nums = values.map(Number).filter(function(num) { return !isNaN(num); });
  if (!nums.length) return '';
  return Math.round((nums.reduce(function(sum, num) { return sum + num; }, 0) / nums.length) * 100) / 100;
}

function rankSemScore_(rows, student, key) {
  const studentValue = Number(student[key]);
  if (isNaN(studentValue)) return '';

  const scores = rows
    .map(function(row) { return Number(row[key]); })
    .filter(function(num) { return !isNaN(num); })
    .sort(function(a, b) { return b - a; });

  return scores.findIndex(function(score) { return score === studentValue; }) + 1;
}

function semClassLabel_(className) {
  return String(className || '').replace(/^XE/, 'X E-');
}

function semNameKey_(name) {
  return String(name || '').toUpperCase().replace(/\s+/g, ' ').trim();
}

function getSemRosterStudents_(className) {
  const result = getStudents(className);
  return result && result.students ? result.students : [];
}

function getSemComparisonStudents(className) {
  const cls = String(className || '').trim();
  const rows = getSemComparisonRows_().filter(function(row) { return !cls || row.className === cls; });
  const rosterStudents = cls ? getSemRosterStudents_(cls) : [];

  if (rosterStudents.length) {
    const rowsByName = {};
    const rowsByNo = {};
    rows.forEach(function(row) { rowsByName[semNameKey_(row.name)] = row; });
    rows.forEach(function(row) { rowsByNo[String(row.studentNo)] = row; });
    return {
      success: true,
      className: cls,
      students: rosterStudents
        .filter(function(student) { return !!rowsByName[semNameKey_(student.name)] || !!rowsByNo[String(student.no)]; })
        .map(function(student) {
          return { no: student.no, name: student.name };
        })
    };
  }

  return {
    success: true,
    className: cls,
    students: rows.map(function(row) {
      return { no: row.studentNo, name: row.name };
    })
  };
}

function getSemComparison(className, studentNo) {
  const cls = String(className || '').trim();
  const no = String(studentNo || '').trim();
  if (!cls || !no) return { success: false, error: 'Missing className or studentNo' };

  const allRows = getSemComparisonRows_();
  const classRows = allRows.filter(function(row) { return row.className === cls; });
  const rosterStudent = getSemRosterStudents_(cls).find(function(student) { return String(student.no) === no; });
  const matchedStudent = rosterStudent
    ? classRows.find(function(row) { return semNameKey_(row.name) === semNameKey_(rosterStudent.name); })
    : null;
  const studentRow = matchedStudent || classRows.find(function(row) { return String(row.studentNo) === no; });
  if (!studentRow) return { success: false, error: 'Student not found' };

  const student = Object.assign({}, studentRow);
  if (rosterStudent) {
    student.studentNo = rosterStudent.no;
    student.name = rosterStudent.name;
  }

  const sem1Rows = classRows.filter(function(row) { return row.sem1 !== '' && !isNaN(Number(row.sem1)); });
  const sem2Rows = classRows.filter(function(row) { return row.sem2 !== '' && !isNaN(Number(row.sem2)); });

  return {
    success: true,
    className: cls,
    classLabel: semClassLabel_(cls),
    student: student,
    summary: {
      classSize: classRows.length,
      completedSem2Count: sem2Rows.length,
      sem1Average: averageSemScores_(sem1Rows.map(function(row) { return row.sem1; })),
      sem2Average: averageSemScores_(sem2Rows.map(function(row) { return row.sem2; })),
      rankSem1: rankSemScore_(classRows, student, 'sem1'),
      rankSem2: rankSemScore_(classRows, student, 'sem2'),
      improvedCount: classRows.filter(function(row) { return String(row.result).toLowerCase() === 'improved'; }).length,
      decreasedCount: classRows.filter(function(row) { return String(row.result).toLowerCase() === 'decreased'; }).length,
      sameCount: classRows.filter(function(row) { return String(row.result).toLowerCase() === 'same'; }).length
    },
    updatedAt: new Date().toISOString()
  };
}

function saveFinalScore(username, password, className, studentNo, column, value) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  // Only allow saving whitelisted columns
  const ALLOWED = ['Chapter 4', 'Chapter 5', 'ASAT'];
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

// ── FINAL DASHBOARD + REMEDIAL (read-only) ───────────────────
function getSheetByGid_(spreadsheet, gid) {
  const target = Number(gid);
  const sheets = spreadsheet.getSheets();
  for (let i = 0; i < sheets.length; i++) {
    if (sheets[i].getSheetId() === target) return sheets[i];
  }
  return null;
}

function finalDashboardRows_() {
  const ss = SpreadsheetApp.openById(FINAL_COMPARISON_SHEET_ID);
  const sheet = getSheetByGid_(ss, FINAL_DASHBOARD_SHEET_GID);
  if (!sheet) throw new Error('Final dashboard sheet not found');

  const data = sheet.getDataRange().getValues();
  const rows = [];
  for (let r = 1; r < data.length; r++) {
    for (let c = 0; c + 7 < data[r].length; c += 9) {
      const globalNo = data[r][c];
      const className = String(data[r][c + 1] || '').trim();
      const studentNo = data[r][c + 2];
      const name = String(data[r][c + 3] || '').trim();
      const result = String(data[r][c + 7] || '').trim();
      if (!className || !studentNo || !name || className === 'Class' || name === 'Name') continue;
      rows.push({
        globalNo: globalNo,
        className: className,
        studentNo: studentNo,
        name: name,
        sem1: normalizeSemScore_(data[r][c + 4]),
        sem2: normalizeSemScore_(data[r][c + 5]),
        difference: normalizeSemScore_(data[r][c + 6]),
        result: result
      });
    }
  }
  return rows;
}

function finalDashboardRemedial_(asatScore) {
  if (asatScore === '' || asatScore === null || asatScore === undefined || asatScore === '-') {
    return {
      type: 'pending',
      label: 'Waiting for ASAT',
      task: 'Your ASAT score is not available yet.',
      gap: '',
      link: '',
      linkLabel: 'No group yet'
    };
  }
  const score = Number(asatScore);
  if (isNaN(score)) {
    return {
      type: 'pending',
      label: 'Waiting for score',
      task: 'Your ASAT score is not available yet.',
      gap: '',
      link: '',
      linkLabel: 'No group yet'
    };
  }
  if (score >= 75) {
    return {
      type: 'enrichment',
      label: 'Enrichment',
      task: 'No remedial needed. You may join enrichment to improve your ASAT score by 1-5 points.',
      gap: 0,
      link: REMEDIAL_LINKS.enrichment,
      linkLabel: 'Join Enrichment Group'
    };
  }
  const gap = Math.ceil((75 - score) * 100) / 100;
  if (gap <= 15) {
    return {
      type: 'gap1',
      label: 'Gap 1-15',
      task: 'Read a paragraph offline and meet the teacher.',
      gap: gap,
      link: REMEDIAL_LINKS.gap1to15,
      linkLabel: 'Join Gap 1-15 Group'
    };
  }
  if (gap <= 25) {
    return {
      type: 'gap2',
      label: 'Gap 16-25',
      task: 'Make a poster summary of Semester 2 material.',
      gap: gap,
      link: REMEDIAL_LINKS.gap16to25,
      linkLabel: 'Join Gap 16-25 Group'
    };
  }
  return {
    type: 'gap3',
    label: 'Gap 26+',
    task: 'Write a complete written summary of Semester 2 material.',
    gap: gap,
    link: REMEDIAL_LINKS.gap26plus,
    linkLabel: 'Join Gap 26+ Group'
  };
}

function getDashboardChapter_(className, studentNo, chapter) {
  const data = getChapterScores(className, chapter);
  const students = data && data.students ? data.students : [];
  const row = students.find(function(student) { return String(student.no) === String(studentNo); });
  return {
    chapter: chapter,
    inputCols: data && data.inputCols ? data.inputCols : [],
    student: row || null
  };
}

function getDashboardOverall_(className, studentNo) {
  const data = getFinalScores(className);
  const students = data && data.students ? data.students : [];
  const row = students.find(function(student) { return String(student.no) === String(studentNo); });
  if (!row) return null;
  return {
    inputCols: data.inputCols || [],
    calcCols: data.calcCols || [],
    student: row
  };
}

function finalScoreBands_(rows) {
  const bands = [
    { label: '90-100', count: 0 },
    { label: '80-89', count: 0 },
    { label: '75-79', count: 0 },
    { label: 'Below 75', count: 0 }
  ];
  const scores = rows.map(function(row) { return Number(row.sem2); }).filter(function(num) { return !isNaN(num); });
  scores.forEach(function(score) {
    if (score >= 90) bands[0].count++;
    else if (score >= 80) bands[1].count++;
    else if (score >= 75) bands[2].count++;
    else bands[3].count++;
  });
  return bands.map(function(band) {
    return {
      label: band.label,
      count: band.count,
      percent: scores.length ? Math.round((band.count / scores.length) * 1000) / 10 : 0
    };
  });
}

function getFinalDashboardStudents(className) {
  const cls = String(className || '').trim();
  const rows = finalDashboardRows_().filter(function(row) { return !cls || row.className === cls; });
  const rosterStudents = cls ? getSemRosterStudents_(cls) : [];
  if (rosterStudents.length) {
    const rowsByNo = {};
    rows.forEach(function(row) { rowsByNo[String(row.studentNo)] = row; });
    return {
      success: true,
      className: cls,
      students: rosterStudents
        .filter(function(student) { return !!rowsByNo[String(student.no)]; })
        .map(function(student) { return { no: student.no, name: student.name }; })
    };
  }
  return {
    success: true,
    className: cls,
    students: rows.map(function(row) { return { no: row.studentNo, name: row.name }; })
  };
}

function getFinalDashboard(className, studentNo) {
  const cls = String(className || '').trim();
  const no = String(studentNo || '').trim();
  if (!cls || !no) return { success: false, error: 'Missing className or studentNo' };

  const classRows = finalDashboardRows_().filter(function(row) { return row.className === cls; });
  const row = classRows.find(function(item) { return String(item.studentNo) === no; });
  if (!row) return { success: false, error: 'Student not found' };

  const rosterStudent = getSemRosterStudents_(cls).find(function(student) { return String(student.no) === no; });
  const student = Object.assign({}, row);
  if (rosterStudent) {
    student.studentNo = rosterStudent.no;
    student.name = rosterStudent.name;
  }

  const sem1Rows = classRows.filter(function(item) { return item.sem1 !== '' && !isNaN(Number(item.sem1)); });
  const sem2Rows = classRows.filter(function(item) { return item.sem2 !== '' && !isNaN(Number(item.sem2)); });
  const chapters = [
    getDashboardChapter_(cls, no, 4),
    getDashboardChapter_(cls, no, 5)
  ];
  const overall = getDashboardOverall_(cls, no);
  const asatScore = overall && overall.student && overall.student.scores ? overall.student.scores.ASAT : '';
  const asatNum = (asatScore === '' || asatScore === null || asatScore === undefined || asatScore === '-') ? NaN : Number(asatScore);
  return {
    success: true,
    className: cls,
    classLabel: semClassLabel_(cls),
    student: student,
    chapters: chapters,
    overall: overall,
    asat: {
      score: asatScore,
      gap: isNaN(asatNum) ? '' : Math.max(0, Math.ceil((75 - asatNum) * 100) / 100)
    },
    remedial: finalDashboardRemedial_(asatScore),
    summary: {
      classSize: classRows.length,
      sem1Average: averageSemScores_(sem1Rows.map(function(item) { return item.sem1; })),
      sem2Average: averageSemScores_(sem2Rows.map(function(item) { return item.sem2; })),
      rankSem1: rankSemScore_(classRows, student, 'sem1'),
      rankSem2: rankSemScore_(classRows, student, 'sem2'),
      improvedCount: classRows.filter(function(item) { return String(item.result).toLowerCase() === 'improved'; }).length,
      sameCount: classRows.filter(function(item) { return String(item.result).toLowerCase() === 'same'; }).length,
      decreasedCount: classRows.filter(function(item) { return String(item.result).toLowerCase() === 'decreased'; }).length,
      completeCount: sem2Rows.length,
      bands: finalScoreBands_(classRows)
    },
    updatedAt: new Date().toISOString()
  };
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

// ── LESSON MATERIALS (stored in Script Properties) ───────────
function getMaterials(chapter) {
  const key = 'materials_ch' + chapter;
  const raw = PropertiesService.getScriptProperties().getProperty(key);
  if (!raw) return { materials: [] };
  try { return { materials: JSON.parse(raw) }; }
  catch(e) { return { materials: [] }; }
}

function addMaterial(username, password, chapter, title, fileId, fileType) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const key = 'materials_ch' + chapter;
  const props = PropertiesService.getScriptProperties();
  const raw = props.getProperty(key);
  let materials = [];
  try { if (raw) materials = JSON.parse(raw); } catch(e) {}
  // Check duplicate
  if (materials.find(function(m) { return m.fileId === fileId; })) return { success: false, error: 'Already exists' };
  materials.push({ title: title, fileId: fileId, fileType: fileType || 'PDF', addedAt: new Date().toISOString() });
  props.setProperty(key, JSON.stringify(materials));
  return { success: true, materials: materials };
}

function deleteMaterial(username, password, chapter, fileId) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  const key = 'materials_ch' + chapter;
  const props = PropertiesService.getScriptProperties();
  const raw = props.getProperty(key);
  let materials = [];
  try { if (raw) materials = JSON.parse(raw); } catch(e) {}
  materials = materials.filter(function(m) { return m.fileId !== fileId; });
  props.setProperty(key, JSON.stringify(materials));
  return { success: true, materials: materials };
}

// ── ANNOUNCEMENTS ─────────────────────────────────────────────
function getAnnouncement() {
  var raw = PropertiesService.getScriptProperties().getProperty('announcement');
  if (!raw) return { announcement: null };
  try { return { announcement: JSON.parse(raw) }; }
  catch(e) { return { announcement: null }; }
}

function setAnnouncement(username, password, title, body, type) {
  if (!authOk(username, password)) return { success: false, error: 'Unauthorized' };
  var ann = {
    title: title || '',
    body: body || '',
    type: type || 'info',  // info | warning | success
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
