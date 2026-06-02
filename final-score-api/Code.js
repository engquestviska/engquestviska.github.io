const CLASSES = ["XE1", "XE4", "XE5", "XE6", "XE7", "XE8", "XE9", "XE10", "XE11"];
const FINAL_DASHBOARD_SHEET_GID = 1145427024;
const REMEDIAL_LINKS = {
  enrichment: "https://chat.whatsapp.com/L9isr7aTbeJKZh5YeMCnBE",
  gap1to15: "https://chat.whatsapp.com/JTrmrhnBz1v0yhBpMGatpZ",
  gap16to25: "https://chat.whatsapp.com/GIIRbntJuQYK32dwI3xnEV",
  gap26plus: "https://chat.whatsapp.com/BnUByekB5QR3wvMb3Vkmf4"
};

const SOURCES = {
  XE1: "https://docs.google.com/spreadsheets/d/1X3PBpMCEvglTA92HST8286dY_OWe5lw7tdJHPpNjggk/edit",
  XE4: "https://docs.google.com/spreadsheets/d/16CT_wxAGV0mLRhvlIFpXRzKobdnpnj2-DcAJSiz14JY/edit",
  XE5: "https://docs.google.com/spreadsheets/d/1-GMJ8amrcE-r_N1qJL8rsrxEItRraN-97FgyySJgOxg/edit",
  XE6: "https://docs.google.com/spreadsheets/d/1MxqzK2JOwMyc2yfUSwCgIW4siWCsJe2R_o_cF11mOHU/edit",
  XE7: "https://docs.google.com/spreadsheets/d/1D9iYhBLNPhFsgTZ15vQOHFn22RYCI6HFW10aC4HwJK4/edit",
  XE8: "https://docs.google.com/spreadsheets/d/1skj7KBc11zaFa07FGu0r4bEHvDrV-oHZm4qKhBLimpg/edit",
  XE9: "https://docs.google.com/spreadsheets/d/19Pn0JyIZRAgzhUGs_W519vjmwcnivair6hsqkIVyOnE/edit",
  XE10: "https://docs.google.com/spreadsheets/d/1UEgm1spqzNGPbYl9a9CmLavwALVLG_6o2gEvN3R_LKM/edit",
  XE11: "https://docs.google.com/spreadsheets/d/1yt6km9wnBYRe61bHaSjDfJWkmqz884v1cogp836Ioa0/edit"
};

const SEM1_XE1 = {
  "ANGGITA ANASTASYA PUTRI": 82,
  "ANNISA FEBRIYANTI AZIZAH": 83,
  "APRILIA GALUH PURNAMASARI": 78,
  "ARDHINDA NAOMI PUTRI": 81,
  "AUREL ANNASTASYA": 80,
  "AVIS OMEYRO SETIAWAN": 84,
  "BAIM DWI FANO": 76,
  "CLARESTA JANET VOISHELA": 83,
  "DANIEL PUTRA WAHANA": 78,
  "DEAN JOSHUA": 77,
  "DEVANNDRA JAVAS NARARYA": 83,
  "DINDA KAYLA": 81,
  "ELLENA ALLYSA SETIANANG": 80,
  "ELVANUELLE EDGAR PUTRA JAYANTO": 79,
  "EXCEL JUAN PRATAMA": 34,
  "FAUZAN DAVIN PUTRA YUNANTO": 78,
  "GADIS LINTANG WAHYU ASIH": 79,
  "GANDRUNG JESHUA CANGGIH CAKSANA": 84,
  "GRACIA LARASATI": 90,
  "GRACIA THEOVANI": 83,
  "HEKA TRAH VENDIKA": 78,
  "HELENA KRISTIANINGRUM": 90,
  "KEVIN REZANUARTA ABDIL GIFARI": 78,
  "KRISTINA KRISEL WAHYU PRATIWI": 90,
  "LINGGOM GATAN NADEAK": 79,
  "MELINDA PATRICIA": 86,
  "MICHELLE OLIVIA MAGDALENA": 78,
  "NA ILLA INGGIT MEYSANDRA": 84,
  "NATANIA PRATIWI": 94,
  "NATASHA DEANDRA KRISHNA PUTRI": 88,
  "RAPHAEL GRISTO MAYNARDIVE": 83,
  "RUTH CLARICE AURELLIA BERNADIGTA": 90,
  "ZEFANYA MEINSHA HANDAYANI": 82,
  "ZEFANYA PUTRI MAHARANI PRAMONO": 92
};

