package com.schedule.vela.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASES_API = "https://api.github.com/repos/Jursin/Schedule-Sync/releases/latest"

object AppUpdate {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchLatestRelease(): GithubRelease =
        withContext(Dispatchers.IO) {
            val connection =
                (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 30_000
                    readTimeout = 60_000
                    requestMethod = "GET"
                    setRequestProperty("Accept", "application/vnd.github+json")
                    setRequestProperty("User-Agent", "Schedule-Sync-update")
                }
            try {
                val code = connection.responseCode
                if (code !in 200..299) error("HTTP $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                json.decodeFromString(body)
            } finally {
                connection.disconnect()
            }
        }

    fun selectAsset(
        release: GithubRelease,
        abis: List<String>,
    ): GithubAsset? {
        // 发布产物为按 ABI 拆分的安装包，如 schedule-sync-arm64-v8a-release.apk
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        for (abi in abis) {
            apks.firstOrNull { it.name.contains(abi, ignoreCase = true) }?.let { return it }
        }
        return null
    }

    fun compareVersions(
        first: String,
        second: String,
    ): Int {
        val firstParts = first.split('.')
        val secondParts = second.split('.')
        for (index in 0 until maxOf(firstParts.size, secondParts.size)) {
            val a = firstParts.getOrNull(index)?.trim()?.toIntOrNull() ?: 0
            val b = secondParts.getOrNull(index)?.trim()?.toIntOrNull() ?: 0
            if (a != b) return a - b
        }
        return 0
    }

    fun applyProxy(
        url: String,
        proxy: String,
    ): String {
        val trimmed = proxy.trim()
        if (trimmed.isEmpty()) return url
        return trimmed.trimEnd('/') + "/" + url
    }
}
