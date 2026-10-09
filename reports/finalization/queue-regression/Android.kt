package android.content
abstract class Context {
    companion object { const val MODE_PRIVATE = 0 }
    abstract fun getSharedPreferences(name: String, mode: Int): SharedPreferences
}
interface SharedPreferences {
    fun getString(key: String, defaultValue: String?): String?
    fun edit(): Editor
    fun registerOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun unregisterOnSharedPreferenceChangeListener(listener: OnSharedPreferenceChangeListener)
    fun interface OnSharedPreferenceChangeListener { fun onChanged(prefs: SharedPreferences, key: String?) }
    interface Editor { fun putString(key: String, value: String): Editor; fun apply() }
}
