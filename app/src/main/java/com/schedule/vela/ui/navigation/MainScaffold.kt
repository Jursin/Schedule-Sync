package com.schedule.vela.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.schedule.vela.ui.page.LicensePage
import com.schedule.vela.ui.theme.appBarBlur
import com.schedule.vela.ui.theme.blurAppBarColor
import com.schedule.vela.ui.theme.captureForBlur
import com.schedule.vela.ui.theme.rememberAppBlurBackdrop
import top.yukonga.miuix.kmp.basic.FloatingNavigationBar
import top.yukonga.miuix.kmp.basic.FloatingNavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationItem
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavCornerClipMode
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val tabs =
    listOf(
        NavigationItem("首页", MiuixIcons.Home),
        NavigationItem("设置", MiuixIcons.Settings),
    )

@Composable
private fun NavBarContent(
    floatingNav: Boolean,
    navBarBackdrop: LayerBackdrop?,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    val blurActive = navBarBackdrop != null
    if (floatingNav) {
        FloatingNavigationBar(
            color = if (blurActive) Color.Transparent else MiuixTheme.colorScheme.surfaceContainer,
            modifier =
                if (blurActive) {
                    Modifier.textureBlur(
                        backdrop = navBarBackdrop,
                        shape = RoundedCornerShape(50.dp),
                        blurRadius = 25f,
                        colors =
                            BlurColors(
                                blendColors =
                                    listOf(
                                        BlendColorEntry(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.8f)),
                                    ),
                            ),
                    )
                } else {
                    Modifier
                },
        ) {
            tabs.forEachIndexed { index, tab ->
                FloatingNavigationBarItem(
                    selected = pagerState.currentPage == index,
                    onClick = { onSelect(index) },
                    icon = tab.icon,
                    label = tab.label,
                )
            }
        }
    } else {
        NavigationBar(
            color = blurAppBarColor(navBarBackdrop),
            modifier = Modifier.appBarBlur(navBarBackdrop),
        ) {
            tabs.forEachIndexed { index, tab ->
                NavigationBarItem(
                    selected = pagerState.currentPage == index,
                    onClick = { onSelect(index) },
                    icon = tab.icon,
                    label = tab.label,
                )
            }
        }
    }
}

@Composable
private fun NavEntry(
    interceptPredictiveBack: Boolean,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = state,
        isBackEnabled = interceptPredictiveBack,
        onBackCompleted = onBack,
    )
    content()
}

@Composable
fun MainScaffold(
    floatingNav: Boolean,
    appBlur: Boolean,
    predictiveBackEnabled: Boolean,
    content: @Composable (page: Int, bottomPadding: Dp, navigate: (Page) -> Unit) -> Unit,
) {
    val navBackStack = rememberNavBackStack<Page>(Page.Home)
    val backStack: MutableList<NavKey> = navBackStack
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    val mainPagerState = rememberMainPagerState(pagerState, coroutineScope)
    val navBarBackdrop = rememberAppBlurBackdrop(appBlur)

    val onBack: () -> Unit =
        remember(backStack) {
            { backStack.removeLastOrNull() }
        }

    val navigate: (Page) -> Unit =
        remember(backStack) {
            { page -> if (backStack.lastOrNull() != page) backStack.add(page) }
        }

    val interceptPredictiveBack = !predictiveBackEnabled && backStack.size > 1

    // 回到上一层：在非首个 Tab 时先回到首个 Tab
    val isPagerBackHandlerEnabled by remember {
        derivedStateOf { backStack.size == 1 && mainPagerState.selectedPage != 0 }
    }
    val pagerBackEventState = rememberNavigationEventState(NavigationEventInfo.None)
    NavigationBackHandler(
        state = pagerBackEventState,
        isBackEnabled = isPagerBackHandlerEnabled,
        onBackCompleted = { mainPagerState.animateToPage(0) },
    )

    LaunchedEffect(pagerState.currentPage) {
        mainPagerState.syncPage()
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
    ) {
        NavDisplay(
            backStack = navBackStack,
            onBack = onBack,
            transition = NavTransitions.MiuixDefault,
            effects =
                NavDisplayEffects(
                    enableCornerClip = true,
                    cornerClipRadius = 32.dp,
                    cornerClipMode = NavCornerClipMode.Leading,
                    dimAmount = 0.5f,
                    backdropColor = MiuixTheme.colorScheme.surface,
                    blockInputDuringTransition = false,
                ),
        ) {
            entry<Page.Home> {
                NavEntry(interceptPredictiveBack, onBack) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            userScrollEnabled = true,
                            beyondViewportPageCount = 1,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .captureForBlur(navBarBackdrop),
                        ) { page ->
                            content(page, 80.dp, navigate)
                        }
                        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                            NavBarContent(
                                floatingNav = floatingNav,
                                navBarBackdrop = navBarBackdrop,
                                pagerState = pagerState,
                                onSelect = { mainPagerState.animateToPage(it) },
                            )
                        }
                    }
                }
            }
            entry<Page.License> {
                NavEntry(interceptPredictiveBack, onBack) {
                    LicensePage(
                        appBlur = appBlur,
                        onBack = onBack,
                    )
                }
            }
        }
    }
}
