// ============================================================
//  ENGLISH QUEST — PHOTO GALLERY API
//  Separate Apps Script project — paste into Code.gs
//  Redeploy as new version after updating!
// ============================================================

const GALLERY_FOLDER_ID  = '1ua2Rqfyk6gzusPziY1lWmA25qwY7yHG-';
const GALLERY_SHEET_NAME = 'Gallery';
const TEACHER_USER       = 'teacher';
const TEACHER_HASH       = '39de0394764747426b997b945770fd60661cbd072051131da2cb99d6d8dd8430';
const WRITES_ENABLED     = false;

// Sheet columns: Timestamp | Class | Name | StudentNo | Caption | FileId | Status
// Status: pending | approved | rejected

function doGet(e) {
  var action   = e.parameter.action   || '';
  var callback = e.parameter.callback || '';
  var result;
  try {
    if      (action === 'getApproved') result = getApproved();
    else if (action === 'getPending')  result = getPending(e.parameter.username, e.parameter.password);
    else if (action === 'hasPending')  result = hasPending(e.parameter.cls, e.parameter.studentNo);
    else if (action === 'setStatus')   result = setStatus(e.parameter.username, e.parameter.password, e.parameter.rowIndex, e.parameter.status);
    else if (action === 'upload')      result = upload(e.parameter);
    else if (action === 'ping')        result = { ok: true };
    else result = { error: 'Unknown action' };
  } catch(err) { result = { error: err.message }; }

  var json = JSON.stringify(result);
  if (callback) return ContentService.createTextOutput(callback + '(' + json + ')').setMimeType(ContentService.MimeType.JAVASCRIPT);
  return ContentService.createTextOutput(json).setMimeType(ContentService.MimeType.JSON);
}

// ── HELPERS ──────────────────────────────────────────────────
function sha256Hex(value) {
  var bytes = Utilities.computeDigest(Utilities.DigestAlgorithm.SHA_256, String(value || ''), Utilities.Charset.UTF_8);
  return bytes.map(function(byte) {
    var v = byte;
    if (v < 0) v += 256;
    return ('0' + v.toString(16)).slice(-2);
  }).join('');
}

function authOk(username, password) {
  return username === TEACHER_USER && sha256Hex(password) === TEACHER_HASH;
}

function writeBlocked() {
  return { success: false, error: 'Security maintenance: gallery writes are temporarily disabled.' };
}

function getSheet() {
  var ss    = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(GALLERY_SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(GALLERY_SHEET_NAME);
    sheet.appendRow(['Timestamp', 'Class', 'Name', 'StudentNo', 'Caption', 'FileId', 'Status']);
    sheet.getRange(1, 1, 1, 7).setFontWeight('bold');
  }
  return sheet;
}

function trySetSharingSafe(file) {
  try {
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  } catch(e) {
    // School domain policy may block public sharing — upload still succeeds
  }
}

function uploadToDrive(base64Data, fileName, mimeType) {
  var folder = DriveApp.getFolderById(GALLERY_FOLDER_ID);
  var bytes  = Utilities.base64Decode(base64Data);
  var blob   = Utilities.newBlob(bytes, mimeType, fileName);
  var file   = folder.createFile(blob);
  trySetSharingSafe(file);
  return file.getId();
}

// ── GET APPROVED PHOTOS ───────────────────────────────────────
function getApproved() {
  var sheet = getSheet();
  var data  = sheet.getDataRange().getValues();
  var photos = [];
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][6]).toLowerCase() === 'approved') {
      photos.push({
        rowIndex: r + 1,
        cls:      data[r][1],
        name:     data[r][2],
        caption:  data[r][4],
        fileId:   data[r][5],
        timestamp: data[r][0]
      });
    }
  }
  // Most recent first
  photos.reverse();
  return { photos: photos };
}

// ── GET PENDING PHOTOS (TEACHER ONLY) ────────────────────────
function getPending(username, password) {
  if (!authOk(username, password))
    return { success: false, error: 'Unauthorized' };
  var sheet = getSheet();
  var data  = sheet.getDataRange().getValues();
  var photos = [];
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][6]).toLowerCase() === 'pending') {
      photos.push({
        rowIndex: r + 1,
        cls:      data[r][1],
        name:     data[r][2],
        caption:  data[r][4],
        fileId:   data[r][5],
        timestamp: data[r][0]
      });
    }
  }
  return { photos: photos };
}

// ── CHECK IF STUDENT HAS PENDING ─────────────────────────────
function hasPending(cls, studentNo) {
  var sheet = getSheet();
  var data  = sheet.getDataRange().getValues();
  for (var r = 1; r < data.length; r++) {
    if (String(data[r][1]) === String(cls) &&
        String(data[r][3]) === String(studentNo) &&
        String(data[r][6]).toLowerCase() === 'pending') {
      return { hasPending: true };
    }
  }
  return { hasPending: false };
}

// ── SET STATUS (APPROVE / REJECT) ─────────────────────────────
function setStatus(username, password, rowIndex, status) {
  if (!WRITES_ENABLED) return writeBlocked();
  if (!authOk(username, password))
    return { success: false, error: 'Unauthorized' };
  if (status !== 'approved' && status !== 'rejected')
    return { success: false, error: 'Invalid status' };

  var sheet = getSheet();
  var row   = parseInt(rowIndex);
  if (isNaN(row) || row < 2) return { success: false, error: 'Invalid row' };

  sheet.getRange(row, 7).setValue(status);
  SpreadsheetApp.flush();

  // If rejected, delete the file from Drive too
  if (status === 'rejected') {
    try {
      var fileId = sheet.getRange(row, 6).getValue();
      if (fileId) DriveApp.getFileById(fileId).setTrashed(true);
    } catch(e) {}
  }

  return { success: true, status: status };
}

// ── UPLOAD PHOTO ──────────────────────────────────────────────
function upload(params) {
  if (!WRITES_ENABLED) return writeBlocked();
  var cls       = params.cls       || '';
  var name      = params.name      || '';
  var studentNo = params.studentNo || '';
  var caption   = params.caption   || '';
  var fileName  = params.fileName  || 'photo.jpg';
  var mimeType  = params.mimeType  || 'image/jpeg';
  var base64    = params.base64    || '';
  var direct    = params.direct === 'true'; // teacher direct upload = auto-approved

  if (!base64) return { success: false, error: 'No image data' };
  if (!cls || !name) return { success: false, error: 'Missing name or class' };

  // Teacher direct upload auth check
  if (direct) {
    if (!authOk(params.username, params.password))
      return { success: false, error: 'Unauthorized' };
  }

  // Check pending limit for students
  if (!direct && cls !== 'Teacher') {
    var pending = hasPending(cls, studentNo);
    if (pending.hasPending) return { success: false, error: 'You already have a pending submission.' };
  }

  // Upload to Drive
  var fileId = uploadToDrive(base64, fileName, mimeType);

  // Save to sheet
  var sheet  = getSheet();
  var status = direct ? 'approved' : 'pending';
  sheet.appendRow([new Date(), cls, name, studentNo, caption, fileId, status]);
  SpreadsheetApp.flush();

  return { success: true, fileId: fileId, status: status };
}
