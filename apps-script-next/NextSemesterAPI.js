// ============================================================
//  ENGLISH QUEST NEXT SEMESTER API
//  Separate Apps Script for the next-semester master spreadsheet.
//  Current-year ScoresAPI.js must remain independent.
// ============================================================

const NEXT_SEMESTER_SHEET_ID = '1Z5RlW3GfsUR3sbBBP4gY8BrjFxr6JbHFbDOTcxa0rfo';

const SHEETS = {
  SETTINGS: 'Settings',
  CLASSES: 'Classes',
  STUDENTS: 'Students',
  PROFILES: 'Profiles',
  FORM_RESPONSES: 'Form responses 1',
  SCORES: 'Scores',
  TASKS: 'Tasks',
  ATTENDANCE: 'Attendance',
  SUBMISSIONS: 'Submissions',
  XP_LOG: 'XP_Log',
  XP_RULES: 'XP_Rules',
  RANKS: 'Ranks',
  STRIKES: 'Strikes',
  PROFILE_FORM_QUESTIONS: 'Profile_Form_Questions'
};

function doGet(e) {
  const params = (e && e.parameter) || {};
  const action = params.action || 'ping';
  const callback = params.callback || '';
  let result;

  try {
    if (action === 'ping') result = { ok: true, system: 'next-semester' };
    else if (action === 'healthCheck') result = healthCheck();
    else if (action === 'getSettings') result = getSettings();
    else if (action === 'getDataReadiness') result = getDataReadiness();
    else if (action === 'getProfileFormSpec') result = getProfileFormSpec(isTruthy(params.includeInactive));
    else if (action === 'getApprovedProfileResponses') result = getApprovedProfileResponses();
    else if (action === 'getTeacherControlSummary') result = getTeacherControlSummary();
    else if (action === 'getActiveClasses') result = getActiveClasses(isTruthy(params.includeInactive));
    else if (action === 'getStudentsByClass') result = getStudentsByClass(params.classId || params.class_id, isTruthy(params.includeInactive));
    else if (action === 'getStudentProfile') result = getStudentProfile(params.classId || params.class_id, params.studentNo || params.student_no);
    else if (action === 'getXpSummary') result = getXpSummary(params.classId || params.class_id, params.studentNo || params.student_no);
    else if (action === 'getRankings') result = getRankings(params.classId || params.class_id, Number(params.limit || 0));
    else if (action === 'getStudentDashboard') result = getStudentDashboard(params.classId || params.class_id, params.studentNo || params.student_no);
    else result = { error: 'Unknown action: ' + action };
  } catch (err) {
    result = { error: err.message };
  }

  const json = JSON.stringify(result);
  if (callback) {
    return ContentService
      .createTextOutput(callback + '(' + json + ')')
      .setMimeType(ContentService.MimeType.JAVASCRIPT);
  }
  return ContentService
    .createTextOutput(json)
    .setMimeType(ContentService.MimeType.JSON);
}

function healthCheck() {
  const ss = getSpreadsheet();
  const required = Object.keys(SHEETS).map(function(key) { return SHEETS[key]; });
  const found = ss.getSheets().map(function(sheet) { return sheet.getName(); });
  const missing = required.filter(function(name) { return found.indexOf(name) === -1; });
  return {
    ok: missing.length === 0,
    spreadsheetId: NEXT_SEMESTER_SHEET_ID,
    spreadsheetName: ss.getName(),
    foundSheets: found,
    missingSheets: missing
  };
}

function getSettings() {
  const rows = readRecords(SHEETS.SETTINGS);
  const settings = {};
  rows.forEach(function(row) {
    if (!row.key) return;
    settings[String(row.key)] = row.value;
  });
  return { settings: settings, rows: rows };
}

