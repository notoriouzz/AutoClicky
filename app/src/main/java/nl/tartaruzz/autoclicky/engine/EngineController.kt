/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Copyright (C) 2026 AutoClicky contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nl.tartaruzz.autoclicky.engine

import android.util.Log

import com.buzbuz.smartautoclicker.core.dumb.domain.IDumbRepository
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbAction
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.domain.model.REPEAT_DELAY_MAX_MS
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point to drive the vendored Smart AutoClicker (Klick'r) dumb engine.
 *
 * Both the accessibility service, the activity and (later) the recorder control the engine through this façade, so the
 * engine lifecycle stays consistent: a scenario can only be started once the accessibility service has initialized the
 * gesture executor.
 */
@Singleton
class EngineController @Inject constructor(
    private val dumbEngine: DumbEngine,
    private val dumbRepository: IDumbRepository,
) {

    /** All the scenarios stored in the database, ordered by name. */
    val scenarios: Flow<List<DumbScenario>> = dumbRepository.dumbScenarios

    /** True while a scenario is being played. */
    val isRunning: StateFlow<Boolean> = dumbEngine.isRunning

    /** The scenario currently played, if any. */
    val runningScenario: Flow<DumbScenario?> = dumbEngine.dumbScenario

    private val _lastStartedScenarioId = MutableStateFlow<Long?>(null)

    /**
     * Database id of the last scenario that was started or created, kept in memory only.
     *
     * The floating panel uses it to replay the same template without opening the activity, and setting it on insertion
     * makes the PLAY button replay the recording that was just saved.
     */
    val lastStartedScenarioId: StateFlow<Long?> = _lastStartedScenarioId

    private val _isAccessibilityServiceConnected = MutableStateFlow(false)

    /** True when the accessibility service is connected, which is required to execute any gesture. */
    val isAccessibilityServiceConnected: StateFlow<Boolean> = _isAccessibilityServiceConnected

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _repeatSetting = MutableStateFlow(RepeatSetting.SINGLE)

    /**
     * How the templates are played: once, a fixed number of times or endlessly.
     *
     * Chosen from the panel or from the save card, it applies to the next [startScenario] done without an explicit
     * setting, which is what the PLAY button does.
     */
    val repeatSetting: StateFlow<RepeatSetting> = _repeatSetting

    private val _isInfiniteRunActive = MutableStateFlow(false)

    /** True while the running template repeats endlessly, so a touch on the screen must stop it. */
    val isInfiniteRunActive: StateFlow<Boolean> = _isInfiniteRunActive

    init {
        // The engine can also stop on its own (last repetition reached): forget the endless run in that case too.
        scope.launch {
            isRunning.collect { running -> if (!running) _isInfiniteRunActive.value = false }
        }
    }

    fun onAccessibilityServiceConnected() {
        Log.i(TAG, "Accessibility service connected")
        _isAccessibilityServiceConnected.value = true
    }

    fun onAccessibilityServiceDisconnected() {
        Log.i(TAG, "Accessibility service disconnected")
        _isAccessibilityServiceConnected.value = false
        dumbEngine.release()
    }

    /**
     * The vendored data source runs blocking DAO queries (for example `getDumbActions` while inserting the actions of a
     * scenario), and Room refuses to run those on the main thread. Every database access therefore goes through the IO
     * dispatcher, whatever the context of the caller is.
     */
    private suspend fun <T> onDatabase(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    /**
     * Persist a new scenario.
     *
     * @return the database id of the inserted scenario, or null if it could not be found after the insertion.
     */
    suspend fun addScenario(scenario: DumbScenario): Long? {
        val newScenarioId = onDatabase {
            val knownIds = dumbRepository.dumbScenarios.first().map { it.id.databaseId }.toSet()

            dumbRepository.addDumbScenario(scenario)

            dumbRepository.dumbScenarios.first()
                .firstOrNull { storedScenario -> storedScenario.id.databaseId !in knownIds }
                ?.id?.databaseId
        }

        // The PLAY button of the floating panel must replay the recording that was just saved.
        if (newScenarioId != null) _lastStartedScenarioId.value = newScenarioId

        return newScenarioId
    }

    /**
     * Start the scenario with the provided database id. Returns false if it can't be started.
     *
     * @param repeat how the scenario must be repeated. When null, the setting stored in the template is used, so a
     * template saved as "5×" keeps playing five times. The setting is only applied in memory, the stored template is
     * left untouched: use [persistScenarioRepeat] to store it.
     */
    suspend fun startScenario(scenarioDbId: Long, repeat: RepeatSetting? = null): Boolean {
        if (!_isAccessibilityServiceConnected.value) return false

        val storedScenario = onDatabase { dumbRepository.getDumbScenario(scenarioDbId) } ?: return false
        val scenario = repeat?.let { setting -> storedScenario.withRepeat(setting) } ?: storedScenario
        if (!scenario.isValid()) return false

        _repeatSetting.value = scenario.toRepeatSetting()

        dumbEngine.init(scenario)
        dumbEngine.startDumbScenario()
        _lastStartedScenarioId.value = scenarioDbId
        _isInfiniteRunActive.value = scenario.isRepeatInfinite
        return true
    }

    /**
     * Start the scenario that was played last, falling back to the first stored one.
     *
     * @param repeat how the scenario must be repeated, see [startScenario].
     * @return false if no scenario could be started.
     */
    suspend fun startLastUsedScenario(repeat: RepeatSetting? = null): Boolean {
        val lastStartedId = _lastStartedScenarioId.value
        if (lastStartedId != null && startScenario(lastStartedId, repeat)) return true

        val fallbackId = onDatabase { dumbRepository.dumbScenarios.first().firstOrNull()?.id?.databaseId }
            ?: return false
        return startScenario(fallbackId, repeat)
    }

    /** Remember how the templates must be played, without starting anything. */
    fun setRepeatSetting(repeat: RepeatSetting) {
        _repeatSetting.value = repeat.sanitized()
    }

    /** Store the repeat choice in the template itself, so it survives a restart. */
    suspend fun persistScenarioRepeat(scenarioDbId: Long, repeat: RepeatSetting) {
        val scenario = onDatabase { dumbRepository.getDumbScenario(scenarioDbId) } ?: return

        onDatabase {
            dumbRepository.updateDumbScenario(scenario.withRepeat(repeat.sanitized()))
        }
    }

    fun stopScenario() {
        _isInfiniteRunActive.value = false
        dumbEngine.stopDumbScenario()
    }

    /** Rename a stored scenario. No-op when the new name is blank or unchanged. */
    suspend fun renameScenario(scenario: DumbScenario, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty() || trimmedName == scenario.name) return

        onDatabase { dumbRepository.updateDumbScenario(scenario.copy(name = trimmedName)) }
    }

    /** Store a copy of [scenario] under [copyName]. */
    fun duplicateScenario(scenario: DumbScenario, copyName: String = "${scenario.name} (copy)") {
        dumbRepository.addDumbScenarioCopy(scenario.id.databaseId, copyName) { /* The scenario flow refreshes itself. */ }
    }

    suspend fun deleteScenario(scenario: DumbScenario) {
        if (dumbEngine.isRunning.value) return

        onDatabase { dumbRepository.deleteDumbScenario(scenario) }
    }
}

/**
 * Apply a [RepeatSetting] to this scenario.
 *
 * The loop delay is stored on the last action: the engine waits the `repeatDelayMs` of an action after executing it, so
 * the delay of the last one is also the idle time between two passes. It is only touched when the scenario actually
 * repeats, so a single run keeps the delays that were recorded.
 */
private fun DumbScenario.withRepeat(repeat: RepeatSetting): DumbScenario {
    val setting = repeat.sanitized()
    val actions = if (setting.isSingleRun) {
        dumbActions
    } else {
        dumbActions.mapIndexed { index, action ->
            if (index == dumbActions.lastIndex) action.withRepeatDelay(setting.loopDelayMs) else action
        }
    }

    return copy(
        repeatCount = setting.repeatCount,
        isRepeatInfinite = setting.isRepeatInfinite,
        dumbActions = actions,
    )
}

private fun DumbAction.withRepeatDelay(delayMs: Long): DumbAction = when (this) {
    is DumbAction.DumbClick -> copy(repeatDelayMs = delayMs.coerceAtMost(REPEAT_DELAY_MAX_MS))
    is DumbAction.DumbGesture -> copy(repeatDelayMs = delayMs.coerceAtMost(REPEAT_DELAY_MAX_MS))
    is DumbAction.DumbPause -> this
}

private const val TAG = "EngineController"
