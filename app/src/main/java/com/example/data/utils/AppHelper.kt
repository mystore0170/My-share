package com.example.data.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.example.data.model.AppItem
import java.io.File

object AppHelper {

    fun getInstalledApps(context: Context): List<AppItem> {
        val packageManager = context.packageManager
        val installedApps = mutableListOf<AppItem>()

        val packages = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

        for (appInfo in packages) {
            // Include non-system apps, or system apps updated by user
            val isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

            // Skip current app and standard internal system processes
            if (appInfo.packageName == context.packageName) continue

            val appName = packageManager.getApplicationLabel(appInfo).toString()
            val sourceDir = appInfo.publicSourceDir ?: appInfo.sourceDir ?: continue
            val apkFile = File(sourceDir)
            if (!apkFile.exists() || apkFile.length() <= 0) continue

            val versionName = try {
                val packageInfo = packageManager.getPackageInfo(appInfo.packageName, 0)
                packageInfo.versionName ?: "1.0"
            } catch (e: Exception) {
                "1.0"
            }

            // Only add user-facing apps or user-installed packages
            if (!isSystemApp || isUpdatedSystemApp) {
                installedApps.add(
                    AppItem(
                        appName = appName,
                        packageName = appInfo.packageName,
                        apkPath = sourceDir,
                        sizeBytes = apkFile.length(),
                        versionName = versionName
                    )
                )
            }
        }

        return installedApps.sortedBy { it.appName.lowercase() }
    }
}