function setupEnglishQuestAllSync() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();

  CLASSES.forEach(cls => {
    const main = ss.getSheetByName(cls);
    if (!main) return;

    const ch4 = getOrCreateSheet_(ss, `Import_${cls}_Ch4`);
    const ch5 = getOrCreateSheet_(ss, `Import_${cls}_Ch5`);
    const fin = getOrCreateSheet_(ss, `Import_${cls}_Final`);

    ch4.clear().getRange("A1").setFormula(`=IMPORTRANGE("${SOURCES[cls]}","Chapter_4!A:Z")`);
    ch5.clear().getRange("A1").setFormula(`=IMPORTRANGE("${SOURCES[cls]}","Chapter_5!A:Z")`);
    fin.clear().getRange("A1").setFormula(`=IMPORTRANGE("${SOURCES[cls]}","Scores!A:Z")`);

    ch4.hideSheet();
    ch5.hideSheet();
    fin.hideSheet();

    const lastRow = getLastStudentRow_(main);
    for (let row = 3; row <= lastRow; row++) {
      for (let col = 3; col <= 9; col++) {
        main.getRange(row, col).setFormula(
          `=IFERROR(INDEX(Import_${cls}_Ch4!$D:$J, MATCH($B${row}, Import_${cls}_Ch4!$B:$B, 0), ${col - 2}), "")`
        );
      }

      for (let col = 10; col <= 14; col++) {
        main.getRange(row, col).setFormula(
          `=IFERROR(INDEX(Import_${cls}_Ch5!$D:$H, MATCH($B${row}, Import_${cls}_Ch5!$B:$B, 0), ${col - 9}), "")`
        );
      }

      main.getRange(row, 15).setFormula(
        `=IFERROR(INDEX(Import_${cls}_Ch4!$K:$K, MATCH($B${row}, Import_${cls}_Ch4!$B:$B, 0)), "")`
      );
      main.getRange(row, 16).setFormula(
        `=IFERROR(INDEX(Import_${cls}_Ch5!$I:$I, MATCH($B${row}, Import_${cls}_Ch5!$B:$B, 0)), "")`
      );
      main.getRange(row, 17).setFormula(
        `=IFERROR(INDEX(Import_${cls}_Final!$F:$F, MATCH($B${row}, Import_${cls}_Final!$B:$B, 0)), "")`
      );
    }
  });
}

function fixXE1RosterKevin() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName("XE1");
  if (!sheet) throw new Error("XE1 sheet not found");

  const roster = [
    [23, "KEVIN REZANUARTA ABDIL GIFARI"],
    [24, "KRISTINA KRISEL WAHYU PRATIWI"],
    [25, "LINGGOM GATAN NADEAK"],
    [26, "MELINDA PATRICIA"],
    [27, "MICHELLE OLIVIA MAGDALENA"],
    [28, "NA ILLA INGGIT MEYSANDRA"],
    [29, "NATANIA PRATIWI"],
    [30, "NATASHA DEANDRA KRISHNA PUTRI"],
    [31, "RAPHAEL GRISTO MAYNARDIVE"],
    [32, "RUTH CLARICE AURELLIA BERNADIGTA"],
    [33, "ZEFANYA MEINSHA HANDAYANI"],
    [34, "ZEFANYA PUTRI MAHARANI PRAMONO"],
    [35, "."],
    ["", ""]
  ];

  sheet.getRange(25, 1, roster.length, 2).setValues(roster);
  sheet.getRange(37, 3, 2, 15).clearContent();
}

function rebuildSemComparison() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName("Sem 1 vs Sem 2");
  if (!sheet) throw new Error("Sem 1 vs Sem 2 sheet not found");

  const allRows = [];
  let runningNo = 1;

  CLASSES.forEach(cls => {
    const classSheet = ss.getSheetByName(cls);
    if (!classSheet) return;

    const rows = classSheet.getRange("A3:R38").getValues();
    rows.forEach(row => {
      const studentNo = row[0];
      const name = String(row[1] || "").trim();
      if (!name || name === ".") return;

      const sem1 = cls === "XE1" ? (SEM1_XE1[name] ?? "") : "";
      const targetRow = allRows.length + 4;

      allRows.push([
        runningNo++,
        cls,
        studentNo,
        name,
        sem1,
        `=IFERROR(VLOOKUP(D${targetRow}, ${cls}!B:R, 17, FALSE), "")`,
        `=IF(OR(E${targetRow}="",F${targetRow}=""),"",F${targetRow}-E${targetRow})`,
        `=IF(F${targetRow}="","Waiting Sem 2",IF(G${targetRow}>0,"Improved",IF(G${targetRow}<0,"Decreased","Same")))`
      ]);
    });
  });

  sheet.getRange("A4:H400").clearContent();
  sheet.getRange(4, 1, allRows.length, 8).setValues(allRows);
  sheet.getRange(4, 5, allRows.length, 3).setNumberFormat("0.00");
  restoreSemComparisonRightSummary();
}

function restoreSemComparisonRightSummary() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName("Sem 1 vs Sem 2");
  if (!sheet) throw new Error("Sem 1 vs Sem 2 sheet not found");

  const rows = [
    ["Class", "Students", "Sem 1 Average", "Sem 2 Average", "Avg Difference", "Improved Count"],
    ...CLASSES.map((cls, i) => {
      const row = i + 4;
      return [
        cls,
        `=COUNTIF(B:B,"${cls}")`,
        `=AVERAGEIF(B:B,"${cls}",E:E)`,
        `=AVERAGEIF(B:B,"${cls}",F:F)`,
        `=M${row}-L${row}`,
        `=COUNTIFS(B:B,"${cls}",H:H,"Improved")`
      ];
    })
  ];

  sheet.getRange("J3:O12").setValues(rows);
  sheet.getRange("J3:O3")
    .setBackground("#174E75")
    .setFontColor("white")
    .setFontWeight("bold")
    .setHorizontalAlignment("center");
  sheet.getRange("L4:N12").setNumberFormat("0.00");
  sheet.getRange("K4:K12").setNumberFormat("0");
  sheet.getRange("O4:O12").setNumberFormat("0");
}