function getDataReadiness() {
  const classes = readRecords(SHEETS.CLASSES)
    .filter(function(row) { return row.class_id; })
    .map(function(row) {
      return {
        class_id: normalizeClassId(row.class_id),
        grade: normalizeNumber(row.grade),
        display_name: String(row.display_name || row.class_id).trim(),
        active: isTruthy(row.active),
        student_capacity: normalizeNumber(row.student_capacity),
        notes: String(row.notes || '').trim()
      };
    });
  const students = readRecords(SHEETS.STUDENTS)
    .filter(function(row) { return row.class_id || row.student_no || row.full_name; })
    .map(normalizeStudent);
  const classIds = {};
  const activeClassIds = {};
  const issues = [];
  const byClass = {};
  const seenStudentSlots = {};

  classes.forEach(function(cls) {
    classIds[cls.class_id] = true;
    if (cls.active) activeClassIds[cls.class_id] = true;
    byClass[cls.class_id] = {
      class_id: cls.class_id,
      display_name: cls.display_name,
      grade: cls.grade,
      active: cls.active,
      student_capacity: cls.student_capacity,
      total_students: 0,
      active_students: 0,
      placeholder_students: 0,
      first_placeholder: ''
    };
  });

  students.forEach(function(student) {
    const classId = student.class_id;
    const key = classId + '#' + student.student_no;
    const classSummary = byClass[classId] || {
      class_id: classId,
      display_name: classId,
      grade: 0,
      active: false,
      student_capacity: 0,
      total_students: 0,
      active_students: 0,
      placeholder_students: 0,
      first_placeholder: ''
    };

    if (!classIds[classId]) {
      issues.push({
        severity: 'error',
        code: 'unknown_class',
        message: 'Student row points to a class that is not listed in Classes.',
        class_id: classId,
        student_no: student.student_no
      });
    }

    if (seenStudentSlots[key]) {
      issues.push({
        severity: 'error',
        code: 'duplicate_student_slot',
        message: 'Two student rows use the same class_id and student_no.',
        class_id: classId,
        student_no: student.student_no
      });
    }
    seenStudentSlots[key] = true;

    classSummary.total_students += 1;
    if (student.active) classSummary.active_students += 1;
    if (isPlaceholderStudent(student.full_name)) {
      classSummary.placeholder_students += 1;
      if (!classSummary.first_placeholder) classSummary.first_placeholder = student.full_name;
    }
    byClass[classId] = classSummary;
  });

  Object.keys(byClass).forEach(function(classId) {
    const summary = byClass[classId];
    if (summary.active && summary.placeholder_students > 0) {
      issues.push({
        severity: 'warning',
        code: 'active_class_has_placeholders',
        message: 'Active class still has placeholder student names.',
        class_id: classId,
        placeholder_students: summary.placeholder_students,
        first_placeholder: summary.first_placeholder
      });
    }
    if (summary.student_capacity && summary.total_students > summary.student_capacity) {
      issues.push({
        severity: 'warning',
        code: 'over_capacity',
        message: 'Class has more student rows than its configured capacity.',
        class_id: classId,
        student_capacity: summary.student_capacity,
        total_students: summary.total_students
      });
    }
    if (summary.active && summary.active_students === 0) {
      issues.push({
        severity: 'warning',
        code: 'active_class_no_active_students',
        message: 'Class is active but has no active students.',
        class_id: classId
      });
    }
  });

  const classSummaries = Object.keys(byClass).sort().map(function(classId) {
    return byClass[classId];
  });
  const activeClasses = classSummaries.filter(function(item) { return item.active; });
  const placeholderTotal = classSummaries.reduce(function(sum, item) {
    return sum + item.placeholder_students;
  }, 0);
  const errors = issues.filter(function(issue) { return issue.severity === 'error'; }).length;
  const warnings = issues.filter(function(issue) { return issue.severity === 'warning'; }).length;

  return {
    ok: errors === 0,
    ready_for_real_students: activeClasses.length > 0 && errors === 0,
    ready_for_frontend_migration: activeClasses.length > 0 && errors === 0 && warnings === 0,
    summary: {
      total_classes: classes.length,
      active_classes: activeClasses.length,
      inactive_classes: classes.length - activeClasses.length,
      total_students: students.length,
      active_students: students.filter(function(student) { return student.active; }).length,
      placeholder_students: placeholderTotal,
      errors: errors,
      warnings: warnings
    },
    classes: classSummaries,
    issues: issues
  };
}

