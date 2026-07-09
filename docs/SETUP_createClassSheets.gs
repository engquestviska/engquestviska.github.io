/**
 * ONE-TIME SETUP — creates a fresh score spreadsheet for every new-year class,
 * each with the exact tabs + columns the English Quest API reads, plus 36
 * numbered students ("Student 1".."Student 36"). Run ONCE, copy the logged IDs
 * into the chat, then you can delete this function.
 */
function SETUP_createClassSheets() {
  var CLASSES = [
    ['XE1','X E-1'], ['XE2','X E-2'], ['XE3','X E-3'], ['XE4','X E-4'], ['XE5','X E-5'],
    ['XIF7','XI F-7'], ['XIF8','XI F-8'], ['XIF9','XI F-9']
  ];
  var N = 36;
  var ids = {};

  CLASSES.forEach(function(c) {
    var id = c[0], label = c[1];
    var ss = SpreadsheetApp.create('English Quest ' + label + ' — Scores 2026');

    // 1) Scores (MUST be the first tab)
    var scores = ss.getSheets()[0].setName('Scores');
    fillTab_(scores, ['No','Name','Nickname','Chapter 1','Chapter 2','Chapter 3','ASAT','Average','Final Score','Indicator'], N);

    // 2) Chapter tabs (Semester 1 = Chapters 1-3)
    ['1','2','3'].forEach(function(ch) {
      fillTab_(ss.insertSheet('Chapter_' + ch),
        ['No','Name','Nickname','Task 1','Task 2','Task 3','Formative','Summative','Average','Final Score','Indicator'], N);
    });

    // 3) Task_Status
    fillTab_(ss.insertSheet('Task_Status'),
      ['No','Name','Nickname','C1T1','C1T2','C2T1','C2T2','C3T1','C3T2'], N);

    // 4) Activeness (Total auto-sums with the participation weights)
    var act = ss.insertSheet('Activeness');
    fillTab_(act, ['No','Name','Nickname','Vocabulary Box','Answering','Presenting','Doing Task On Time','Helping Hand','Quiz','Total','Indicator'], N);
    for (var r = 2; r <= N + 1; r++) {
      // Total = VB*1 + Answering*2 + Presenting*5 + Task On Time*2 + Helping*1 + Quiz*1
      act.getRange(r, 10).setFormula('=D' + r + '+E' + r + '*2+F' + r + '*5+G' + r + '*2+H' + r + '+I' + r);
    }

    // 5) Strike
    fillTab_(ss.insertSheet('Strike'),
      ['No','Name','Nickname','Strike1','Strike2','Strike3','Strike4','Total','Date/Time','Reason'], N);

    ids[id] = ss.getId();
  });

  PropertiesService.getScriptProperties().setProperty('NEW_SHEET_IDS', JSON.stringify(ids));
  Logger.log('DONE — copy everything between the braces into the chat:\n' + JSON.stringify(ids, null, 2));
  return ids;
}

function fillTab_(sheet, headers, n) {
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]).setFontWeight('bold');
  var rows = [];
  for (var i = 1; i <= n; i++) {
    var row = [];
    for (var j = 0; j < headers.length; j++) row.push('');
    row[0] = i;                // No
    row[1] = 'Student ' + i;   // Name (placeholder until real names go in)
    rows.push(row);
  }
  sheet.getRange(2, 1, n, headers.length).setValues(rows);
  sheet.setFrozenRows(1);
}
