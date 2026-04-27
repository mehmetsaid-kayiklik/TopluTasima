package com.example.toplutasima.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.koin.androidx.compose.koinViewModel
import com.example.toplutasima.model.NavItem
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S

import com.example.toplutasima.ui.screens.RMVLogScreen
import com.example.toplutasima.ui.screens.RecordsScreen
import com.example.toplutasima.ui.screens.SettingsScreen
import com.example.toplutasima.ui.screens.SummaryScreen
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.SettingsViewModel
import com.example.toplutasima.viewmodel.SummaryViewModel

@Composable
fun MainAppScreen(isDarkTheme: Boolean) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPersonal by remember { mutableStateOf(false) }
    val lang = LocaleManager.currentLanguage
    val rmvLogViewModel: RmvLogViewModel = koinViewModel()
    val navItems = listOf(
        NavItem(S.navRecord(lang), Icons.Filled.Create, Icons.Outlined.Create),
        NavItem(S.navSummary(lang), Icons.Filled.List, Icons.Outlined.List),
        NavItem(S.navRecords(lang), Icons.Filled.DateRange, Icons.Outlined.DateRange),
        NavItem(S.navSettings(lang), Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        modifier = Modifier.systemBarsPadding(),
        bottomBar = {
            NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp
                ) {
                    navItems.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Icon(
                                    imageVector = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.title
                                )
                            },
                            label = { Text(item.title, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
        }
    ) { innerPadding ->
        Crossfade(targetState = selectedTab, animationSpec = tween(300), label = "tabAnim") { tab ->
            when (tab) {
                0 -> RMVLogScreen(
                    modifier = Modifier.padding(innerPadding),
                    viewModel = rmvLogViewModel,
                    showPersonal = showPersonal,
                    onTogglePersonal = { showPersonal = it }
                )
                1 -> {
                    val summaryViewModel: SummaryViewModel = koinViewModel()
                    SummaryScreen(
                        modifier = Modifier.padding(innerPadding),
                        viewModel = summaryViewModel,
                        showPersonal = showPersonal,
                        onTogglePersonal = { showPersonal = it }
                    )
                }
                2 -> RecordsScreen(
                    modifier = Modifier.padding(innerPadding),
                    showPersonal = showPersonal,
                    onTogglePersonal = { showPersonal = it },
                    isActive = (tab == 2),
                    onRestoreRecord = { record ->
                        rmvLogViewModel.restoreRecord(record)
                        selectedTab = 0
                    }
                )
                3 -> {
                    val settingsViewModel: SettingsViewModel = koinViewModel()
                    SettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        isDarkTheme = isDarkTheme,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}
