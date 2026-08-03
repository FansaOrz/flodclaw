package com.foldledger.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import com.foldledger.domain.repo.SettingsRepository
import com.foldledger.presentation.accounts.AccountsRoute
import com.foldledger.presentation.budgets.BudgetsRoute
import com.foldledger.presentation.common.FoldLedgerMark
import com.foldledger.presentation.ledger.LedgerRoute
import com.foldledger.presentation.nav.TopDest
import com.foldledger.presentation.onboarding.OnboardingScreen
import com.foldledger.presentation.settings.SettingsRoute
import com.foldledger.presentation.stats.StatsRoute
import com.foldledger.presentation.theme.FoldLedgerTheme

@Composable
fun FoldLedgerApp(
    settingsRepository: SettingsRepository,
    onboardingDoneInitial: Boolean,
) {
    FoldLedgerTheme {
        var finishedOnboarding by remember { mutableStateOf(false) }
        val onboardingDone = finishedOnboarding || onboardingDoneInitial
        if (!onboardingDone) {
            OnboardingScreen(settings = settingsRepository) { finishedOnboarding = true }
            return@FoldLedgerTheme
        }

        val adaptive = currentWindowAdaptiveInfo()
        val widthSizeClass = adaptive.windowSizeClass.windowWidthSizeClass
        val useNavigationRail = widthSizeClass != WindowWidthSizeClass.COMPACT
        val useTwoPane = widthSizeClass == WindowWidthSizeClass.EXPANDED
        var selectedDestName by rememberSaveable { mutableStateOf(TopDest.Ledger.name) }
        var displayedDestName by rememberSaveable { mutableStateOf(TopDest.Ledger.name) }
        val selectedDest = TopDest.entries.firstOrNull { it.name == selectedDestName }
            ?: TopDest.Ledger
        val displayedDest = TopDest.entries.firstOrNull { it.name == displayedDestName }
            ?: TopDest.Ledger
        val tabStateHolder = rememberSaveableStateHolder()
        val tabTransition = updateTransition(
            targetState = displayedDest,
            label = "mainTab",
        )
        val transitionRunning = tabTransition.isRunning

        LaunchedEffect(transitionRunning, selectedDest, displayedDest) {
            if (!transitionRunning && selectedDest != displayedDest) {
                displayedDestName = selectedDest.name
            }
        }

        val onTabSelected: (TopDest) -> Unit = { requested ->
            selectedDestName = requested.name
            displayedDestName = resolveDisplayedTab(
                current = displayedDest,
                requested = requested,
                transitionRunning = transitionRunning,
            ).name
        }

        Row(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                ),
        ) {
            if (useNavigationRail) {
                LedgerNavigationRail(
                    selected = selectedDest,
                    onSelected = onTabSelected,
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            Scaffold(
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (!useNavigationRail) {
                        LedgerBottomNavigation(
                            selected = selectedDest,
                            onSelected = onTabSelected,
                        )
                    }
                },
            ) { paddingValues ->
                tabTransition.AnimatedContent(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize(),
                    transitionSpec = {
                        val direction = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                        (
                            slideInHorizontally(
                                animationSpec = tween(160, easing = FastOutSlowInEasing),
                            ) { width -> direction * width / 12 } +
                                fadeIn(tween(120))
                            ).togetherWith(
                            slideOutHorizontally(
                                animationSpec = tween(130, easing = FastOutSlowInEasing),
                            ) { width -> -direction * width / 14 } +
                                fadeOut(tween(90)),
                        )
                    },
                ) { tab ->
                    tabStateHolder.SaveableStateProvider(tab.name) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                        ) {
                            when (tab) {
                                TopDest.Ledger -> LedgerRoute(wide = useTwoPane)
                                TopDest.Stats -> StatsRoute()
                                TopDest.Accounts -> AccountsRoute()
                                TopDest.Budgets -> BudgetsRoute()
                                TopDest.Settings -> SettingsRoute()
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun resolveDisplayedTab(
    current: TopDest,
    requested: TopDest,
    transitionRunning: Boolean,
): TopDest = if (transitionRunning) current else requested

@Composable
private fun LedgerBottomNavigation(
    selected: TopDest,
    onSelected: (TopDest) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 10.dp,
    ) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            TopDest.entries.forEach { item ->
                NavigationBarItem(
                    selected = selected == item,
                    onClick = { onSelected(item) },
                    icon = {
                        if (item == TopDest.Ledger) {
                            Surface(
                                modifier = Modifier.size(44.dp),
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = if (selected == item) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shadowElevation = if (selected == item) 3.dp else 0.dp,
                            ) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = item.label,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(11.dp),
                                )
                            }
                        } else {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label,
                            )
                        }
                    },
                    label = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        indicatorColor = if (item == TopDest.Ledger) {
                            Color.Transparent
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        },
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun LedgerNavigationRail(
    selected: TopDest,
    onSelected: (TopDest) -> Unit,
) {
    NavigationRail(
        modifier = Modifier
            .width(88.dp)
            .fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface,
        header = {
            FoldLedgerMark(
                modifier = Modifier
                    .size(48.dp),
            )
        },
    ) {
        TopDest.entries.forEach { tab ->
            NavigationRailItem(
                selected = selected == tab,
                onClick = { onSelected(tab) },
                icon = { Icon(tab.icon, contentDescription = tab.label) },
                label = { Text(tab.label) },
            )
        }
    }
}