function getProfileFormSpec(includeInactive) {
  const questions = readRecords(SHEETS.PROFILE_FORM_QUESTIONS)
    .filter(function(row) {
      return row.question || row.field_key;
    })
    .map(function(row) {
      return {
        question: String(row.question || '').trim(),
        field_key: normalizeKey(row.field_key),
        required: isTruthy(row.required),
        answer_type: String(row.answer_type || '').trim(),
        notes: String(row.notes || '').trim()
      };
    });
  const requiredProfileHeaders = readRecords(SHEETS.PROFILE_FORM_QUESTIONS)
    .filter(function(row) { return isTruthy(row.required); })
    .map(function(row) { return normalizeKey(row.field_key); });
  const classes = getActiveClasses(includeInactive).classes;

  return {
    questions: questions,
    required_profile_headers: ['timestamp'].concat(requiredProfileHeaders).concat(['approved']),
    classes: classes,
    active_classes: classes,
    profile_sheet_headers: [
      'timestamp',
      'class_id',
      'student_no',
      'full_name',
      'preferred_name',
      'photo_url',
      'learning_goal',
      'english_strength',
      'english_weakness',
      'favorite_activity',
      'quote',
      'approved'
    ],
    rules: {
      destination_tab: SHEETS.PROFILES,
      raw_response_tab: SHEETS.FORM_RESPONSES,
      join_key: 'class_id + student_no',
      approval_field: 'approved',
      approved_values: ['TRUE', 'FALSE', ''],
      read_behavior: 'Student dashboard reads approved rows from Profiles and TRUE-approved rows from Form responses 1.'
    }
  };
}

function getApprovedProfileResponses() {
  return {
    response_tab: SHEETS.FORM_RESPONSES,
    profiles: readApprovedFormProfiles()
  };
}

function getTeacherControlSummary() {
  const readiness = getDataReadiness();
  const profileForm = getProfileFormSpec(true);
  const settings = getSettings().settings;
  const xpRules = readRecords(SHEETS.XP_RULES)
    .filter(function(row) { return row.source; })
    .map(function(row) {
      return {
        source: String(row.source || '').trim(),
        default_xp: normalizeNumber(row.default_xp),
        category: String(row.category || '').trim(),
        active: isTruthy(row.active),
        notes: String(row.notes || '').trim()
      };
    });
  const ranks = readRecords(SHEETS.RANKS)
    .filter(function(row) { return row.level !== '' && row.level !== undefined; })
    .map(function(row) {
      return {
        level: normalizeNumber(row.level),
        total_xp_min: normalizeNumber(row.total_xp_min),
        xp_to_next: normalizeNumber(row.xp_to_next),
        next_level_total: normalizeNumber(row.next_level_total),
        rank_name: String(row.rank_name || '').trim()
      };
    });

  return {
    ok: true,
    mode: 'read-only-preview',
    settings: settings,
    readiness: readiness,
    profile_form: {
      question_count: profileForm.questions.length,
      required_count: profileForm.questions.filter(function(question) { return question.required; }).length,
      questions: profileForm.questions,
      rules: profileForm.rules
    },
    xp: {
      rule_count: xpRules.length,
      active_rule_count: xpRules.filter(function(rule) { return rule.active; }).length,
      rank_count: ranks.length,
      rules: xpRules,
      ranks: ranks
    },
    records: {
      scores: readRecords(SHEETS.SCORES).length,
      tasks: readRecords(SHEETS.TASKS).length,
      attendance: readRecords(SHEETS.ATTENDANCE).length,
      submissions: readRecords(SHEETS.SUBMISSIONS).length,
      xp_logs: readRecords(SHEETS.XP_LOG).length,
      strikes: readRecords(SHEETS.STRIKES).length,
      profiles: readRecords(SHEETS.PROFILES).length,
      form_responses: readOptionalRecords(SHEETS.FORM_RESPONSES).length,
      approved_form_profiles: readApprovedFormProfiles().length
    },
    controls: [
      { id: 'classes', label: 'Active Classes', status: readiness.summary.active_classes ? 'Needs review' : 'Waiting', note: 'Set Classes.active to TRUE only for classes you will teach.' },
      { id: 'profiles', label: 'Profile Approval', status: 'Queued', note: 'Write controls will approve or hide rows in Profiles.approved.' },
      { id: 'xp', label: 'XP and Rank', status: xpRules.length ? 'Rules loaded' : 'Waiting', note: 'XP write controls will add rows to XP_Log.' },
      { id: 'strikes', label: 'Strikes', status: 'Queued', note: 'Strike write controls will append rows to Strikes.' },
      { id: 'attendance', label: 'Attendance', status: 'Queued', note: 'Attendance controls will write meeting records.' },
      { id: 'scores', label: 'Scores', status: 'Queued', note: 'Score controls will write rows to Scores.' },
      { id: 'tasks', label: 'Tasks', status: 'Queued', note: 'Task controls will publish rows from Tasks.' },
      { id: 'submissions', label: 'Submissions', status: 'Queued', note: 'Submission review will update Submissions.status and teacher notes.' }
    ]
  };
}

