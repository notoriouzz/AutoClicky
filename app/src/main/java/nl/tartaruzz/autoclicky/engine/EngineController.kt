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
import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario
import com.buzbuz.smartautoclicker.core.dumb.engine.DumbEngine

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
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
     * Database id of the last scenario that was started, kept in memory only.
     *
     * The floating panel uses it to replay the same template without opening the activity.
     */
    val lastStartedScenarioId: StateFlow<Long?> = _lastStartedScenarioId

    private val _isAccessibilityServiceConnected = MutableStateFlow(false)

    /** True when the accessibility service is connected, which is required to execute any gesture. */
    val isAccessibilityServiceConnected: StateFlow<Boolean> = _isAccessibilityServiceConnected

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
    suspend fun addScenario(scenario: DumbScenario): Long? = onDatabase {
        val knownIds = dumbRepository.dumbScenarios.first().map { it.id.databaseId }.toSet()

        dumbRepository.addDumbScenario(scenario)

        dumbRepository.dumbScenarios.first()
            .firstOrNull { storedScenario -> storedScenario.id.databaseId !in knownIds }
            ?.id?.databaseId
    }

    /**
     * Start the scenario with the provided database id. Returns false if it can't be started.
     */
    suspend fun startScenario(scenarioDbId: Long): Boolean {
        if (!_isAccessibilityServiceConnected.value) return false

        val scenario = onDatabase { dumbRepository.getDumbScenario(scenarioDbId) } ?: return false
        if (!scenario.isValid()) return false

        dumbEngine.init(scenario)
        dumbEngine.startDumbScenario()
        _lastStartedScenarioId.value = scenarioDbId
        return true
    }

    /**
     * Start the scenario that was played last, falling back to the first stored one.
     *
     * @return false if no scenario could be started.
     */
    suspend fun startLastUsedScenario(): Boolean {
        val lastStartedId = _lastStartedScenarioId.value
        if (lastStartedId != null && startScenario(lastStartedId)) return true

        val fallbackId = onDatabase { dumbRepository.dumbScenarios.first().firstOrNull()?.id?.databaseId }
            ?: return false
        return startScenario(fallbackId)
    }

    fun stopScenario() {
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

private const val TAG = "EngineController"
