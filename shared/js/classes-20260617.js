/* English Quest canonical class and pre-roster configuration. */
(function(global) {
  var DEFAULT_ROSTER_SIZE = 36;

  // Numbered-roster mode: show a placeholder roster of "Student 1..36" per class
  // so the site is usable before real names exist. This ONLY affects what names
  // are DISPLAYED/pickable — it never fetches real backend data.
  // Went LIVE 2026-07-10: real rosters loaded into all 8 class sheets, so this is
  // now false (students pick their real name) and rostersReady() is true.
  var NUMBERED_ROSTER = false;

  var GRADE_X = [
    { id: 'XE1', label: 'X E-1', shortLabel: 'XE1', grade: '10' },
    { id: 'XE2', label: 'X E-2', shortLabel: 'XE2', grade: '10' },
    { id: 'XE3', label: 'X E-3', shortLabel: 'XE3', grade: '10' },
    { id: 'XE4', label: 'X E-4', shortLabel: 'XE4', grade: '10' },
    { id: 'XE5', label: 'X E-5', shortLabel: 'XE5', grade: '10' }
  ];

  var GRADE_XI = [
    { id: 'XIF7', label: 'XI F-7', shortLabel: 'XIF7', grade: '11' },
    { id: 'XIF8', label: 'XI F-8', shortLabel: 'XIF8', grade: '11' },
    { id: 'XIF9', label: 'XI F-9', shortLabel: 'XIF9', grade: '11' }
  ];

  var ALL = GRADE_X.concat(GRADE_XI);
  var labels = {};
  var shortLabels = {};
  var gradeById = {};

  ALL.forEach(function(item) {
    labels[item.id] = item.label;
    shortLabels[item.id] = item.shortLabel;
    gradeById[item.id] = item.grade;
  });

  function cloneList(list) {
    return list.map(function(item) {
      return Object.assign({}, item);
    });
  }

  function listForGrade(grade) {
    return grade === '11' ? cloneList(GRADE_XI) : cloneList(GRADE_X);
  }

  function idsForGrade(grade) {
    return listForGrade(grade).map(function(item) { return item.id; });
  }

  function exists(id) {
    return !!gradeById[id];
  }

  function isForGrade(id, grade) {
    return gradeFor(id) === String(grade);
  }

  function labelFor(id) {
    return labels[id] || id || '';
  }

  function shortLabelFor(id) {
    return shortLabels[id] || id || '';
  }

  function gradeFor(id) {
    return gradeById[id] || '';
  }

  function optionHtml(grade, selectedId) {
    return listForGrade(grade).map(function(item) {
      var selected = item.id === selectedId ? ' selected' : '';
      return '<option value="' + item.id + '"' + selected + '>' + item.label + '</option>';
    }).join('');
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

  function fillSelect(target, grade, selectedId, placeholderText) {
    var select = typeof target === 'string' ? document.getElementById(target) : target;
    if (!select) return;
    select.innerHTML = '<option value="">' + escapeHtml(placeholderText || 'Choose class') + '</option>' + optionHtml(grade, selectedId);
  }

  function buildPlaceholderRoster(classId, count) {
    var grade = gradeFor(classId);
    var total = Number(count || DEFAULT_ROSTER_SIZE);
    if (!grade || !Number.isFinite(total) || total < 1) return [];

    return Array.from({ length: total }, function(_, index) {
      var no = index + 1;
      return {
        grade: grade,
        classId: classId,
        studentNo: String(no),
        no: no,
        fullName: 'Student ' + no,
        name: 'Student ' + no,
        preferredName: '',
        profilePhoto: '',
        active: false,
        placeholder: true
      };
    });
  }

  function placeholderRosterFor(classId) {
    return buildPlaceholderRoster(classId, DEFAULT_ROSTER_SIZE);
  }

  function dataReadiness() {
    return {
      classesReady: true,
      rostersReady: true,
      attendanceReady: true,
      rosterStatus: 'Official student rosters are loaded.',
      grade10Classes: idsForGrade('10'),
      grade11Classes: idsForGrade('11')
    };
  }

  function rostersReady() {
    return dataReadiness().rostersReady === true;
  }

  // Attendance runs on a SEPARATE backend that still holds last-year data.
  // Keep this false so attendance pages stay placeholder-safe (no old-year
  // records) until a new-period attendance sheet is wired. Flip to true then.
  function attendanceReady() {
    return dataReadiness().attendanceReady === true;
  }

  // True when the site should display the numbered placeholder roster (Student
  // 1..36) in place of real names. Independent of rostersReady() on purpose.
  function numberedRosterMode() {
    return NUMBERED_ROSTER === true;
  }

  global.EQClasses = {
    defaultRosterSize: DEFAULT_ROSTER_SIZE,
    grade10: cloneList(GRADE_X),
    grade11: cloneList(GRADE_XI),
    all: cloneList(ALL),
    labels: Object.assign({}, labels),
    shortLabels: Object.assign({}, shortLabels),
    listForGrade: listForGrade,
    idsForGrade: idsForGrade,
    exists: exists,
    isForGrade: isForGrade,
    labelFor: labelFor,
    shortLabelFor: shortLabelFor,
    gradeFor: gradeFor,
    optionHtml: optionHtml,
    fillSelect: fillSelect,
    placeholderRosterFor: placeholderRosterFor,
    dataReadiness: dataReadiness,
    rostersReady: rostersReady,
    attendanceReady: attendanceReady,
    numberedRosterMode: numberedRosterMode
  };
})(window);
