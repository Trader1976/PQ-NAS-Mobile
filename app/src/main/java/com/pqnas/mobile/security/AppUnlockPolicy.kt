package com.pqnas.mobile.security

import android.os.Build
import androidx.biometric.BiometricManager

object AppUnlockPolicy {
    fun allowedAuthenticators(): Int {
        return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            // AndroidX Biometric does not support BIOMETRIC_STRONG | DEVICE_CREDENTIAL
            // on Android 9-10. Use strong biometric only there.
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        } else {
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }
    }

    fun allowsDeviceCredential(authenticators: Int): Boolean {
        return authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL != 0
    }
}