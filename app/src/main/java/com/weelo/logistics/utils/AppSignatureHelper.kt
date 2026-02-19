package com.weelo.logistics.utils

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.util.Base64
import timber.log.Timber
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Arrays

/**
 * =============================================================================
 * APP SIGNATURE HELPER — Computes SMS Retriever App Hash
 * =============================================================================
 *
 * Google SMS Retriever API requires SMS messages to contain an 11-character
 * app hash derived from the app's signing certificate. This utility computes
 * that hash.
 *
 * USAGE:
 * 1. Call AppSignatureHelper.getAppSignatures(context) once during development
 * 2. Check logcat for the hash: "📱 App Hash for SMS Retriever: XXXXXXXXXX"
 * 3. Hardcode this hash in the backend SMS template
 * 4. This class is NOT needed in production — hash is static per signing key
 *
 * The hash changes when:
 * - The signing key changes (e.g., debug vs release keystore)
 * - The package name changes
 *
 * FORMAT: 11 characters, Base64-encoded, truncated
 * =============================================================================
 */
object AppSignatureHelper {

    private const val HASH_TYPE = "SHA-256"
    private const val NUM_HASHED_BYTES = 9
    private const val NUM_BASE64_CHAR = 11

    /**
     * Get the app signatures (hash codes) for SMS Retriever.
     *
     * @param context Application context
     * @return List of 11-character hash strings (usually just one)
     */
    fun getAppSignatures(context: Context): List<String> {
        val appSignatures = mutableListOf<String>()
        val packageName = context.packageName

        try {
            val signaturesArray = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val signingInfo = context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNING_CERTIFICATES
                ).signingInfo
                if (signingInfo?.hasMultipleSigners() == true) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo?.signingCertificateHistory
                }
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.GET_SIGNATURES
                ).signatures
            }

            signaturesArray?.forEach { signature ->
                val hash = hash(packageName, signature.toCharsString())
                if (hash != null) {
                    appSignatures.add(hash)
                    Timber.i("📱 App Hash for SMS Retriever: $hash (package: $packageName)")
                }
            }
        } catch (e: PackageManager.NameNotFoundException) {
            Timber.e(e, "📱 Failed to get app signatures")
        }

        return appSignatures
    }

    /**
     * Compute the 11-character hash from package name + signature.
     */
    private fun hash(packageName: String, signature: String): String? {
        val appInfo = "$packageName $signature"
        return try {
            val messageDigest = MessageDigest.getInstance(HASH_TYPE)
            messageDigest.update(appInfo.toByteArray(StandardCharsets.UTF_8))
            var hashSignature = messageDigest.digest()

            // Truncated into NUM_HASHED_BYTES
            hashSignature = Arrays.copyOfRange(hashSignature, 0, NUM_HASHED_BYTES)

            // Encode into Base64
            var base64Hash = Base64.encodeToString(hashSignature, Base64.NO_PADDING or Base64.NO_WRAP)
            base64Hash = base64Hash.substring(0, NUM_BASE64_CHAR)

            Timber.d("📱 Hash: pkg=$packageName, hash=$base64Hash")
            base64Hash
        } catch (e: Exception) {
            Timber.e(e, "📱 Hash computation failed")
            null
        }
    }
}
