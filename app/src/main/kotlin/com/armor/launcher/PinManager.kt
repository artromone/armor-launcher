package com.armor.launcher

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Secure local PIN storage.
 *
 *  - PIN never touches disk in plaintext.
 *  - Stored as PBKDF2-HMAC-SHA256(pin, salt, 50_000 iter, 256 bit).
 *  - Salt is per-PIN, random 16 bytes.
 *  - Verification is constant-time.
 *  - Failed attempts are counted; after 5 fails we start exponential lockout
 *    starting at 30 s and doubling each subsequent failure (capped).
 */
class PinManager(context: Context, prefsName: String = "armor_pin") {

    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun isSet(): Boolean = prefs.contains(KEY_HASH)

    fun pinLength(): Int = prefs.getInt(KEY_LEN, 0)

    /** Stores a new PIN (overwrites any existing). Resets failure counters. */
    fun setPin(pin: String) {
        require(pin.length in MIN_LEN..MAX_LEN) { "PIN length out of range" }
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val hash = pbkdf2(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
            .putInt(KEY_LEN, pin.length)
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .apply()
    }

    fun clearPin() {
        prefs.edit().clear().apply()
    }

    /** True if the lock-out window is still in effect. */
    fun isLockedOut(): Boolean = lockoutSecondsRemaining() > 0

    fun lockoutSecondsRemaining(): Long {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        val now = System.currentTimeMillis()
        return if (now < until) (until - now + 999) / 1000 else 0
    }

    fun failedAttempts(): Int = prefs.getInt(KEY_FAILS, 0)

    /**
     * Verifies [pin] against the stored hash. Returns true on match, false
     * on mismatch or while locked out. Updates failure counter accordingly.
     */
    fun verify(pin: String): Boolean {
        if (isLockedOut()) return false
        val saltB64 = prefs.getString(KEY_SALT, null) ?: return false
        val hashB64 = prefs.getString(KEY_HASH, null) ?: return false
        val salt = Base64.decode(saltB64, Base64.NO_WRAP)
        val expected = Base64.decode(hashB64, Base64.NO_WRAP)
        val actual = pbkdf2(pin, salt)
        val ok = constantTimeEquals(actual, expected)
        recordAttempt(ok)
        return ok
    }

    private fun recordAttempt(success: Boolean) {
        val edit = prefs.edit()
        if (success) {
            edit.putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0L)
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            edit.putInt(KEY_FAILS, fails)
            if (fails >= MAX_FAILS_BEFORE_LOCK) {
                val factor = (fails - MAX_FAILS_BEFORE_LOCK).coerceAtMost(LOCKOUT_MAX_DOUBLINGS)
                val lockoutMs = LOCKOUT_BASE_MS shl factor
                edit.putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + lockoutMs)
            }
        }
        edit.apply()
    }

    private fun pbkdf2(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LEN_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
    }

    private fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        MessageDigest.isEqual(a, b)

    companion object {
        const val MIN_LEN = 4
        const val MAX_LEN = 8

        /** Factory for the unlock-PIN (used by LockActivity). */
        fun forPin(c: Context) = PinManager(c, "armor_pin")

        /** Factory for the secret code that toggles Real mode. */
        fun forSecret(c: Context) = PinManager(c, "armor_secret")

        private const val KEY_HASH = "hash"
        private const val KEY_SALT = "salt"
        private const val KEY_LEN = "len"
        private const val KEY_FAILS = "fails"
        private const val KEY_LOCKED_UNTIL = "locked_until"

        private const val SALT_LEN = 16
        private const val ITERATIONS = 50_000
        private const val KEY_LEN_BITS = 256

        private const val MAX_FAILS_BEFORE_LOCK = 5
        private const val LOCKOUT_BASE_MS = 30_000L
        private const val LOCKOUT_MAX_DOUBLINGS = 6  // 30 s → 30 min cap
    }
}
