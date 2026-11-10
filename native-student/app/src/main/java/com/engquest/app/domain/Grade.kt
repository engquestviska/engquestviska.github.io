package com.engquest.app.domain

/** The two grades. Grade 10 classes are XE1–XE5 (blue), Grade 11 classes XIF7–XIF9 (teal). */
enum class Grade(val number: String, val classes: List<String>) {
    G10("10", listOf("XE1", "XE2", "XE3", "XE4", "XE5")),
    G11("11", listOf("XIF7", "XIF8", "XIF9"));

    companion object {
        fun of(number: String?): Grade? = entries.firstOrNull { it.number == number?.trim() }
        fun ofClass(classId: String): Grade? = entries.firstOrNull { classId.uppercase() in it.classes }
    }
}