function createLiveImprovedDecreasedSideways() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  let sheet = ss.getSheetByName("Improved vs Decreased");
  if (!sheet) sheet = ss.insertSheet("Improved vs Decreased");

  const neededCols = 1 + (CLASSES.length - 1) * 9 + 8;
  const neededRows = 125;

  if (sheet.getMaxColumns() < neededCols) {
    sheet.insertColumnsAfter(sheet.getMaxColumns(), neededCols - sheet.getMaxColumns());
  }
  if (sheet.getMaxRows() < neededRows) {
    sheet.insertRowsAfter(sheet.getMaxRows(), neededRows - sheet.getMaxRows());
  }

  sheet.clear();

  const headers = ["No.", "Class", "Student No.", "Name", "Sem 1", "Sem 2", "Diff", "Result"];

  CLASSES.forEach((cls, index) => {
    const startCol = 1 + index * 9;

    sheet.getRange(1, startCol, 1, 8)
      .merge()
      .setValue(cls)
      .setBackground("#174E75")
      .setFontColor("white")
      .setFontWeight("bold")
      .setFontSize(13)
      .setHorizontalAlignment("center");

    // Improved
    sheet.getRange(2, startCol, 1, 8)
      .merge()
      .setValue("Improved")
      .setBackground("#D9EAD3")
      .setFontWeight("bold");

    sheet.getRange(3, startCol, 1, 8)
      .setValues([headers])
      .setBackground("#115E7A")
      .setFontColor("white")
      .setFontWeight("bold")
      .setHorizontalAlignment("center");

    sheet.getRange(4, startCol).setFormula(
      `=IFERROR(FILTER('Sem 1 vs Sem 2'!A:H,'Sem 1 vs Sem 2'!B:B="${cls}",'Sem 1 vs Sem 2'!H:H="Improved"),"")`
    );

    // Same
    const sameRow = 42;

    sheet.getRange(sameRow, startCol, 1, 8)
      .merge()
      .setValue("Same")
      .setBackground("#FFF2CC")
      .setFontWeight("bold");

    sheet.getRange(sameRow + 1, startCol, 1, 8)
      .setValues([headers])
      .setBackground("#115E7A")
      .setFontColor("white")
      .setFontWeight("bold")
      .setHorizontalAlignment("center");

    sheet.getRange(sameRow + 2, startCol).setFormula(
      `=IFERROR(FILTER('Sem 1 vs Sem 2'!A:H,'Sem 1 vs Sem 2'!B:B="${cls}",'Sem 1 vs Sem 2'!H:H="Same"),"")`
    );

    // Decreased
    const decreasedRow = 82;

    sheet.getRange(decreasedRow, startCol, 1, 8)
      .merge()
      .setValue("Decreased")
      .setBackground("#F4CCCC")
      .setFontWeight("bold");

    sheet.getRange(decreasedRow + 1, startCol, 1, 8)
      .setValues([headers])
      .setBackground("#115E7A")
      .setFontColor("white")
      .setFontWeight("bold")
      .setHorizontalAlignment("center");

    sheet.getRange(decreasedRow + 2, startCol).setFormula(
      `=IFERROR(FILTER('Sem 1 vs Sem 2'!A:H,'Sem 1 vs Sem 2'!B:B="${cls}",'Sem 1 vs Sem 2'!H:H="Decreased"),"")`
    );

    sheet.getRange(1, startCol, 125, 8).setVerticalAlignment("middle");
    sheet.getRange(4, startCol + 4, 115, 3).setNumberFormat("0.00");

    sheet.setColumnWidth(startCol, 46);
    sheet.setColumnWidth(startCol + 1, 48);
    sheet.setColumnWidth(startCol + 2, 78);
    sheet.setColumnWidth(startCol + 3, 210);
    sheet.setColumnWidth(startCol + 4, 70);
    sheet.setColumnWidth(startCol + 5, 70);
    sheet.setColumnWidth(startCol + 6, 60);
    sheet.setColumnWidth(startCol + 7, 82);
    sheet.setColumnWidth(startCol + 8, 20);
  });

  sheet.setFrozenRows(3);
}

