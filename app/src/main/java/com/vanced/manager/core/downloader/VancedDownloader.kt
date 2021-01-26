package com.vanced.manager.core.downloader

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import com.vanced.manager.R
import com.vanced.manager.utils.*
import com.vanced.manager.utils.AppUtils.validateTheme
import com.vanced.manager.utils.AppUtils.vancedRootPkg
import com.vanced.manager.utils.DownloadHelper.download
import com.vanced.manager.utils.DownloadHelper.downloadProgress
import com.vanced.manager.utils.PackageHelper.downloadStockCheck
import com.vanced.manager.utils.PackageHelper.installVanced
import com.vanced.manager.utils.PackageHelper.installVancedRoot
import java.io.File
import java.lang.Exception

object VancedDownloader {
    
    private lateinit var prefs: SharedPreferences
    private lateinit var defPrefs: SharedPreferences
    private lateinit var arch: String
    private var installUrl: String? = null
    private var variant: String? = null
    private var theme: String? = null
    private var lang = mutableListOf<String>()

    private lateinit var themePath: String

    private var count: Int = 0
    private var succesfulLangCount: Int = 0
    private var hashUrl = ""

    private var vancedVersionCode = 0
    private var vancedVersion: String? = null

    private var downloadPath: String? = null
    private var folderName: String? = null

    fun downloadVanced(context: Context) {
        defPrefs = getDefaultSharedPreferences(context)
        prefs = context.getSharedPreferences("installPrefs", Context.MODE_PRIVATE)
        variant = defPrefs.getString("vanced_variant", "nonroot")
        folderName = "vanced/$variant"
        downloadPath = context.getExternalFilesDir(folderName)?.path
        File(downloadPath.toString()).deleteRecursively()
        installUrl = defPrefs.getInstallUrl()
        prefs.getString("lang", getDefaultVancedLanguages())?.let {
            lang = it.split(", ").toMutableList()
        }
        theme = prefs.getString("theme", "dark")
        vancedVersion = defPrefs.getString("vanced_version", "latest")?.getLatestAppVersion(vancedVersions.value?.value ?: listOf(""))
        themePath = "$installUrl/apks/v$vancedVersion/$variant/Theme"
        hashUrl = "apks/v$vancedVersion/$variant/Theme/hash.json"
        //newInstaller = defPrefs.getBoolean("new_installer", false)
        arch = getArch()
        count = 0

        vancedVersionCode = vanced.value?.int("versionCode") ?: 0
        try {
            downloadSplits(context)
        } catch (e: Exception) {
            Log.d("VMDownloader", e.stackTraceToString())
            downloadProgress.value?.downloadingFile?.postValue(context.getString(R.string.error_downloading, "Vanced"))
        }

    }

    private fun downloadSplits(context: Context, type: String = "theme") {
        val url = when (type) {
            "theme" -> "$themePath/$theme.apk"
            "arch" -> "$installUrl/apks/v$vancedVersion/$variant/Arch/split_config.$arch.apk"
            "stock" -> "$themePath/stock.apk"
            "dpi" ->  "$themePath/dpi.apk"
            "lang" -> "$installUrl/apks/v$vancedVersion/$variant/Language/split_config.${lang[count]}.apk"
            else -> throw NotImplementedError("This type of APK is NOT valid. What the hell did you even do?")
        }

        installUrl?.let {
            download(url, "$it/", folderName!!, getFileNameFromUrl(url), context, onDownloadComplete = {
                when (type) {
                    "theme" ->
                        if (variant == "root") {
                            if (validateTheme(downloadPath!!, theme!!, hashUrl, context)) {
                                if (downloadStockCheck(vancedRootPkg, vancedVersionCode, context))
                                    downloadSplits(context, "arch")
                                else
                                    startVancedInstall(context)
                            } else
                                downloadSplits(context, "theme")
                        } else
                            downloadSplits(context, "arch")
                    "arch" -> if (variant == "root") downloadSplits(context, "stock") else downloadSplits(context, "lang")
                    "stock" -> downloadSplits(context, "dpi")
                    "dpi" -> downloadSplits(context, "lang")
                    "lang" -> {
                        count++
                        succesfulLangCount++
                        if (count < lang.size)
                            downloadSplits(context, "lang")
                        else
                            startVancedInstall(context)
                    }

                }
            }, onError = {
                if (type == "lang") {
                    count++
                    when {
                        count < lang.size -> downloadSplits(context, "lang")
                        succesfulLangCount == 0 -> {
                            lang.add("en")
                            downloadSplits(context, "lang")
                        }
                        else -> startVancedInstall(context)
                    }

                } else {
                    downloadProgress.value?.downloadingFile?.postValue(context.getString(R.string.error_downloading, getFileNameFromUrl(url)))
                }
            })
        }
    }

    fun startVancedInstall(context: Context, variant: String? = this.variant) {
        downloadProgress.value?.installing?.postValue(true)
        downloadProgress.value?.postReset()
        if (variant == "root")
            installVancedRoot(context)
        else
            installVanced(context)
    }
}