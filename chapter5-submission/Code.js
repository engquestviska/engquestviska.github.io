/**
 * @OnlyCurrentDoc false
 */
// Required scopes: Drive, Spreadsheets
const FOLDER_ID = '1mrV8NBGb8SyxX6oyxU_GNSJM_Q9GF3bP';
const SHEET_NAME = 'Students';
const RESPONSES_SHEET = 'Responses';

function doGet() {
  return HtmlService.createHtmlOutputFromFile('Index')
    .setTitle('Chapter 5 Task Submission')
    .setXFrameOptionsMode(HtmlService.XFrameOptionsMode.ALLOWALL)
    .addMetaTag("viewport", "width=device-width, initial-scale=1.0");
}

// Open Chapter 5 spreadsheet directly
function getSpreadsheet() {
  return SpreadsheetApp.openById('1WqvB1SkFEh-lnZ3mLAFzArCuHWqnuxXDbGz_gLZ3zyQ');
}

function getClasses() {
  var sheet = getSpreadsheet().getSheetByName(SHEET_NAME);
  var headers = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getValues()[0];
  return headers.filter(function(h) { return h !== ''; });
}

function getStudents(className) {
  var sheet = getSpreadsheet().getSheetByName(SHEET_NAME);
  var data = sheet.getDataRange().getValues();
  var headers = data[0];
  var colIndex = headers.indexOf(className);
  if (colIndex === -1) return [];
  var students = [];
  for (var i = 1; i < data.length; i++) {
    if (data[i][colIndex]) students.push(data[i][colIndex]);
  }
  return students;
}

function trySetSharingSafe(file) {
  try {
    file.setSharing(DriveApp.Access.ANYONE_WITH_LINK, DriveApp.Permission.VIEW);
  } catch(e) {
    // School domain policy may block public sharing — upload still succeeds
  }
}

function uploadFile(base64Data, fileName, mimeType, className, studentName, taskLabel) {
  try {
    var mainFolder = DriveApp.getFolderById(FOLDER_ID);

    var classFolders = mainFolder.getFoldersByName(className);
    var classFolder = classFolders.hasNext() ? classFolders.next() : mainFolder.createFolder(className);

    var studentFolders = classFolder.getFoldersByName(studentName);
    var studentFolder = studentFolders.hasNext() ? studentFolders.next() : classFolder.createFolder(studentName);

    var bytes = Utilities.base64Decode(base64Data);
    var blob = Utilities.newBlob(bytes, mimeType, taskLabel + '_' + fileName);
    var file = studentFolder.createFile(blob);
    trySetSharingSafe(file);

    return { success: true, fileId: file.getId(), url: file.getUrl() };
  } catch(e) {
    return { success: false, error: e.toString() };
  }
}

// Returns { submitted: false } or { submitted: true, tasks: [bool x5], timestamp: Date }
function checkSubmission(className, studentName) {
  try {
    var sheet = getSpreadsheet().getSheetByName(RESPONSES_SHEET);
    if (!sheet) return { submitted: false };
    var data = sheet.getDataRange().getValues();
    for (var r = 1; r < data.length; r++) {
      if (data[r][1] === className && data[r][2] === studentName) {
        var tasks = [];
        for (var t = 0; t < 5; t++) {
          tasks.push(!!String(data[r][3 + t] || '').trim());
        }
        return { submitted: true, tasks: tasks, timestamp: String(data[r][0]) };
      }
    }
    return { submitted: false };
  } catch(e) {
    return { submitted: false };
  }
}

// taskFileIds: array of 5 items (Task 1-5), each is an array of fileIds
function submitForm(className, studentName, taskFileIds) {
  try {
    var ss = getSpreadsheet();
    var sheet = ss.getSheetByName(RESPONSES_SHEET);

    if (!sheet) {
      sheet = ss.insertSheet(RESPONSES_SHEET);
      var headers = ['Timestamp', 'Class', 'Student Name'];
      for (var i = 1; i <= 5; i++) headers.push('Task ' + i + ' File');
      sheet.appendRow(headers);
      sheet.getRange(1, 1, 1, headers.length).setFontWeight('bold');
    }

    var TASK_LABELS = ['Task 1','Task 2','Task 3','Task 4','Task 5'];

    var row = [new Date(), className, studentName];
    for (var j = 0; j < 5; j++) {
      var ids = taskFileIds[j] || [];
      var links = ids.map(function(id) {
        return id ? 'https://drive.google.com/file/d/' + id + '/view' : '';
      }).filter(Boolean).join('\n');
      row.push(links);
    }

    // Overwrite if student already submitted
    var data = sheet.getDataRange().getValues();
    var existingRow = -1;
    for (var r = 1; r < data.length; r++) {
      if (data[r][1] === className && data[r][2] === studentName) {
        existingRow = r + 1;
        break;
      }
    }

    if (existingRow !== -1) {
      sheet.getRange(existingRow, 1, 1, row.length).setValues([row]);
    } else {
      sheet.appendRow(row);
    }

    // Send email notification if teacher_email property is set
    try {
      var notifyEmail = PropertiesService.getScriptProperties().getProperty('teacher_email');
      if (notifyEmail) {
        var submittedLabels = [];
        for (var n = 0; n < taskFileIds.length; n++) {
          if (taskFileIds[n] && taskFileIds[n][0]) submittedLabels.push('Task ' + (n + 1));
        }
        MailApp.sendEmail({
          to: notifyEmail,
          subject: '[English Quest] Ch5 Submission — ' + studentName + ' (' + className + ')',
          body: 'A Chapter 5 submission has been received.\n\n' +
                'Class: ' + className + '\n' +
                'Student: ' + studentName + '\n' +
                'Tasks submitted: ' + (submittedLabels.length ? submittedLabels.join(', ') : 'none') + '\n' +
                'Time: ' + new Date().toLocaleString('id-ID') + '\n\n' +
                'View responses: https://docs.google.com/spreadsheets/d/1WqvB1SkFEh-lnZ3mLAFzArCuHWqnuxXDbGz_gLZ3zyQ'
        });
      }
    } catch(mailErr) { /* email is optional — never fail the submission */ }

    return { success: true };
  } catch(e) {
    return { success: false, error: e.toString() };
  }
}