function fixXE8RosterAndSemComparison() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();

  const roster = [
    [1, "ALLAN YOGIANTARA"],
    [2, "ANINDIA ARIMBI"],
    [3, "ANNISA NUR RAHMAH"],
    [4, "AQEELA NADIA PERWITHA PURISANT"],
    [5, "ARDHIN FATIMAH AZZAHRA"],
    [6, "ASTVAT ERETA MALKUTHA HR"],
    [7, "ATHIFA AURELIA"],
    [8, "DAVIN KHAIRUL AZAM"],
    [9, "DESFINA KURNIA ARBITA"],
    [10, "DITO RHAMADIANTO"],
    [11, "DUAN ADIYA UTAMA"],
    [12, "ELLISA MELATI PUTRI"],
    [13, "EVAN NANDANA PUTRA PAMBUDI"],
    [14, "FADHIL DAMAR RAJENDRA"],
    [15, "FADILLA ALISYA PUTRI"],
    [16, "FARID DZULFIKAR HARYANTO"],
    [17, "GADIS AMELIA AKHIRA"],
    [18, "GIANDRA NAYOGI"],
    [19, "GIVTA KHUSNUL KHOTIMAH"],
    [20, "HAFIZ LUCKY PRADYAWAN"],
    [21, "INTAN JULIANI"],
    [22, "JESSICA MAHARANI"],
    [23, "MARSHA ELYCIA PUTRI OKTAVIANANDA"],
    [24, "MUHAMMAD ABDUL BASITH MUGHNY"],
    [25, "MUHAMMAD FAUZAN ADHIMA AL-HABSYI"],
    [26, "MUHAMMAD REZAUL FATTAH"],
    [27, "MUTIARA NUR AZIZAH"],
    [28, "NABIL ARSHAQ GHOFRAN HAKEEM"],
    [29, "NAFIZA MAYYADA FARHANI"],
    [30, "NAJWA AZZAHRA MELANIE PUTRI"],
    [31, "REVA PUTRI NATAMENGGALA"],
    [32, "REZKY AGUNG RAMADHAN"],
    [33, "SAFA DWI ANGGITA"],
    [34, "SAFARAZ RAZIQ YUSTIAN"],
    [35, "SATRIO FAWWAZ BAGASKORO PUTRO"],
    [36, "TIKA INDI HAPSARI"]
  ];

  const xe8 = ss.getSheetByName("XE8");
  if (!xe8) throw new Error("XE8 sheet not found");

  // XE8 visible tab: row 3 is student no. 1.
  xe8.getRange(3, 1, roster.length, 2).setValues(roster);

  const sem = ss.getSheetByName("Sem 1 vs Sem 2");
  if (!sem) throw new Error("Sem 1 vs Sem 2 sheet not found");

  const sem1ByName = {
    "ALLAN YOGIANTARA": 76,
    "ANINDIA ARIMBI": 87,
    "ANNISA NUR RAHMAH": 90,
    "AQEELA NADIA PERWITHA PURISANT": 85,
    "ARDHIN FATIMAH AZZAHRA": 84,
    "ASTVAT ERETA MALKUTHA HR": "",
    "ATHIFA AURELIA": 87,
    "DAVIN KHAIRUL AZAM": 77,
    "DESFINA KURNIA ARBITA": 89,
    "DITO RHAMADIANTO": 82,
    "DUAN ADIYA UTAMA": 84,
    "ELLISA MELATI PUTRI": 85,
    "EVAN NANDANA PUTRA PAMBUDI": 91,
    "FADHIL DAMAR RAJENDRA": 79,
    "FADILLA ALISYA PUTRI": 88,
    "FARID DZULFIKAR HARYANTO": 75,
    "GADIS AMELIA AKHIRA": 79,
    "GIANDRA NAYOGI": 90,
    "GIVTA KHUSNUL KHOTIMAH": 85,
    "HAFIZ LUCKY PRADYAWAN": 82,
    "INTAN JULIANI": 75,
    "JESSICA MAHARANI": 81,
    "MARSHA ELYCIA PUTRI OKTAVIANANDA": 82,
    "MUHAMMAD ABDUL BASITH MUGHNY": 91,
    "MUHAMMAD FAUZAN ADHIMA AL-HABSYI": 89,
    "MUHAMMAD REZAUL FATTAH": 86,
    "MUTIARA NUR AZIZAH": 90,
    "NABIL ARSHAQ GHOFRAN HAKEEM": 88,
    "NAFIZA MAYYADA FARHANI": 90,
    "NAJWA AZZAHRA MELANIE PUTRI": 85,
    "REVA PUTRI NATAMENGGALA": 90,
    "REZKY AGUNG RAMADHAN": 83,
    "SAFA DWI ANGGITA": 84,
    "SAFARAZ RAZIQ YUSTIAN": 81,
    "SATRIO FAWWAZ BAGASKORO PUTRO": 90,
    "TIKA INDI HAPSARI": 84
  };

  const classValues = sem.getRange(1, 2, sem.getLastRow(), 1).getValues();
  let startRow = -1;

  for (let i = 0; i < classValues.length; i++) {
    if (classValues[i][0] === "XE8") {
      startRow = i + 1;
      break;
    }
  }

  if (startRow === -1) throw new Error("XE8 block not found in Sem 1 vs Sem 2");

  const rows = roster.map((r, i) => {
    const studentNo = r[0];
    const name = r[1];
    const row = startRow + i;

    return [
      row - 3,
      "XE8",
      studentNo,
      name,
      sem1ByName[name] ?? "",
      `=IFERROR(VLOOKUP(D${row}, XE8!B:R, 17, FALSE), "")`,
      `=IF(OR(E${row}="",F${row}=""),"",F${row}-E${row})`,
      `=IF(F${row}="","Waiting Sem 2",IF(G${row}>0,"Improved",IF(G${row}<0,"Decreased","Same")))`
    ];
  });

  sem.getRange(startRow, 1, roster.length, 8).setValues(rows);
  sem.getRange(startRow, 5, roster.length, 3).setNumberFormat("0.00");

  // Refresh the right-side summary if that helper exists in your script.
  if (typeof restoreSemComparisonRightSummary === "function") {
    restoreSemComparisonRightSummary();
  }
}

function syncASATTotalToAllSheets() {
  const ASAT_SOURCE = "https://docs.google.com/spreadsheets/d/1KJykYg14uT76iXNVmufu6MFQpFuGBbmhwZCL9-tQF2Q/edit";

  const classes = ["XE1", "XE4", "XE5", "XE6", "XE7", "XE8", "XE9", "XE10", "XE11"];
  const ss = SpreadsheetApp.getActiveSpreadsheet();

  classes.forEach(cls => {
    const main = ss.getSheetByName(cls);
    if (!main) return;

    let imported = ss.getSheetByName(`Import_ASAT_${cls}`);
    if (!imported) imported = ss.insertSheet(`Import_ASAT_${cls}`);

    imported.clear();
    imported.getRange("A1").setFormula(`=IMPORTRANGE("${ASAT_SOURCE}","${cls}!A:K")`);
    imported.hideSheet();

    const lastRow = getLastStudentRow_(main);

    for (let row = 3; row <= lastRow; row++) {
      // ASAT column in All class tabs is Q / column 17.
      // ASAT Total in ASAT workbook is J / column 10.
      main.getRange(row, 17).setFormula(
        `=IFERROR(INDEX(Import_ASAT_${cls}!$J:$J, MATCH($B${row}, Import_ASAT_${cls}!$B:$B, 0)), "")`
      );
    }
  });
}

