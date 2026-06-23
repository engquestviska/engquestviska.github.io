/* Shared student identity and display helpers. */
(function(global) {
  var PROFILE_KEYS = {
    grade: 'eq_grade',
    classId: 'eq_student_class',
    studentNo: 'eq_student_no',
    studentName: 'eq_student_name',
    setupSkipped: 'eq_setup_skipped'
  };

  function read(expectedGrade) {
    var grade = localStorage.getItem(PROFILE_KEYS.grade) || '';
    var classId = localStorage.getItem(PROFILE_KEYS.classId) || '';
    var studentNo = localStorage.getItem(PROFILE_KEYS.studentNo) || '';
    var studentName = localStorage.getItem(PROFILE_KEYS.studentName) || '';
    var classGrade = global.EQClasses ? global.EQClasses.gradeFor(classId) : grade;
    var matchesGrade = !expectedGrade || !classId || classGrade === String(expectedGrade);

    if (!matchesGrade) {
      return {
        grade: String(expectedGrade || ''),
        classId: '',
        studentNo: '',
        studentName: '',
        hasProfile: false
      };
    }

    return {
      grade: grade || classGrade || String(expectedGrade || ''),
      classId: classId,
      studentNo: studentNo,
      studentName: studentName,
      hasProfile: !!(classId && studentNo && studentName)
    };
  }

  function save(profile) {
    var grade = String(profile.grade || '');
    var classId = String(profile.classId || '');
    var studentNo = String(profile.studentNo || '');
    var studentName = String(profile.studentName || '');
    if (!grade || !classId || !studentNo || !studentName) return false;

    localStorage.setItem(PROFILE_KEYS.grade, grade);
    localStorage.setItem(PROFILE_KEYS.classId, classId);
    localStorage.setItem(PROFILE_KEYS.studentNo, studentNo);
    localStorage.setItem(PROFILE_KEYS.studentName, studentName);
    localStorage.removeItem(PROFILE_KEYS.setupSkipped);
    return true;
  }

  function clear(options) {
    options = options || {};
    localStorage.removeItem(PROFILE_KEYS.classId);
    localStorage.removeItem(PROFILE_KEYS.studentNo);
    localStorage.removeItem(PROFILE_KEYS.studentName);
    if (options.includeGrade) localStorage.removeItem(PROFILE_KEYS.grade);
    if (options.includeSetup) localStorage.removeItem(PROFILE_KEYS.setupSkipped);
  }

  function classLabel(classId) {
    return global.EQClasses ? global.EQClasses.labelFor(classId) : String(classId || '');
  }

  function initials(name, fallback) {
    var value = String(name || '').trim();
    if (!value) return fallback || 'ST';
    return value.split(/\s+/).slice(0, 2).map(function(part) {
      return part.charAt(0);
    }).join('').toUpperCase();
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function(ch) {
      return {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
      }[ch];
    });
  }

  global.EQStudent = {
    keys: Object.assign({}, PROFILE_KEYS),
    read: read,
    save: save,
    clear: clear,
    classLabel: classLabel,
    initials: initials,
    escapeHtml: escapeHtml
  };
})(window);
