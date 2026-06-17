/* English Quest canonical class configuration */
(function(global) {
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

  global.EQClasses = {
    grade10: cloneList(GRADE_X),
    grade11: cloneList(GRADE_XI),
    all: cloneList(ALL),
    labels: Object.assign({}, labels),
    shortLabels: Object.assign({}, shortLabels),
    listForGrade: listForGrade,
    idsForGrade: idsForGrade,
    labelFor: labelFor,
    shortLabelFor: shortLabelFor,
    gradeFor: gradeFor,
    optionHtml: optionHtml
  };
})(window);