function getActiveClasses(includeInactive) {
  const classes = readRecords(SHEETS.CLASSES)
    .filter(function(row) {
      return row.class_id && (includeInactive || isTruthy(row.active));
    })
    .map(function(row) {
      return {
        class_id: String(row.class_id).trim(),
        grade: normalizeNumber(row.grade),
        display_name: String(row.display_name || row.class_id).trim(),
        active: isTruthy(row.active),
        student_capacity: normalizeNumber(row.student_capacity),
        notes: String(row.notes || '').trim()
      };
    });
  return { classes: classes };
}

function getStudentsByClass(classId, includeInactive) {
  classId = normalizeClassId(classId);
  if (!classId) return { error: 'Missing classId' };

  const students = readRecords(SHEETS.STUDENTS)
    .filter(function(row) {
      return normalizeClassId(row.class_id) === classId && (includeInactive || isTruthy(row.active));
    })
    .map(function(row) {
      return normalizeStudent(row);
    })
    .sort(function(a, b) {
      return Number(a.student_no) - Number(b.student_no);
    });

  return { class_id: classId, students: students };
}

function getStudentProfile(classId, studentNo) {
  const student = findStudent(classId, studentNo);
  if (!student) return { error: 'Student not found' };

  const profile = findLatestProfile(student.class_id, student.student_no);
  return {
    class_id: student.class_id,
    student_no: student.student_no,
    student: student,
    profile: profile
  };
}

function getXpSummary(classId, studentNo) {
  const student = findStudent(classId, studentNo);
  if (!student) return { error: 'Student not found' };
  return buildXpSummary(student);
}

function getRankings(classId, limit) {
  const normalizedClass = normalizeClassId(classId);
  const students = readRecords(SHEETS.STUDENTS)
    .filter(function(row) {
      return row.class_id && isTruthy(row.active) &&
        (!normalizedClass || normalizeClassId(row.class_id) === normalizedClass);
    })
    .map(normalizeStudent);

  const summaries = students.map(function(student) {
    const xp = buildXpSummary(student);
    return {
      class_id: student.class_id,
      student_no: student.student_no,
      full_name: student.full_name,
      nickname: student.nickname,
      total_xp: xp.total_xp,
      level: xp.level,
      rank_name: xp.rank_name
    };
  }).sort(function(a, b) {
    if (b.total_xp !== a.total_xp) return b.total_xp - a.total_xp;
    if (a.class_id !== b.class_id) return a.class_id.localeCompare(b.class_id);
    return Number(a.student_no) - Number(b.student_no);
  });

  return {
    class_id: normalizedClass || '',
    rankings: limit > 0 ? summaries.slice(0, limit) : summaries
  };
}

function getStudentDashboard(classId, studentNo) {
  const student = findStudent(classId, studentNo);
  if (!student) return { error: 'Student not found' };

  const profile = findLatestProfile(student.class_id, student.student_no);
  const xp = buildXpSummary(student);
  const scores = findStudentRows(SHEETS.SCORES, student);
  const tasks = findStudentTasks(student);
  const attendance = buildAttendanceSummary(student);
  const submissions = findStudentRows(SHEETS.SUBMISSIONS, student);
  const strikes = buildStrikeSummary(student);

  return {
    class_id: student.class_id,
    student_no: student.student_no,
    student: student,
    profile: profile,
    xp: xp,
    scores: scores,
    tasks: tasks,
    attendance: attendance,
    submissions: submissions,
    strikes: strikes
  };
}

