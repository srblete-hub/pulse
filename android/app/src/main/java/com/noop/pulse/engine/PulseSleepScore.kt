package com.noop.pulse.engine

import com.noop.ui.AppViewModel
import com.noop.ui.freshRestScore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The Sleep score exactly as noop's Today screen reads it: the `sleep_performance` series resolved across
 * sources (an imported WHOOP figure wins, otherwise noop's on-device Rest composite), and on today, while
 * tonight isn't scored yet, the latest night if it is still fresh ([freshRestScore]).
 */
object PulseSleepScore {
    private val _values = MutableStateFlow<Map<String, Double>>(emptyMap())
    val values: StateFlow<Map<String, Double>> = _values

    suspend fun refresh(vm: AppViewModel) {
        _values.value = runCatching {
            vm.repo.resolvedSeries("sleep_performance", "my-whoop", "0000-00-00", "9999-99-99", strapDeviceId = vm.activeStrapId)
                .values.associate { it.first to it.second }
        }.getOrDefault(_values.value)
    }

    fun forDay(dayKey: String, isToday: Boolean, byDay: Map<String, Double> = _values.value): Double? {
        val latest = byDay.entries.maxByOrNull { it.key }
        return freshRestScore(byDay[dayKey], latest?.key, latest?.value, isToday, dayKey)
    }
}
