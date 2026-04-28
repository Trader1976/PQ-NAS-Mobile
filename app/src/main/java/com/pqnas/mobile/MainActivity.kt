package com.pqnas.mobile

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.pqnas.mobile.auth.AuthRepository
import com.pqnas.mobile.auth.PairQrPayload
import com.pqnas.mobile.auth.TokenStore
import com.pqnas.mobile.files.FilesRepository
import com.pqnas.mobile.security.AppUnlockPolicy
import com.pqnas.mobile.ui.screens.AppLockScreen
import com.pqnas.mobile.ui.screens.FilesScreen
import com.pqnas.mobile.ui.screens.PairConfirmScreen
import com.pqnas.mobile.ui.screens.ScanPairQrScreen
import com.pqnas.mobile.ui.screens.ServerSetupScreen
import com.pqnas.mobile.ui.theme.PQNASTheme
import kotlinx.coroutines.launch
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PQNASTheme {
                val context = LocalContext.current
                val tokenStore = remember { TokenStore(context) }
                val lifecycleOwner = LocalLifecycleOwner.current
                val authRepository = remember { AuthRepository(tokenStore) }
                val scope = rememberCoroutineScope()

                var screen by remember { mutableStateOf("server") }
                var baseUrl by remember { mutableStateOf("") }
                var pairPayload by remember { mutableStateOf<PairQrPayload?>(null) }
                var authLoaded by remember { mutableStateOf(false) }

                var appUnlocked by remember { mutableStateOf(false) }
                var appLockStatus by remember { mutableStateOf("") }
                var unlockPromptActive by remember { mutableStateOf(false) }

                // Android file picker temporarily moves our app through onStop().
                // Do not lock the app for that intentional external picker handoff.
                val suppressNextAppLock = remember { mutableStateOf(false) }
                DisposableEffect(lifecycleOwner, authLoaded, screen) {
                    val observer = object : DefaultLifecycleObserver {
                        override fun onStop(owner: LifecycleOwner) {
                            if (authLoaded && screen == "files") {
                                if (suppressNextAppLock.value) {
                                    suppressNextAppLock.value = false
                                    return
                                }

                                appUnlocked = false
                                unlockPromptActive = false
                                appLockStatus = ""
                            }
                        }
                    }

                    lifecycleOwner.lifecycle.addObserver(observer)

                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }
                fun logoutToServerScreen() {
                    scope.launch {
                        authRepository.logout()
                        baseUrl = ""
                        pairPayload = null
                        appUnlocked = false
                        appLockStatus = ""
                        screen = "server"
                    }
                }

                fun requestAppUnlock(force: Boolean = false) {
                    if (appUnlocked) return
                    if (unlockPromptActive && !force) return

                    val authenticators = AppUnlockPolicy.allowedAuthenticators()
                    val canAuthenticate = BiometricManager
                        .from(this@MainActivity)
                        .canAuthenticate(authenticators)

                    if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
                        appLockStatus = when (canAuthenticate) {
                            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                                "This device does not have biometric hardware. Use Logout or configure device security."
                            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                                "Biometric hardware is temporarily unavailable."
                            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                                "No biometric credential is enrolled. Add fingerprint/face unlock in Android settings."
                            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                                "A security update is required before biometric unlock can be used."
                            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
                                "This unlock method is not supported on this Android version."
                            BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
                                "Unlock status is unknown. Try again."
                            else ->
                                "App unlock is not available on this device."
                        }
                        return
                    }

                    unlockPromptActive = true
                    appLockStatus = "Waiting for authentication..."

                    val executor = ContextCompat.getMainExecutor(this@MainActivity)

                    val prompt = BiometricPrompt(
                        this@MainActivity,
                        executor,
                        object : BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationSucceeded(
                                result: BiometricPrompt.AuthenticationResult
                            ) {
                                unlockPromptActive = false
                                appUnlocked = true
                                appLockStatus = ""
                            }

                            override fun onAuthenticationError(
                                errorCode: Int,
                                errString: CharSequence
                            ) {
                                unlockPromptActive = false
                                appLockStatus = "Unlock cancelled or failed: $errString"
                            }

                            override fun onAuthenticationFailed() {
                                appLockStatus = "Authentication failed. Try again."
                            }
                        }
                    )

                    val promptBuilder = BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Unlock DNA-Nexus")
                        .setSubtitle("Confirm it is you before opening your files")
                        .setAllowedAuthenticators(authenticators)

                    if (!AppUnlockPolicy.allowsDeviceCredential(authenticators)) {
                        promptBuilder.setNegativeButtonText("Cancel")
                    }

                    prompt.authenticate(promptBuilder.build())
                }

                LaunchedEffect(Unit) {
                    val state = tokenStore.getAuthStateOnce()
                    baseUrl = state.baseUrl
                    screen = if (state.isLoggedIn) "files" else "server"
                    authLoaded = true
                }

                LaunchedEffect(authLoaded, screen, appUnlocked) {
                    if (authLoaded && screen == "files" && !appUnlocked) {
                        requestAppUnlock()
                    }
                }
                LaunchedEffect(unlockPromptActive, appUnlocked) {
                    if (unlockPromptActive && !appUnlocked) {
                        kotlinx.coroutines.delay(30_000L)

                        if (unlockPromptActive && !appUnlocked) {
                            unlockPromptActive = false
                            appLockStatus = "Unlock timed out. Tap Unlock to try again."
                        }
                    }
                }
                when (screen) {
                    "server" -> ServerSetupScreen(
                        onScanPair = { url ->
                            scope.launch {
                                tokenStore.saveBaseUrl(url)
                                baseUrl = url
                                appUnlocked = false
                                screen = "scan_pair"
                            }
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
                                configuredBaseUrl = baseUrl,
                                authRepository = authRepository,
                                onPaired = {
                                    scope.launch {
                                        val s = tokenStore.getAuthStateOnce()
                                        baseUrl = s.baseUrl

                                        // Pairing just completed successfully, so do not immediately
                                        // force a second unlock prompt in the same foreground session.
                                        appUnlocked = true
                                        appLockStatus = ""
                                        screen = "files"
                                    }
                                },
                                onBack = {
                                    screen = "scan_pair"
                                }
                            )
                        }
                    }

                    "files" -> {
                        if (!appUnlocked) {
                            AppLockScreen(
                                status = appLockStatus,
                                onUnlock = {
                                    requestAppUnlock(force = true)
                                },
                                onLogout = {
                                    logoutToServerScreen()
                                }
                            )
                        } else {
                            val filesRepository = remember(tokenStore, baseUrl) {
                                FilesRepository(
                                    tokenStore = tokenStore,
                                    baseUrlProvider = { baseUrl }
                                )
                            }

                            FilesScreen(
                                filesRepository = filesRepository,
                                onLogout = {
                                    logoutToServerScreen()
                                },
                                onBeforeExternalPicker = {
                                    suppressNextAppLock.value = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}