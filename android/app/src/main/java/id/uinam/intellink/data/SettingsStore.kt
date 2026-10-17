package id.uinam.intellink.data

import android.content.Context

/** Menyimpan IP ESP32 terakhir (mengikuti perilaku web). */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("intellink", Context.MODE_PRIVATE)

    fun loadIp(): String = prefs.getString(KEY_LAST_IP, DEFAULT_IP) ?: DEFAULT_IP

    fun saveIp(ip: String) {
        prefs.edit().putString(KEY_LAST_IP, ip).apply()
    }

    companion object {
        const val DEFAULT_IP = "192.168.1.100"
        private const val KEY_LAST_IP = "last_ip"
    }
}
