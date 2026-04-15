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
import com.uniandes.sport.ui.screens.tabs.communities.CommunitiesMainScreen
import com.uniandes.sport.viewmodels.communities.FirestoreCommunitiesViewModel
import com.uniandes.sport.viewmodels.profesores.FirestoreProfesoresViewModel
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainTabsScreen(
    initialPage: Int = 0,
    pendingOpenEventId: String? = null,
    onOpenEventConsumed: () -> Unit = {},
    onPageChanged: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    searchQuery: String = "",
    modifier: Modifier = Modifier
) {
    val coreScreens = listOf(Screen.Home, Screen.Challenges, Screen.Play, Screen.Comunidades, Screen.Profesores)
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
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = true
    ) { page ->
        when (coreScreens[page]) {
            Screen.Home -> {
                val logViewModel: com.uniandes.sport.viewmodels.log.FirebaseLogViewModel = viewModel()
                val authViewModel: com.uniandes.sport.viewmodels.auth.FirebaseAuthViewModel = viewModel()
                val retosViewModel: com.uniandes.sport.viewmodels.retos.FirestoreRetosViewModel = viewModel()
                val playViewModel: com.uniandes.sport.viewmodels.play.FirestorePlayViewModel = viewModel(
                    factory = com.uniandes.sport.viewmodels.play.FirestorePlayViewModel.provideFactory(logViewModel)
                )
                HomeScreen(
                    onNavigate = onNavigate,
                    authViewModel = authViewModel,
                    retosViewModel = retosViewModel,
                    playViewModel = playViewModel,
                    logViewModel = logViewModel
                )
            }
            Screen.Challenges -> {
                val retosViewModel: com.uniandes.sport.viewmodels.retos.FirestoreRetosViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                val logViewModel: com.uniandes.sport.viewmodels.log.FirebaseLogViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                
                // Update search query in VM
                LaunchedEffect(searchQuery) {
                    retosViewModel.setSearchQuery(searchQuery)
                }

                ChallengesScreen(
                    viewModel = retosViewModel,
                    logViewModel = logViewModel,
                    onNavigate = onNavigate
                )
            }
            Screen.Play -> {
                val logViewModel: com.uniandes.sport.viewmodels.log.FirebaseLogViewModel = viewModel()
                val playViewModel: com.uniandes.sport.viewmodels.play.FirestorePlayViewModel = viewModel(
                    factory = com.uniandes.sport.viewmodels.play.FirestorePlayViewModel.provideFactory(logViewModel)
                )
                com.uniandes.sport.ui.screens.tabs.play.PlayScreen(
                    viewModel = playViewModel,
                    openEventId = pendingOpenEventId,
                    onOpenEventConsumed = onOpenEventConsumed,
                    logViewModel = logViewModel,
                    onNavigate = onNavigate
                )
            }
            Screen.Comunidades -> {
                val communitiesViewModel: FirestoreCommunitiesViewModel = viewModel()
                CommunitiesMainScreen(
                    viewModel = communitiesViewModel,
                    onNavigate = onNavigate
                )
            }
            Screen.Profesores -> {
                val profesoresViewModel: FirestoreProfesoresViewModel = viewModel()
                val logViewModel: com.uniandes.sport.viewmodels.log.FirebaseLogViewModel = viewModel()
                ProfesoresScreen(
                    profesoresViewModel = profesoresViewModel,
                    logViewModel = logViewModel,
                    onNavigate = onNavigate
                )
            }
            else -> {}
        }
    }
}