function getLastStudentRow_(sheet) {
  const names = sheet.getRange("B3:B").getValues();

  for (let i = 0; i < names.length; i++) {
    const value = String(names[i][0] || "").trim();
    if (!value || value === ".") return i + 2;
  }

  return sheet.getLastRow();
}

// ============================================================
//  READ-ONLY WEB API FOR FINAL SCORE COMPARISON
// ============================================================

const SEM_COMPARISON_SHEET = "Sem 1 vs Sem 2";

function outputJson_(result, callback) {
  const json = JSON.stringify(result);
  if (callback) {
    return ContentService
      .createTextOutput(callback + "(" + json + ")")
      .setMimeType(ContentService.MimeType.JAVASCRIPT);
  }

  return ContentService
    .createTextOutput(json)
    .setMimeType(ContentService.MimeType.JSON);
}

function doGet(e) {
  e = e || { parameter: {} };
  const action = e.parameter.action || "";
  const callback = e.parameter.callback || "";

  try {
    if (action === "getStudents") {
      return outputJson_(getFinalScoreStudents(e.parameter.className), callback);
    }

    if (action === "getComparison") {
      return outputJson_(getFinalScoreComparison(e.parameter.className, e.parameter.studentNo), callback);
    }

    if (action === "getFinalDashboardStudents") {
      return outputJson_(getFinalDashboardStudents(e.parameter.className), callback);
    }

    if (action === "getFinalDashboard") {
      return outputJson_(getFinalDashboard(e.parameter.className, e.parameter.studentNo), callback);
    }

    return outputJson_({ success: false, error: "Unknown action" }, callback);
  } catch (err) {
    return outputJson_({ success: false, error: String(err && err.message ? err.message : err) }, callback);
  }
}

function getSemComparisonRows_() {
  const ss = SpreadsheetApp.getActiveSpreadsheet();
  const sheet = ss.getSheetByName(SEM_COMPARISON_SHEET);
  if (!sheet) throw new Error(SEM_COMPARISON_SHEET + " sheet not found");

  const lastRow = sheet.getLastRow();
  if (lastRow < 4) return [];

  return sheet.getRange(4, 1, lastRow - 3, 8).getValues()
    .filter(row => row[1] && row[3])
    .map(row => ({
      globalNo: row[0],
      className: String(row[1] || "").trim(),
      studentNo: row[2],
      name: String(row[3] || "").trim(),
      sem1: normalizeScore_(row[4]),
      sem2: normalizeScore_(row[5]),
      difference: normalizeScore_(row[6]),
      result: String(row[7] || "").trim()
    }));
}

function normalizeScore_(value) {
  if (value === "" || value === null || value === undefined) return "";
  const num = Number(value);
  return isNaN(num) ? value : Math.round(num * 100) / 100;
}

function classLabel_(className) {
  return String(className || "").replace(/^XE/, "X E-");
}

function getFinalScoreStudents(className) {
  const cls = String(className || "").trim();
  const rows = getSemComparisonRows_().filter(row => !cls || row.className === cls);

  return {
    success: true,
    className: cls,
    students: rows.map(row => ({
      no: row.studentNo,
      name: row.name
    }))
  };
}

function getFinalScoreComparison(className, studentNo) {
  const cls = String(className || "").trim();
  const no = String(studentNo || "").trim();
  if (!cls || !no) return { success: false, error: "Missing className or studentNo" };

  const allRows = getSemComparisonRows_();
  const classRows = allRows.filter(row => row.className === cls);
  const student = classRows.find(row => String(row.studentNo) === no);
  if (!student) return { success: false, error: "Student not found" };

  const completedRows = classRows.filter(row => row.sem2 !== "" && !isNaN(Number(row.sem2)));
  const sem1Rows = classRows.filter(row => row.sem1 !== "" && !isNaN(Number(row.sem1)));
  const improvedCount = classRows.filter(row => String(row.result).toLowerCase() === "improved").length;
  const decreasedCount = classRows.filter(row => String(row.result).toLowerCase() === "decreased").length;
  const sameCount = classRows.filter(row => String(row.result).toLowerCase() === "same").length;

  const sem1Average = average_(sem1Rows.map(row => row.sem1));
  const sem2Average = average_(completedRows.map(row => row.sem2));
  const rankSem1 = rankByScore_(classRows, student, "sem1");
  const rankSem2 = rankByScore_(classRows, student, "sem2");

  return {
    success: true,
    className: cls,
    classLabel: classLabel_(cls),
    student: student,
    summary: {
      classSize: classRows.length,
      completedSem2Count: completedRows.length,
      sem1Average: sem1Average,
      sem2Average: sem2Average,
      rankSem1: rankSem1,
      rankSem2: rankSem2,
      improvedCount: improvedCount,
      decreasedCount: decreasedCount,
      sameCount: sameCount
    },
    updatedAt: new Date().toISOString()
  };
}

function average_(values) {
  const nums = values.map(Number).filter(num => !isNaN(num));
  if (!nums.length) return "";
  return Math.round((nums.reduce((sum, num) => sum + num, 0) / nums.length) * 100) / 100;
}

function rankByScore_(rows, student, key) {
  const studentValue = Number(student[key]);
  if (isNaN(studentValue)) return "";

  const scores = rows
    .map(row => Number(row[key]))
    .filter(num => !isNaN(num))
    .sort((a, b) => b - a);

  return scores.findIndex(score => score === studentValue) + 1;
}

