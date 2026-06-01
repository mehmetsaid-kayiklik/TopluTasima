package com.example.toplutasima.ui.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import com.example.toplutasima.model.NavItem
import com.example.toplutasima.ui.LocaleManager
import com.example.toplutasima.ui.S
import com.example.toplutasima.ui.screens.RMVLogScreen
import com.example.toplutasima.ui.screens.RecordsScreen
import com.example.toplutasima.ui.screens.SettingsScreen
import com.example.toplutasima.ui.screens.SummaryScreen
import com.example.toplutasima.ui.theme.AccentDark
import com.example.toplutasima.ui.theme.AccentLight
import com.example.toplutasima.ui.theme.TextLowDark
import com.example.toplutasima.ui.theme.TextLowLight
import com.example.toplutasima.viewmodel.RmvLogViewModel
import com.example.toplutasima.viewmodel.SettingsViewModel
import com.example.toplutasima.viewmodel.SummaryViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
private fun isDark() = isSystemInDarkTheme()

@Composable
fun MainAppScreen(isDarkTheme: Boolean) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showPersonal by remember { mutableStateOf(false) }
    val lang = LocaleManager.currentLanguage
    val dark = isDark()
    val accent = if (dark) AccentDark else AccentLight
    val unselectedTint = if (dark) TextLowDark else TextLowLight
    val rmvLogViewModel: RmvLogViewModel = koinViewModel()
    val summaryViewModel: SummaryViewModel = koinViewModel()
    val settingsViewModel: SettingsViewModel = koinViewModel()
    val navItems = listOf(
        NavItem(S.navRecord(lang), Icons.Filled.Create, Icons.Outlined.Create),
        NavItem(S.navSummary(lang), Icons.Filled.List, Icons.Outlined.List),
        NavItem(S.navRecords(lang), Icons.Filled.DateRange, Icons.Outlined.DateRange),
        NavItem(S.navSettings(lang), Icons.Filled.Settings, Icons.Outlined.Settings)
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                navItems.forEachIndexed { index, item ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        BottomNavIcon(
                            icon = if (selectedTab == index) item.selectedIcon else item.unselectedIcon,
                            label = item.title,
                            selected = selectedTab == index,
                            selectedTint = accent,
                            unselectedTint = unselectedTint,
                            onClick = { selectedTab = index }
                        )
                    }
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
                        showPersonal = false
                        rmvLogViewModel.restoreRecord(record)
                        selectedTab = 0
                    }
                )
                3 -> {
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

@Composable
private fun BottomNavIcon(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    selectedTint: androidx.compose.ui.graphics.Color,
    unselectedTint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.6f else 1f,
        label = "bottomNavPressAlpha"
    )

    Column(
        modifier = Modifier
            .height(48.dp)
            .padding(horizontal = 14.dp)
            .alpha(pressAlpha)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) selectedTint else unselectedTint,
            modifier = Modifier.size(24.dp)
        )
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .size(if (selected) 3.dp else 0.dp)
        ) {
            if (selected) {
                Surface(
                    modifier = Modifier.size(3.dp),
                    shape = CircleShape,
                    color = selectedTint,
                    content = {}
                )
            }
        }
    }
}
