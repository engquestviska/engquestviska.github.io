package com.engquest.teacher.domain

data class Student(
    val no: String,
    val name: String,
    val nickname: String,
    /** Stored marks keyed by meeting, e.g. "m3" -> "Sick". */
    val attendance: Map<String, String>,
)

data class Meeting(val number: Int, val date: String)

data class SubmissionRow(
    val subId: String,
    val className: String,
    val studentNo: String,
    val studentName: String,
    val task: String,
    val timestamp: String,
    val status: String,
)

/** One student's submissions, tasks in teaching order. */
data class SubmissionGroup(
    val studentNo: String,
    val studentName: String,
    val className: String,
    val items: List<SubmissionRow>,
)

data class ClassInfo(val id: String, val label: String, val grade: String)

/** Same classes as shared/js/classes-20260617.js. */
object Classes {
    val ALL: List<ClassInfo> = listOf(
        ClassInfo("XE1", "X E-1", "10"),
        ClassInfo("XE2", "X E-2", "10"),
        ClassInfo("XE3", "X E-3", "10"),
        ClassInfo("XE4", "X E-4", "10"),
        ClassInfo("XE5", "X E-5", "10"),
        ClassInfo("XIF7", "XI F-7", "11"),
        ClassInfo("XIF8", "XI F-8", "11"),
        ClassInfo("XIF9", "XI F-9", "11"),
    )
}