function sourceId_(className) {
  const url = SOURCES[className];
  const match = String(url || "").match(/\/d\/([^/]+)/);
  return match ? match[1] : "";
}

function classSpreadsheet_(className) {
  const id = sourceId_(className);
  if (!id) throw new Error("Class source not found: " + className);
  return SpreadsheetApp.openById(id);
}

function getSheetByGid_(spreadsheet, gid) {
  const target = Number(gid);
  const sheets = spreadsheet.getSheets();
  for (let i = 0; i < sheets.length; i++) {
    if (sheets[i].getSheetId() === target) return sheets[i];
  }
  return null;
}

function getRosterStudents_(className) {
  const sheet = classSpreadsheet_(className).getSheets()[0];
  const data = sheet.getDataRange().getValues();
  const students = [];
  for (let r = 1; r < data.length; r++) {
    const no = data[r][0];
    const name = data[r][1];
    if (!no || !name || String(name).trim() === "") break;
    students.push({ no: no, name: String(name).trim(), nickname: String(data[r][2] || "").trim() });
  }
  return students;
}

function getChapterScoresForStudent_(className, studentNo, chapter) {
  const sheet = classSpreadsheet_(className).getSheetByName("Chapter_" + chapter);
  if (!sheet) return { chapter: chapter, inputCols: [], student: null };
  const data = sheet.getDataRange().getValues();
  const headers = data[0] || [];
  const inputCols = [];
  headers.forEach((h, i) => {
    const key = String(h || "").trim();
    if (/^Task\s*\d+$/i.test(key) || key === "Formative" || key === "Summative") inputCols.push({ name: key, col: i });
  });

  let row = null;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) {
      row = data[r];
      break;
    }
  }
  if (!row) return { chapter: chapter, inputCols: inputCols.map(c => c.name), student: null };

  const scores = {};
  inputCols.forEach(c => {
    const value = row[c.col];
    scores[c.name] = (value === "" || value === null || value === undefined || value === "#DIV/0!") ? "" : value;
  });
  const avgCol = headers.findIndex(h => String(h).trim() === "Average");
  const finalCol = headers.findIndex(h => String(h).trim() === "Final Score");
  const indCol = headers.findIndex(h => String(h).trim().toLowerCase().startsWith("ind"));
  return {
    chapter: chapter,
    inputCols: inputCols.map(c => c.name),
    student: {
      no: row[0],
      name: String(row[1] || "").trim(),
      nickname: String(row[2] || "").trim(),
      scores: scores,
      average: avgCol >= 0 ? normalizeScore_(row[avgCol]) : "",
      finalScore: finalCol >= 0 ? normalizeScore_(row[finalCol]) : "",
      indicator: indCol >= 0 ? String(row[indCol] || "").trim() : ""
    }
  };
}

function getOverallScoresForStudent_(className, studentNo) {
  const sheet = classSpreadsheet_(className).getSheetByName("Scores");
  if (!sheet) return null;
  const data = sheet.getDataRange().getValues();
  const headers = data[0] || [];
  let row = null;
  for (let r = 1; r < data.length; r++) {
    if (String(data[r][0]) === String(studentNo)) {
      row = data[r];
      break;
    }
  }
  if (!row) return null;

  const inputCols = ["Chapter 4", "Chapter 5", "ASAT"];
  const calcCols = ["Average", "Final Score", "Indicator"];
  const scores = {};
  const calc = {};
  inputCols.forEach(name => {
    const idx = headers.findIndex(h => String(h).trim() === name);
    const value = idx >= 0 ? row[idx] : "";
    scores[name] = (value === "" || value === null || value === undefined || value === "#DIV/0!" || value === "-") ? "" : value;
  });
  calcCols.forEach(name => {
    const idx = headers.findIndex(h => String(h).trim() === name);
    const value = idx >= 0 ? row[idx] : "";
    calc[name] = (value === "" || value === null || value === undefined || value === "#DIV/0!") ? "" : value;
  });
  return {
    inputCols: inputCols,
    calcCols: calcCols,
    student: {
      no: row[0],
      name: String(row[1] || "").trim(),
      nickname: String(row[2] || "").trim(),
      scores: scores,
      calc: calc
    }
  };
}

function getFinalDashboardRows_() {
  const sheet = getSheetByGid_(SpreadsheetApp.getActiveSpreadsheet(), FINAL_DASHBOARD_SHEET_GID);
  if (!sheet) throw new Error("Final dashboard sheet not found");
  const data = sheet.getDataRange().getValues();
  const rows = [];
  for (let r = 1; r < data.length; r++) {
    for (let c = 0; c + 7 < data[r].length; c += 9) {
      const className = String(data[r][c + 1] || "").trim();
      const studentNo = data[r][c + 2];
      const name = String(data[r][c + 3] || "").trim();
      if (!className || !studentNo || !name || className === "Class" || name === "Name") continue;
      rows.push({
        globalNo: data[r][c],
        className: className,
        studentNo: studentNo,
        name: name,
        sem1: normalizeScore_(data[r][c + 4]),
        sem2: normalizeScore_(data[r][c + 5]),
        difference: normalizeScore_(data[r][c + 6]),
        result: String(data[r][c + 7] || "").trim()
      });
    }
  }
  return rows;
}

