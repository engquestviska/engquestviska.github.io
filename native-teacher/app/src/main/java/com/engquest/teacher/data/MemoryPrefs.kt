package com.engquest.teacher.data

import android.content.SharedPreferences

/**
 * Prefs that live only while the app runs. Last resort when the phone's encrypted store cannot
 * be opened even after a reset: the teacher can still log in, but must log in again next launch.
 */
class MemoryPrefs : SharedPreferences {
    private val values = mutableMapOf<String, Any?>()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    @Synchronized override fun getAll(): Map<String, *> = values.toMap()
    @Synchronized override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    @Synchronized override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? =
        values[key] as? Set<String> ?: defValues

    @Synchronized override fun getInt(key: String, defValue: Int): Int = values[key] as? Int ?: defValue
    @Synchronized override fun getLong(key: String, defValue: Long): Long = values[key] as? Long ?: defValue
    @Synchronized override fun getFloat(key: String, defValue: Float): Float = values[key] as? Float ?: defValue
    @Synchronized override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    @Synchronized override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = Editor()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(this) { listeners += listener }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(this) { listeners -= listener }
    }

    private inner class Editor : SharedPreferences.Editor {
        private val puts = mutableMapOf<String, Any?>()
        private val removes = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?) = apply { puts[key] = value }
        override fun putStringSet(key: String, values: Set<String>?) = apply { puts[key] = values?.toSet() }
        override fun putInt(key: String, value: Int) = apply { puts[key] = value }
        override fun putLong(key: String, value: Long) = apply { puts[key] = value }
        override fun putFloat(key: String, value: Float) = apply { puts[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { puts[key] = value }
        override fun remove(key: String) = apply { removes += key }
        override fun clear() = apply { clear = true }

        override fun commit(): Boolean {
            val changed: Set<String>
            val notify: List<SharedPreferences.OnSharedPreferenceChangeListener>
            synchronized(this@MemoryPrefs) {
                if (clear) values.clear()
                removes.forEach { values.remove(it) }
                puts.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
                changed = removes + puts.keys
                notify = listeners.toList()
            }
            for (key in changed) notify.forEach { it.onSharedPreferenceChanged(this@MemoryPrefs, key) }
            return true
        }

        override fun apply() {
            commit()
        }
    }
}
