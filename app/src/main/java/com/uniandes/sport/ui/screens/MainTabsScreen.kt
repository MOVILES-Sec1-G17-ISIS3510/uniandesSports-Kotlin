package com.uniandes.sport.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.uniandes.sport.ui.navigation.Screen
import com.uniandes.sport.ui.screens.tabs.*
import com.uniandes.sport.viewmodels.profesores.FirestoreProfesoresViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainTabsScreen(
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit,
    onNavigate: (String) -> Unit
) {
    val coreScreens = listOf(Screen.Home, Screen.Retos, Screen.Play, Screen.Comunidades, Screen.Profesores)
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { coreScreens.size })

    // Report page changes to sync with BottomBar
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    // Direct jump if initialPage changes from outside
    LaunchedEffect(initialPage) {
        if (pagerState.currentPage != initialPage) {
            pagerState.scrollToPage(initialPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = true
    ) { page ->
        when (coreScreens[page]) {
            Screen.Home -> HomeScreen(onNavigate = onNavigate)
            Screen.Retos -> RetosScreen(onNavigate = onNavigate)
            Screen.Play -> PlayScreen(onNavigate = onNavigate)
            Screen.Comunidades -> ComunidadesScreen(onNavigate = onNavigate)
            Screen.Profesores -> {
                val profesoresViewModel = FirestoreProfesoresViewModel()
                ProfesoresScreen(
                    profesoresViewModel = profesoresViewModel,
                    onNavigate = onNavigate
                )
            }
            else -> {}
        }
    }
}
