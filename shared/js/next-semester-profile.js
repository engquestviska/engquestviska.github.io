(function(global) {
  var KEYS = {
    classId: 'eq_next_class_id',
    studentNo: 'eq_next_student_no',
    fullName: 'eq_next_student_name'
  };

  function get() {
    return {
      classId: localStorage.getItem(KEYS.classId) || '',
      studentNo: localStorage.getItem(KEYS.studentNo) || '',
      fullName: localStorage.getItem(KEYS.fullName) || ''
    };
  }

  function has(profile) {
    profile = profile || get();
    return !!(profile.classId && profile.studentNo);
  }

  function save(profile) {
    localStorage.setItem(KEYS.classId, profile.classId || '');
    localStorage.setItem(KEYS.studentNo, profile.studentNo || '');
    localStorage.setItem(KEYS.fullName, profile.fullName || '');
  }

  function clear() {
    localStorage.removeItem(KEYS.classId);
    localStorage.removeItem(KEYS.studentNo);
    localStorage.removeItem(KEYS.fullName);
  }

  global.EQNextProfile = {
    get: get,
    has: has,
    save: save,
    clear: clear
  };
})(window);
