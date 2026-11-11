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

package nl.tartaruzz.autoclicky

import android.content.Intent
import android.os.Bundle
import android.provider.Settings

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier

import dagger.hilt.android.AndroidEntryPoint

import nl.tartaruzz.autoclicky.engine.EngineController
import nl.tartaruzz.autoclicky.overlay.QuickPanelController
import nl.tartaruzz.autoclicky.ui.AutoClickyScreen
import nl.tartaruzz.autoclicky.ui.theme.AutoClickyTheme

import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var engineController: EngineController
    @Inject lateinit var quickPanelController: QuickPanelController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AutoClickyTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AutoClickyScreen(
                        engineController = engineController,
                        quickPanelController = quickPanelController,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}