function remedialForAsat_(asatScore) {
  if (asatScore === "" || asatScore === null || asatScore === undefined || asatScore === "-") {
    return { type: "pending", label: "Waiting for ASAT", task: "Your ASAT score is not available yet.", gap: "", link: "", linkLabel: "No group yet" };
  }
  const score = Number(asatScore);
  if (isNaN(score)) return { type: "pending", label: "Waiting for ASAT", task: "Your ASAT score is not available yet.", gap: "", link: "", linkLabel: "No group yet" };
  if (score >= 75) {
    return { type: "enrichment", label: "Enrichment", task: "No remedial needed. You may join enrichment to improve your ASAT score by 1-10 points.", gap: 0, link: REMEDIAL_LINKS.enrichment, linkLabel: "Join Enrichment Group" };
  }
  const gap = Math.ceil((75 - score) * 100) / 100;
  if (gap <= 15) return { type: "gap1", label: "Gap 1-15", task: "Read a paragraph offline and meet the teacher.", gap: gap, link: REMEDIAL_LINKS.gap1to15, linkLabel: "Join Gap 1-15 Group" };
  if (gap <= 25) return { type: "gap2", label: "Gap 16-25", task: "Make a poster summary of Semester 2 material.", gap: gap, link: REMEDIAL_LINKS.gap16to25, linkLabel: "Join Gap 16-25 Group" };
  return { type: "gap3", label: "Gap 26+", task: "Write a complete written summary of Semester 2 material.", gap: gap, link: REMEDIAL_LINKS.gap26plus, linkLabel: "Join Gap 26+ Group" };
}

function getFinalDashboardStudents(className) {
  const cls = String(className || "").trim();
  const rows = getFinalDashboardRows_().filter(row => !cls || row.className === cls);
  const roster = cls ? getRosterStudents_(cls) : [];
  if (roster.length) {
    const rowsByNo = {};
    rows.forEach(row => rowsByNo[String(row.studentNo)] = row);
    return {
      success: true,
      className: cls,
      students: roster.filter(student => !!rowsByNo[String(student.no)]).map(student => ({ no: student.no, name: student.name }))
    };
  }
  return { success: true, className: cls, students: rows.map(row => ({ no: row.studentNo, name: row.name })) };
}

function getFinalDashboard(className, studentNo) {
  const cls = String(className || "").trim();
  const no = String(studentNo || "").trim();
  if (!cls || !no) return { success: false, error: "Missing className or studentNo" };

  const classRows = getFinalDashboardRows_().filter(row => row.className === cls);
  const row = classRows.find(item => String(item.studentNo) === no);
  if (!row) return { success: false, error: "Student not found" };

  const rosterStudent = getRosterStudents_(cls).find(student => String(student.no) === no);
  const student = Object.assign({}, row);
  if (rosterStudent) {
    student.studentNo = rosterStudent.no;
    student.name = rosterStudent.name;
  }

  const sem1Rows = classRows.filter(item => item.sem1 !== "" && !isNaN(Number(item.sem1)));
  const sem2Rows = classRows.filter(item => item.sem2 !== "" && !isNaN(Number(item.sem2)));
  const overall = getOverallScoresForStudent_(cls, no);
  const asatScore = overall && overall.student && overall.student.scores ? overall.student.scores.ASAT : "";
  const asatNum = (asatScore === "" || asatScore === null || asatScore === undefined || asatScore === "-") ? NaN : Number(asatScore);

  return {
    success: true,
    className: cls,
    classLabel: classLabel_(cls),
    student: student,
    chapters: [
      getChapterScoresForStudent_(cls, no, 4),
      getChapterScoresForStudent_(cls, no, 5)
    ],
    overall: overall,
    asat: {
      score: asatScore,
      gap: isNaN(asatNum) ? "" : Math.max(0, Math.ceil((75 - asatNum) * 100) / 100)
    },
    remedial: remedialForAsat_(asatScore),
    summary: {
      classSize: classRows.length,
      completedSem2Count: sem2Rows.length,
      sem1Average: average_(sem1Rows.map(item => item.sem1)),
      sem2Average: average_(sem2Rows.map(item => item.sem2)),
      rankSem1: rankByScore_(classRows, student, "sem1"),
      rankSem2: rankByScore_(classRows, student, "sem2")
    },
    updatedAt: new Date().toISOString()
  };
}

function syncAllAverageBackToClassSheets() {
  const ALL_URL = "https://docs.google.com/spreadsheets/d/1UjlcC2zOy5usrkzwL1gqgt3BDfNFb-heak-jdeIxIcM/edit";

  const classFiles = {
    XE1: "1X3PBpMCEvglTA92HST8286dY_OWe5lw7tdJHPpNjggk",
    XE4: "16CT_wxAGV0mLRhvlIFpXRzKobdnpnj2-DcAJSiz14JY",
    XE5: "1-GMJ8amrcE-r_N1qJL8rsrxEItRraN-97FgyySJgOxg",
    XE6: "1MxqzK2JOwMyc2yfUSwCgIW4siWCsJe2R_o_cF11mOHU",
    XE7: "1D9iYhBLNPhFsgTZ15vQOHFn22RYCI6HFW10aC4HwJK4",
    XE8: "1skj7KBc11zaFa07FGu0r4bEHvDrV-oHZm4qKhBLimpg",
    XE9: "19Pn0JyIZRAgzhUGs_W519vjmwcnivair6hsqkIVyOnE",
    XE10: "1UEgm1spqzNGPbYl9a9CmLavwALVLG_6o2gEvN3R_LKM",
    XE11: "1yt6km9wnBYRe61bHaSjDfJWkmqz884v1cogp836Ioa0"
  };

  Object.keys(classFiles).forEach(cls => {
    const classSS = SpreadsheetApp.openById(classFiles[cls]);
    const scores = classSS.getSheetByName("Scores");
    if (!scores) throw new Error(`${cls}: Scores sheet not found`);

    let helper = classSS.getSheetByName(`Import_All_${cls}`);
    if (!helper) helper = classSS.insertSheet(`Import_All_${cls}`);

    helper.clear();
    helper.getRange("A1").setFormula(`=IMPORTRANGE("${ALL_URL}","${cls}!A:R")`);
    helper.hideSheet();

    const lastRow = getLastStudentRowForScores_(scores);

    for (let row = 2; row <= lastRow; row++) {
      // Average is G in class Scores sheet.
      // True Average is R in All sheet class tab.
      scores.getRange(row, 7).setFormula(
        `=IFERROR(INDEX(Import_All_${cls}!$R:$R, MATCH($B${row}, Import_All_${cls}!$B:$B, 0)), "")`
      );
    }
  });
}

