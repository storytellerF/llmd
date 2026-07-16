package com.storytellerf.llmd

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object LlmdIpcAuthorization {
    const val ACTION_AUTHORIZE_CALLER = "com.storytellerf.llmd.action.AUTHORIZE_CALLER"
    const val EXTRA_CALLER_PACKAGE = "caller_package"

    private const val PREFS = "llmd_ipc_authorizations"
    private const val DIGEST_ALGORITHM = "SHA-256"

    fun isAuthorized(context: Context, callingUid: Int): Boolean {
        val packages = context.packageManager.getPackagesForUid(callingUid).orEmpty()
        return packages.any { packageName -> isPackageAuthorized(context, packageName) }
    }

    fun authorizePackage(context: Context, packageName: String): Boolean {
        val digests = signingDigests(context, packageName)

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.edit().apply {
            if (digests.isEmpty()) {
                putBoolean(packageAuthorizationKey(packageName), true)
            } else {
                digests.forEach { digest ->
                    putBoolean(authorizationKey(packageName, digest), true)
                }
            }
        }.commit()
    }

    fun isPackageAuthorized(context: Context, packageName: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(packageAuthorizationKey(packageName), false)) return true
        return signingDigests(context, packageName)
            .any { digest -> prefs.getBoolean(authorizationKey(packageName, digest), false) }
    }

    fun callerLabel(context: Context, packageName: String): String =
        runCatching {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(packageManager).toString()
        }.getOrDefault(packageName)

    fun displayDigest(context: Context, packageName: String): String =
        signingDigests(context, packageName).firstOrNull().orEmpty()

    private fun signingDigests(context: Context, packageName: String): Set<String> =
        runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else {
                signingInfo.signingCertificateHistory
            }
            signatures.map { signature ->
                MessageDigest.getInstance(DIGEST_ALGORITHM)
                    .digest(signature.toByteArray())
                    .joinToString(":") { byte -> "%02X".format(byte.toInt() and 0xff) }
            }.toSet()
        }.getOrDefault(emptySet())

    private fun authorizationKey(packageName: String, digest: String): String =
        "$packageName|$digest"

    private fun packageAuthorizationKey(packageName: String): String =
        "$packageName|package"
}
