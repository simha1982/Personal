package com.simha.puzzlealarm.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Stores alarms as JSON in private SharedPreferences and exposes them as a flow for the UI. */
class AlarmRepository private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("alarms", Context.MODE_PRIVATE)

    private val _alarms = MutableStateFlow(load())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    fun newId(): Int = (_alarms.value.maxOfOrNull { it.id } ?: 0) + 1

    @Synchronized
    fun upsert(alarm: Alarm) {
        val list = _alarms.value.filterNot { it.id == alarm.id } + alarm
        save(list.sortedWith(compareBy({ it.hour }, { it.minute })))
    }

    @Synchronized
    fun delete(id: Int) = save(_alarms.value.filterNot { it.id == id })

    private fun save(list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY, arr.toString()).apply()
        _alarms.value = list
    }

    private fun load(): List<Alarm> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val KEY = "alarms"

        @Volatile private var instance: AlarmRepository? = null

        fun get(context: Context): AlarmRepository =
            instance ?: synchronized(this) {
                instance ?: AlarmRepository(context).also { instance = it }
            }
    }
}
