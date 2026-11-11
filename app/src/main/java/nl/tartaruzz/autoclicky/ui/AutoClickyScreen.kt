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

package nl.tartaruzz.autoclicky.ui

import android.graphics.Point

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

import com.buzbuz.smartautoclicker.core.dumb.domain.model.DumbScenario

import kotlinx.coroutines.launch

import nl.tartaruzz.autoclicky.R
import nl.tartaruzz.autoclicky.engine.DefaultScenarioFactory
import nl.tartaruzz.autoclicky.engine.EngineController
import nl.tartaruzz.autoclicky.overlay.QuickPanelController
import nl.tartaruzz.autoclicky.recording.DumbScenarioBuilder
import nl.tartaruzz.autoclicky.recording.RecordingController
import nl.tartaruzz.autoclicky.recording.defaultRecordingName

@Composable
fun AutoClickyScreen(
    engineController: EngineController,
    recordingController: RecordingController,
    quickPanelController: QuickPanelController,
    screenCenter: Point,
    onOpenAccessibilitySettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()

    val scenarios by engineController.scenarios.collectAsState(initial = emptyList())
    val isRunning by engineController.isRunning.collectAsState()
    val runningScenario by engineController.runningScenario.collectAsState(initial = null)
    val isServiceConnected by engineController.isAccessibilityServiceConnected.collectAsState()
    val isRecording by recordingController.isRecording.collectAsState()
    val completedRecording by recordingController.completedRecording.collectAsState()
    val isPanelRequested by quickPanelController.isPanelRequested.collectAsState()

    var recordingName by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<DumbScenario?>(null) }
    var renameName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var showAbout by remember { mutableStateOf(false) }

    LaunchedEffect(completedRecording) {
        if (completedRecording != null) {
            recordingName = defaultRecordingName()
        }
    }

    val canRecord = isServiceConnected && !isRunning && !isRecording

    if (completedRecording != null) {
        NameDialog(
            title = "Save recording",
            value = recordingName,
            onValueChange = { newName -> recordingName = newName },
            confirmLabel = "Save",
            onConfirm = {
                val recording = completedRecording ?: return@NameDialog
                val name = recordingName.trim().ifEmpty { defaultRecordingName() }

                coroutineScope.launch {
                    engineController.addScenario(DumbScenarioBuilder.build(name, recording.actions))
                    recordingController.consumeCompletedRecording()
                }
            },
            onDismiss = {
                recordingController.consumeCompletedRecording()
            },
        )
    }

    renameTarget?.let { target ->
        NameDialog(
            title = "Rename template",
            value = renameName,
            onValueChange = { newName -> renameName = newName },
            confirmLabel = "Rename",
            onConfirm = {
                coroutineScope.launch {
                    engineController.renameScenario(target, renameName)
                    renameTarget = null
                }
            },
            onDismiss = { renameTarget = null },
        )
    }

    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo),
                contentDescription = null,
                modifier = Modifier.size(44.dp),
            )
            Text(
                text = "AutoClicky",
                style = MaterialTheme.typography.headlineMedium,
            )
        }

        Text(
            text = if (isServiceConnected) {
                "Accessibility service: connected"
            } else {
                "Accessibility service: not connected"
            },
            style = MaterialTheme.typography.bodyLarge,
        )

        Button(onClick = onOpenAccessibilitySettings) {
            Text("Open accessibility settings")
        }

        Text(
            text = "Floating panel",
            style = MaterialTheme.typography.titleLarge,
        )

        Text(
            text = "A small REC / STOP / PLAY bar that stays above the other apps: start a recording while you use " +
                "another app, stop it from there, and replay the last template without coming back here.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Button(
            enabled = isServiceConnected,
            onClick = { quickPanelController.toggle() },
        ) {
            Text(if (isPanelRequested) "Hide floating panel" else "Show floating panel")
        }

        Text(
            text = "Record",
            style = MaterialTheme.typography.titleLarge,
        )

        Button(
            enabled = canRecord,
            onClick = {
                if (!recordingController.startTapCapture()) {
                    message = "Cannot start the recording, is the accessibility service enabled?"
                }
            },
        ) {
            Text("Record taps (keep using your apps)")
        }

        Button(
            enabled = canRecord,
            onClick = {
                if (!recordingController.startGestureCapture()) {
                    message = "Cannot start the recording, is the accessibility service enabled?"
                }
            },
        ) {
            Text("Record gestures (swipes, long press, multi finger)")
        }

        if (isRecording) {
            Text(
                text = "Recording... tap the \"Stop recording\" button on the screen when you are done.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = { recordingController.stopCapture() }) {
                Text("Stop recording")
            }
        }

        Text(
            text = "Templates",
            style = MaterialTheme.typography.titleLarge,
        )

        if (scenarios.isEmpty()) {
            Text(
                text = "No template yet, record one!",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        scenarios.forEach { scenario ->
            ScenarioRow(
                scenario = scenario,
                isRunning = runningScenario?.id?.databaseId == scenario.id.databaseId,
                enabled = !isRunning,
                onPlay = {
                    coroutineScope.launch {
                        if (!engineController.startScenario(scenario.id.databaseId)) {
                            message = "Cannot play this template, is the accessibility service enabled?"
                        }
                    }
                },
                onStop = { engineController.stopScenario() },
                onDuplicate = { engineController.duplicateScenario(scenario) },
                onRename = {
                    renameName = scenario.name
                    renameTarget = scenario
                },
                onDelete = {
                    coroutineScope.launch { engineController.deleteScenario(scenario) }
                },
            )
        }

        TextButton(
            enabled = isServiceConnected && !isRunning && !isRecording,
            onClick = {
                coroutineScope.launch {
                    val scenarioId = engineController.addScenario(
                        DefaultScenarioFactory.createSingleClickScenario(screenCenter)
                    )
                    if (scenarioId != null) {
                        engineController.startScenario(scenarioId)
                    }
                }
            },
        ) {
            Text("Quick test: click at the screen center")
        }

        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { message = null }) {
                Text("Dismiss")
            }
        }

        TextButton(onClick = { showAbout = true }) {
            Text("About & license")
        }
    }
}
