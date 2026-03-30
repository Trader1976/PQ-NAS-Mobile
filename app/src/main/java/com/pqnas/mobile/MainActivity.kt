package com.pqnas.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.pqnas.mobile.auth.AuthRepository
import com.pqnas.mobile.auth.PairQrPayload
import com.pqnas.mobile.auth.TokenStore
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.ui.screens.FilesScreen
import com.pqnas.mobile.ui.screens.PairConfirmScreen
import com.pqnas.mobile.ui.screens.ScanPairQrScreen
import com.pqnas.mobile.ui.screens.ServerSetupScreen
import com.pqnas.mobile.ui.theme.PQNASTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PQNASTheme {
                val context = LocalContext.current
                val tokenStore = remember { TokenStore(context) }
                val authRepository = remember { AuthRepository(tokenStore) }

                var screen by remember { mutableStateOf("server") }
                var baseUrl by remember { mutableStateOf("") }
                var pairPayload by remember { mutableStateOf<PairQrPayload?>(null) }

                LaunchedEffect(Unit) {
                    val state = tokenStore.authState.first()
                    baseUrl = state.baseUrl
                    screen = if (state.isLoggedIn) "files" else "server"
                }

                when (screen) {
                    "server" -> ServerSetupScreen(
                        onScanPair = { url ->
                            runBlocking { tokenStore.saveBaseUrl(url) }
                            baseUrl = url
                            screen = "scan_pair"
                        }
                    )

                    "scan_pair" -> ScanPairQrScreen(
                        onParsed = { payload ->
                            pairPayload = payload
                            screen = "pair_confirm"
                        },
                        onBack = {
                            screen = "server"
                        }
                    )

                    "pair_confirm" -> {
                        val payload = pairPayload
                        if (payload == null) {
                            screen = "server"
                        } else {
                            PairConfirmScreen(
                                payload = payload,
                                authRepository = authRepository,
                                onPaired = {
                                    runBlocking {
                                        val s = tokenStore.authState.first()
                                        baseUrl = s.baseUrl
                                    }
                                    screen = "files"
                                },
                                onBack = {
                                    screen = "scan_pair"
                                }
                            )
                        }
                    }

                    "files" -> {
                        val filesRepository = remember(tokenStore, baseUrl) {
                            FilesRepository(
                                tokenStore = tokenStore,
                                baseUrlProvider = { baseUrl }
                            )
                        }
                        FilesScreen(
                            filesRepository = filesRepository,
                            onLogout = {
                                runBlocking {
                                    tokenStore.clearAll()
                                }
                                baseUrl = ""
                                screen = "server"
                            }
                        )
                    }
                }
            }
        }
    }
}