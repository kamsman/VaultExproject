package com.vaultex.core.security

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PanicPinManager @Inject constructor(
    private val secureStorage: SecureStorage
) {
    fun setPanicPin(pin: String) {
        val hash = PBKDF2PinHasher.hashPin(pin)
        secureStorage.savePanicPin(hash)
    }

    fun verifyPanicPin(pin: String): Boolean {
        val stored = secureStorage.getPanicPinHash() ?: return false
        return PBKDF2PinHasher.verifyPin(pin, stored)
    }

    fun hasPanicPin(): Boolean = secureStorage.getPanicPinHash() != null

    fun triggerPanic() {
        secureStorage.nukeAllData()
    }
}