function getSpreadsheet() {
  return SpreadsheetApp.openById(NEXT_SEMESTER_SHEET_ID);
}

function getSheet(name) {
  const sheet = getSpreadsheet().getSheetByName(name);
  if (!sheet) throw new Error('Missing sheet: ' + name);
  return sheet;
}

function readRecords(sheetName) {
  const sheet = getSheet(sheetName);
  const values = sheet.getDataRange().getValues();
  if (values.length < 2) return [];

  const headers = values[0].map(function(header) {
    return normalizeKey(header);
  });

  const records = [];
  for (let r = 1; r < values.length; r++) {
    const row = {};
    let hasValue = false;
    for (let c = 0; c < headers.length; c++) {
      if (!headers[c]) continue;
      const value = values[r][c];
      if (value !== '' && value !== null && value !== undefined) hasValue = true;
      row[headers[c]] = normalizeCell(value);
    }
    if (hasValue) records.push(row);
  }
  return records;
}

function readOptionalRecords(sheetName) {
  const sheet = getSpreadsheet().getSheetByName(sheetName);
  if (!sheet) return [];
  return readRecords(sheetName);
}

function normalizeKey(value) {
  return String(value || '').trim().toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '');
}

function normalizeCell(value) {
  if (value instanceof Date) return Utilities.formatDate(value, Session.getScriptTimeZone(), 'yyyy-MM-dd HH:mm:ss');
  return value;
}

function normalizeClassId(value) {
  return String(value || '').trim().toUpperCase().replace(/[^A-Z0-9]/g, '');
}

function normalizeNumber(value) {
  const num = Number(value);
  return isNaN(num) ? 0 : num;
}

function isTruthy(value) {
  return value === true || String(value || '').trim().toUpperCase() === 'TRUE' || String(value || '').trim() === '1';
}

function sameStudent(row, student) {
  return normalizeClassId(row.class_id) === student.class_id &&
    String(row.student_no || '').trim() === String(student.student_no);
}

function normalizeStudent(row) {
  return {
    class_id: normalizeClassId(row.class_id),
    student_no: normalizeNumber(row.student_no),
    full_name: String(row.full_name || '').trim(),
    nickname: String(row.nickname || '').trim(),
    active: isTruthy(row.active),
    notes: String(row.notes || '').trim()
  };
}

function normalizeProfileResponse(row) {
  return {
    timestamp: row.timestamp || '',
    class_id: normalizeClassId(row.class || row.class_id),
    student_no: normalizeNumber(row.student_number || row.student_no),
    full_name: String(row.full_name || '').trim(),
    preferred_name: String(row.preferred_name || '').trim(),
    photo_url: String(row.profile_photo || row.photo_url || '').trim(),
    learning_goal: String(row.my_learning_goal || row.learning_goal || '').trim(),
    english_strength: String(row.my_english_strength || row.english_strength || '').trim(),
    english_weakness: String(row.my_english_weakness || row.english_weakness || '').trim(),
    favorite_activity: String(row.favorite_english_class_activity || row.favorite_activity || '').trim(),
    quote: String(row.profile_quote || row.quote || '').trim(),
    approved: row.approved
  };
}

function isPlaceholderStudent(name) {
  return /^student\s+\d+$/i.test(String(name || '').trim());
}

function findStudent(classId, studentNo) {
  const normalizedClass = normalizeClassId(classId);
  const normalizedNo = String(studentNo || '').trim();
  if (!normalizedClass || !normalizedNo) return null;

  const row = readRecords(SHEETS.STUDENTS).find(function(record) {
    return normalizeClassId(record.class_id) === normalizedClass &&
      String(record.student_no || '').trim() === normalizedNo &&
      isTruthy(record.active);
  });

  return row ? normalizeStudent(row) : null;
}

