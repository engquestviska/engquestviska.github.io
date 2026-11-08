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

  // The server only lists tasks a student has started, so add the ones not started yet.
  // Every chapter has three tasks, the same as the Submission page.
  // A task the teacher sent back still has to be done again.
  function fullTasks(tasks, statuses) {
    var saved = tasks || {};
    var reviewed = statuses || {};
    var full = {};
    for (var chapter = 1; chapter <= 3; chapter++) {
      for (var task = 1; task <= 3; task++) {
        var key = 'C' + chapter + 'T' + task;
        full[key] = !!saved[key];
      }
    }
    Object.keys(saved).forEach(function(key) { full[key] = !!saved[key]; });
    Object.keys(reviewed).forEach(function(key) {
      if (reviewed[key] === 'rejected') full[key] = false;
    });
    return full;
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

  // A phone remembers class + absen number + name. When the class list is renumbered (a student
  // left school), that number can point at someone else. Check the saved name against the
  // class list: follow the name to its new number, or ask to choose again if it is gone.
  var CHECK_KEY = 'eq_student_checked';
  var CHECK_EVERY_MS = 30 * 60 * 1000;
  function sameName(a, b) {
    return String(a || '').trim().toUpperCase() === String(b || '').trim().toUpperCase();
  }
  function checkRoster() {
    var p = read();
    if (!p.hasProfile || !global.EQApi) return;
    var sig = p.classId + '|' + p.studentNo + '|' + p.studentName;
    try {
      var last = JSON.parse(localStorage.getItem(CHECK_KEY) || '{}');
      if (last.sig === sig && Date.now() - last.at < CHECK_EVERY_MS) return;
    } catch (e) {}
    global.EQApi.get({ action: 'getStudents', className: p.classId }).then(function(data) {
      var list = (data && data.students) || [];
      if (!list.length) return; // no answer: leave everything as it is
      var here = list.filter(function(s) { return String(s.no) === String(p.studentNo); })[0];
      if (here && sameName(here.name, p.studentName)) {
        localStorage.setItem(CHECK_KEY, JSON.stringify({ sig: sig, at: Date.now() }));
        return;
      }
      var moved = list.filter(function(s) { return sameName(s.name, p.studentName); })[0];
      if (moved) {
        save({ grade: p.grade, classId: p.classId, studentNo: moved.no, studentName: moved.name });
      } else {
        clear();
      }
      localStorage.removeItem(CHECK_KEY);
      global.location.reload();
    }).catch(function() {});
  }
  if (global.document) {
    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', checkRoster);
    else setTimeout(checkRoster, 0);
  }

  global.EQStudent = {
    keys: Object.assign({}, PROFILE_KEYS),
    read: read,
    save: save,
    clear: clear,
    classLabel: classLabel,
    initials: initials,
    fullTasks: fullTasks,
    escapeHtml: escapeHtml
  };
})(window);