function getLastStudentRowForScores_(sheet) {
  const names = sheet.getRange("B2:B").getValues();

  for (let i = 0; i < names.length; i++) {
    const value = String(names[i][0] || "").trim();
    if (!value || value === ".") return i + 1;
  }

  return sheet.getLastRow();
}

function syncASATGapRemedialToAllSheets() {
  const ASAT_SOURCE = "https://docs.google.com/spreadsheets/d/1KJykYg14uT76iXNVmufu6MFQpFuGBbmhwZCL9-tQF2Q/edit";

  const classes = ["XE1", "XE4", "XE5", "XE6", "XE7", "XE8", "XE9", "XE10", "XE11"];

  const ss = SpreadsheetApp.getActiveSpreadsheet();

  classes.forEach(cls => {
    const main = ss.getSheetByName(cls);
    if (!main) return;

    let helper = ss.getSheetByName(`Import_ASAT_${cls}`);
    if (!helper) helper = ss.insertSheet(`Import_ASAT_${cls}`);

    helper.clear();
    helper.getRange("A1").setFormula(`=IMPORTRANGE("${ASAT_SOURCE}","${cls}!A:L")`);
    helper.hideSheet();

    const lastRow = getLastStudentRow_(main);
    if (lastRow < 3) return;

    // Headers in ALL sheet
    main.getRange("S2").setValue("Gap");
    main.getRange("T2").setValue("Remedial");

    for (let row = 3; row <= lastRow; row++) {
      // Gap from ASAT sheet column K
      main.getRange(row, 19).setFormula(
        `=IFERROR(INDEX(Import_ASAT_${cls}!$K:$K, MATCH($B${row}, Import_ASAT_${cls}!$B:$B, 0)), "")`
      );

      // Remedial from ASAT sheet column L
      main.getRange(row, 20).setFormula(
        `=IFERROR(INDEX(Import_ASAT_${cls}!$L:$L, MATCH($B${row}, Import_ASAT_${cls}!$B:$B, 0)), "")`
      );
    }
  });
}

function getLastStudentRow_(sheet) {
  const names = sheet.getRange("B3:B").getValues();

  for (let i = 0; i < names.length; i++) {
    const value = String(names[i][0] || "").trim();
    if (!value || value === ".") return i + 2;
  }

  return sheet.getLastRow();
}

function syncASATGapRemedialAndLackingToAllSheets() {
  const ASAT_SOURCE = "https://docs.google.com/spreadsheets/d/1KJykYg14uT76iXNVmufu6MFQpFuGBbmhwZCL9-tQF2Q/edit";

  const classes = ["XE1", "XE4", "XE5", "XE6", "XE7", "XE8", "XE9", "XE10", "XE11"];
  const ss = SpreadsheetApp.getActiveSpreadsheet();

  classes.forEach(cls => {
    const main = ss.getSheetByName(cls);
    if (!main) return;

    let helper = ss.getSheetByName(`Import_ASAT_${cls}`);
    if (!helper) helper = ss.insertSheet(`Import_ASAT_${cls}`);

    helper.clear();
    helper.getRange("A1").setFormula(`=IMPORTRANGE("${ASAT_SOURCE}","${cls}!A:L")`);
    helper.hideSheet();

    const lastRow = getLastStudentRow_(main);
    if (lastRow < 3) return;

    main.getRange("R2").setValue("Final Score Sem 2");
    main.getRange("S2").setValue("ASAT Gap");
    main.getRange("T2").setValue("Remedial");
    main.getRange("U2").setValue("Lacking");

    for (let row = 3; row <= lastRow; row++) {
      main.getRange(row, 19).setFormula(
        `=IFERROR(INDEX(Import_ASAT_${cls}!$K:$K, MATCH($B${row}, Import_ASAT_${cls}!$B:$B, 0)), "")`
      );

      main.getRange(row, 20).setFormula(
        `=IFERROR(INDEX(Import_ASAT_${cls}!$L:$L, MATCH($B${row}, Import_ASAT_${cls}!$B:$B, 0)), "")`
      );

      main.getRange(row, 21).setFormula(
        `=IFERROR(TEXTJOIN(", ", TRUE, FILTER($C$2:$Q$2, ($C${row}:$Q${row}="")+($C${row}:$Q${row}=0))), "Complete")`
      );
    }
  });
}

function getLastStudentRow_(sheet) {
  const names = sheet.getRange("B3:B").getValues();

  for (let i = 0; i < names.length; i++) {
    const value = String(names[i][0] || "").trim();
    if (!value || value === ".") return i + 2;
  }

  return sheet.getLastRow();
}