function findLatestProfile(classId, studentNo) {
  const manualProfiles = readRecords(SHEETS.PROFILES)
    .filter(function(row) {
      return normalizeClassId(row.class_id) === classId &&
        String(row.student_no || '').trim() === String(studentNo) &&
        (row.approved === '' || isTruthy(row.approved));
    });
  const formProfiles = readApprovedFormProfiles()
    .filter(function(row) {
      return normalizeClassId(row.class_id) === classId &&
        String(row.student_no || '').trim() === String(studentNo);
    });
  const profiles = manualProfiles.concat(formProfiles);
  if (!profiles.length) return null;
  return profiles[profiles.length - 1];
}

function readApprovedFormProfiles() {
  return readOptionalRecords(SHEETS.FORM_RESPONSES)
    .filter(function(row) {
      return isTruthy(row.approved);
    })
    .map(normalizeProfileResponse)
    .filter(function(row) {
      return row.class_id && row.student_no && row.full_name;
    });
}

function findStudentRows(sheetName, student) {
  return readRecords(sheetName).filter(function(row) {
    return sameStudent(row, student);
  });
}

function buildXpSummary(student) {
  const logs = findStudentRows(SHEETS.XP_LOG, student)
    .filter(function(row) {
      return !isTruthy(row.reversed);
    });
  const total = logs.reduce(function(sum, row) {
    return sum + normalizeNumber(row.xp_change);
  }, 0);
  const rank = findRank(total);
  return {
    class_id: student.class_id,
    student_no: student.student_no,
    total_xp: total,
    level: rank.level,
    rank_name: rank.rank_name,
    current_level_min_xp: rank.total_xp_min,
    next_level_total_xp: rank.next_level_total,
    xp_to_next: Math.max(0, rank.next_level_total - total),
    progress_xp: Math.max(0, total - rank.total_xp_min),
    level_span_xp: Math.max(1, rank.next_level_total - rank.total_xp_min),
    log_count: logs.length,
    recent: logs.slice(-5).reverse()
  };
}

function findRank(totalXp) {
  const ranks = readRecords(SHEETS.RANKS)
    .map(function(row) {
      return {
        level: normalizeNumber(row.level),
        total_xp_min: normalizeNumber(row.total_xp_min),
        xp_to_next: normalizeNumber(row.xp_to_next),
        next_level_total: normalizeNumber(row.next_level_total),
        rank_name: String(row.rank_name || '').trim()
      };
    })
    .sort(function(a, b) {
      return a.total_xp_min - b.total_xp_min;
    });

  let current = ranks[0] || { level: 0, total_xp_min: 0, xp_to_next: 10, next_level_total: 10, rank_name: 'Level 0' };
  ranks.forEach(function(rank) {
    if (totalXp >= rank.total_xp_min) current = rank;
  });
  return current;
}

function findStudentTasks(student) {
  const submissions = findStudentRows(SHEETS.SUBMISSIONS, student);
  const submittedTaskIds = {};
  submissions.forEach(function(row) {
    if (row.task_id) submittedTaskIds[String(row.task_id)] = row;
  });

  return readRecords(SHEETS.TASKS)
    .filter(function(row) {
      return normalizeClassId(row.class_id) === student.class_id && isTruthy(row.published);
    })
    .map(function(task) {
      const submission = submittedTaskIds[String(task.task_id)] || null;
      return {
        task: task,
        submission: submission,
        status: submission ? String(submission.status || 'Received') : 'Missing'
      };
    });
}

function buildAttendanceSummary(student) {
  const rows = findStudentRows(SHEETS.ATTENDANCE, student);
  const presentLike = rows.filter(function(row) {
    const status = String(row.status || '').trim();
    return status === '' || status === 'Present' || status === 'Dispen';
  }).length;
  return {
    records: rows,
    total_records: rows.length,
    present_like: presentLike,
    percentage: rows.length ? Math.round((presentLike / rows.length) * 100) : 0
  };
}

function buildStrikeSummary(student) {
  const rows = findStudentRows(SHEETS.STRIKES, student)
    .filter(function(row) {
      return row.active === '' || isTruthy(row.active);
    });
  const count = rows.reduce(function(max, row) {
    return Math.max(max, normalizeNumber(row.strike_count));
  }, rows.length);
  return {
    active_strikes: count,
    can_join_summative: count < 1,
    can_join_class: count < 3,
    records: rows
  };
}
