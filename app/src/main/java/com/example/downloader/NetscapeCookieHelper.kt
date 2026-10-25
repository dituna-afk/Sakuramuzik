package com.example.downloader

import android.content.Context
import android.webkit.CookieManager
import com.example.data.CookieStorageHelper
import com.example.model.CookieItem
import com.example.model.CookieParser
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Morphe Patch CookieBridge + Netscape Cookie Helper:
 * WebView CookieManager, şifreli CookieStorageHelper ve ytdlnis (cookies_netscape.txt)
 * arasında çift yönlü (bi-directional) senkronizasyon ve SAPISIDHASH doğrulaması sağlar.
 */
object NetscapeCookieHelper {

    private const val YT_MUSIC_URL = "https://music.youtube.com"
    private const val YT_MAIN_URL = "https://www.youtube.com"
    private const val YT_MOBILE_URL = "https://m.youtube.com"

    private val YOUTUBE_COOKIE_URLS = listOf(
        YT_MUSIC_URL,
        YT_MAIN_URL,
        YT_MOBILE_URL,
        "https://youtube.com",
        "https://accounts.google.com"
    )

    private val AUTH_COOKIE_NAMES = setOf(
        "SAPISID",
        "__Secure-3PAPISID",
        "__Secure-1PAPISID",
        "SID",
        "__Secure-1PSID",
        "__Secure-3PSID",
        "HSID",
        "SSID",
        "APISID",
        "LOGIN_INFO",
        "__Secure-1PSIDTS",
        "__Secure-3PSIDTS"
    )

    /**
     * Morphe CookieBridge.getCookies() + Şifreli Depo + WebView birleşimi:
     * Hem CookieStorageHelper'daki kayıtlı çerezleri hem de WebView CookieManager içindeki
     * canlı çerezleri birleştirir ve eksik olanları music.youtube.com'a senkronize eder.
     */
    fun resolveActiveCookies(
        context: Context,
        explicitCookies: List<CookieItem> = emptyList()
    ): List<CookieItem> {
        val mergedByKey = LinkedHashMap<String, CookieItem>()

        fun putAllCookies(list: List<CookieItem>) {
            for (item in list) {
                val cleanName = item.name.trim()
                val cleanValue = item.value.trim()
                if (cleanName.isEmpty() || cleanValue.isEmpty()) continue
                mergedByKey[cleanName] = item.copy(
                    name = cleanName,
                    value = cleanValue,
                    domain = item.domain.trim().ifBlank { ".youtube.com" },
                    path = item.path.trim().ifBlank { "/" }
                )
            }
        }

        // 1. Şifreli depodaki kayıtlı çerezler
        try {
            val (_, saved) = CookieStorageHelper(context.applicationContext).loadCookies()
            putAllCookies(saved)
        } catch (_: Throwable) {
        }

        // 2. Parametre olarak gelen çerezler
        putAllCookies(explicitCookies)

        // 3. WebView CookieManager içindeki canlı çerezler (music.youtube.com, www.youtube.com vb.)
        var harvestedFromWebView = false
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            for (url in YOUTUBE_COOKIE_URLS) {
                val raw = try {
                    cookieManager.getCookie(url)
                } catch (_: Throwable) {
                    null
                }
                if (!raw.isNullOrBlank()) {
                    val parsed = CookieParser.parseRawCookies(raw, url)
                    if (parsed.isNotEmpty()) {
                        harvestedFromWebView = true
                        putAllCookies(parsed)
                    }
                }
            }
        } catch (_: Throwable) {
        }

        val finalCookies = mergedByKey.values.toList()
        if (finalCookies.isNotEmpty()) {
            // Morphe CookieBridge: Çerezleri her zaman music.youtube.com ve www.youtube.com CookieManager'ına da yaz
            syncCookiesToWebViewCookieManager(finalCookies)
            if (harvestedFromWebView || explicitCookies.isNotEmpty()) {
                try {
                    CookieStorageHelper(context.applicationContext).saveCookies(
                        YT_MUSIC_URL,
                        finalCookies
                    )
                } catch (_: Throwable) {
                }
            }
        }
        return finalCookies
    }

    /**
     * Morphe CookieBridge.injectCookies(rawCookies) karşılığı:
     * Kullanıcının yapıştırdığı ham Cookie başlığını (name=val; name2=val2), Netscape formatını
     * veya JSON çerez listesini ayrıştırır, WebView CookieManager + CookieStorageHelper + Netscape dosyasına yazar.
     */
    fun injectRawCookies(
        context: Context,
        rawCookieInput: String
    ): List<CookieItem> {
        val trimmed = rawCookieInput.trim()
        if (trimmed.isEmpty()) return emptyList()

        val parsedItems = mutableListOf<CookieItem>()

        // Durum A: JSON formatı
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            try {
                val arr = if (trimmed.startsWith("{")) {
                    JSONObject(trimmed).optJSONArray("cookies") ?: JSONArray()
                } else {
                    JSONArray(trimmed)
                }
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val name = obj.optString("name", "").trim()
                    val value = obj.optString("value", "").trim()
                    val domain = obj.optString("domain", ".youtube.com").trim()
                    val path = obj.optString("path", "/").trim()
                    if (name.isNotEmpty() && value.isNotEmpty()) {
                        parsedItems.add(
                            CookieItem(
                                name = name,
                                value = value,
                                domain = domain.ifEmpty { ".youtube.com" },
                                path = path.ifEmpty { "/" },
                                isSecure = true
                            )
                        )
                    }
                }
            } catch (_: Throwable) {
            }
        }

        // Durum B: Netscape HTTP Cookie File formatı (7 sütunlu tab ayrılmış) veya satır satır
        if (parsedItems.isEmpty() && (trimmed.contains("\t") || trimmed.contains("# Netscape"))) {
            for (line in trimmed.lines()) {
                val cleanLine = line.trim().removePrefix("#HttpOnly_")
                if (cleanLine.isEmpty() || cleanLine.startsWith("#")) continue
                val parts = cleanLine.split('\t')
                if (parts.size >= 7) {
                    val domain = parts[0].trim().ifEmpty { ".youtube.com" }
                    val path = parts[2].trim().ifEmpty { "/" }
                    val secure = parts[3].trim().equals("TRUE", ignoreCase = true)
                    val name = parts[5].trim()
                    val value = parts[6].trim()
                    if (name.isNotEmpty() && value.isNotEmpty()) {
                        parsedItems.add(
                            CookieItem(
                                name = name,
                                value = value,
                                domain = domain,
                                path = path,
                                isSecure = secure
                            )
                        )
                    }
                }
            }
        }

        // Durum C: Standart "Cookie: SAPISID=...; SID=..." veya "SAPISID=...; __Secure-3PAPISID=..."
        if (parsedItems.isEmpty()) {
            val cleanedHeader = trimmed
                .removePrefix("Cookie:")
                .removePrefix("cookie:")
                .replace("\r", ";")
                .replace("\n", ";")
                .trim()
            val pairs = cleanedHeader.split(";")
            for (pair in pairs) {
                val p = pair.trim()
                if (p.isEmpty()) continue
                val eqIdx = p.indexOf('=')
                if (eqIdx <= 0) continue
                val name = p.substring(0, eqIdx).trim()
                val value = p.substring(eqIdx + 1).trim()
                if (name.equals("Domain", ignoreCase = true) ||
                    name.equals("Path", ignoreCase = true) ||
                    name.equals("Expires", ignoreCase = true) ||
                    name.equals("Max-Age", ignoreCase = true) ||
                    name.equals("SameSite", ignoreCase = true)
                ) {
                    continue
                }
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    parsedItems.add(
                        CookieItem(
                            name = name,
                            value = value,
                            domain = ".youtube.com",
                            path = "/",
                            isSecure = true
                        )
                    )
                }
            }
        }

        if (parsedItems.isEmpty()) return emptyList()

        syncCookiesToWebViewCookieManager(parsedItems)
        val allMerged = resolveActiveCookies(context.applicationContext, parsedItems)
        try {
            CookieStorageHelper(context.applicationContext).saveCookies(YT_MUSIC_URL, allMerged)
            writeNetscapeCookieFile(context.applicationContext, allMerged)
        } catch (_: Throwable) {
        }
        return allMerged
    }

    private fun syncCookiesToWebViewCookieManager(cookies: List<CookieItem>) {
        if (cookies.isEmpty()) return
        try {
            val cm = CookieManager.getInstance()
            cm.setAcceptCookie(true)
            for (c in cookies) {
                val n = c.name.trim()
                val v = c.value.trim()
                if (n.isEmpty() || v.isEmpty()) continue
                val cookieLine = "$n=$v; Domain=.youtube.com; Path=/; Secure"
                cm.setCookie(YT_MUSIC_URL, cookieLine)
                cm.setCookie(YT_MAIN_URL, cookieLine)
            }
            cm.flush()
        } catch (_: Throwable) {
        }
    }

    /**
     * Morphe CookieBridge.hasCookies() & Auth kontrolü:
     * Hem CookieManager hem de kayıtlı çerezlerde geçerli YouTube oturum çerezi olup olmadığını döner.
     */
    fun hasYouTubeAuthCookies(cookies: List<CookieItem>): Boolean {
        if (cookies.any { it.name.trim() in AUTH_COOKIE_NAMES && it.value.isNotBlank() }) {
            return true
        }
        return try {
            val rawMusic = CookieManager.getInstance().getCookie(YT_MUSIC_URL).orEmpty()
            val rawMain = CookieManager.getInstance().getCookie(YT_MAIN_URL).orEmpty()
            val combined = "$rawMusic; $rawMain"
            combined.contains("SAPISID=") ||
                combined.contains("__Secure-3PAPISID=") ||
                combined.contains("__Secure-1PAPISID=") ||
                combined.contains("__Secure-3PSID=") ||
                combined.contains("__Secure-1PSID=") ||
                combined.contains("SID=") ||
                combined.contains("LOGIN_INFO=")
        } catch (_: Throwable) {
            false
        }
    }

    data class CookieImportResult(
        val isSuccess: Boolean,
        val isSessionVerified: Boolean,
        val importedCookies: List<CookieItem>,
        val accountName: String?,
        val message: String
    )

    /**
     * Saf Cookie Import (Çerez Yapıştırma & Doğrulama) Servisi:
     * 1) Dış tarayıcıdan alınan `cookies.txt` (Netscape), JSON veya ham Cookie header dizgesini ayrıştırır.
     * 2) Doğrudan Android `CookieManager` içine (`https://music.youtube.com` ve `https://www.youtube.com`) yazar ve flush eder.
     * 3) `SAPISIDHASH` üreterek `/youtubei/v1/account/account_menu` endpoint'inde oturumu doğrular.
     */
    suspend fun importAndVerifyCookies(
        context: Context,
        rawCookieInput: String
    ): CookieImportResult = withContext(Dispatchers.IO) {
        val injected = injectRawCookies(context, rawCookieInput)
        if (injected.isEmpty()) {
            return@withContext CookieImportResult(
                isSuccess = false,
                isSessionVerified = false,
                importedCookies = emptyList(),
                accountName = null,
                message = "Geçerli bir çerez formatı ayrıştırılamadı. Lütfen Netscape cookies.txt veya 'SAPISID=...; __Secure-1PSID=...' metni yapıştırın."
            )
        }

        val (verified, statusMsg, accountName) = validateSessionWithInnerTubeDetailed(context, injected)
        CookieImportResult(
            isSuccess = true,
            isSessionVerified = verified,
            importedCookies = injected,
            accountName = accountName,
            message = statusMsg
        )
    }

    /**
     * Mevcut çerezleri `https://music.youtube.com/youtubei/v1/account/account_menu` endpoint'ine
     * `SAPISIDHASH` ve `WEB_REMIX` (Client ID: 67) ile göndererek oturumun gerçekten aktif olup olmadığını doğrular.
     */
    suspend fun validateSessionWithInnerTube(
        context: Context,
        explicitCookies: List<CookieItem> = emptyList()
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val (valid, msg, _) = validateSessionWithInnerTubeDetailed(context, explicitCookies)
        valid to msg
    }

    suspend fun validateSessionWithInnerTubeDetailed(
        context: Context,
        explicitCookies: List<CookieItem> = emptyList()
    ): Triple<Boolean, String, String?> = withContext(Dispatchers.IO) {
        val active = resolveActiveCookies(context, explicitCookies)
        val cookieHeader = buildCookieHeaderString(active, context)
        if (cookieHeader.isNullOrBlank()) {
            return@withContext Triple(
                false,
                "Henüz hiç çerez bulunamadı. Lütfen dış tarayıcıdan aldığınız cookies.txt veya Cookie metnini yapıştırın.",
                null
            )
        }
        val sapisidHash = buildSapisidHashHeader(active, YT_MUSIC_URL, context)
        if (sapisidHash.isNullOrBlank()) {
            return@withContext Triple(
                false,
                "SAPISID / __Secure-3PAPISID oturum çerezi eksik! Lütfen dış tarayıcıdan tam oturum çerezlerini (SAPISID, __Secure-1PSID) yapıştırın.",
                null
            )
        }

        return@withContext try {
            val conn = (URL(DownloadEngine.YT_MUSIC_ACCOUNT_MENU_ENDPOINT)
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 12_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("Cookie", cookieHeader)
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                )
                setRequestProperty("Origin", YT_MUSIC_URL)
                setRequestProperty("Referer", "$YT_MUSIC_URL/")
                setRequestProperty("X-Origin", YT_MUSIC_URL)
                setRequestProperty("X-Goog-AuthUser", "0")
                setRequestProperty("X-YouTube-Client-Name", DownloadEngine.WEB_REMIX_CLIENT_ID)
                setRequestProperty("X-YouTube-Client-Version", DownloadEngine.WEB_REMIX_CLIENT_VERSION)
                setRequestProperty("Authorization", sapisidHash)
            }

            val body = JSONObject().apply {
                put(
                    "context",
                    JSONObject().put(
                        "client",
                        JSONObject().apply {
                            put("clientName", DownloadEngine.WEB_REMIX_CLIENT_NAME)
                            put("clientVersion", DownloadEngine.WEB_REMIX_CLIENT_VERSION)
                            put("hl", "tr")
                            put("gl", "TR")
                        }
                    )
                )
            }.toString()

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
                writer.flush()
            }

            val code = conn.responseCode
            if (code == 200) {
                val resp = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()
                val valid = resp.contains("activeAccountHeaderRenderer") ||
                    resp.contains("accountName") ||
                    resp.contains("manageAccountTitle")
                val accountName = extractAccountNameFromMenuJson(resp)
                if (valid) {
                    val accountLabel = if (!accountName.isNullOrBlank()) " ($accountName)" else ""
                    Triple(
                        true,
                        "YouTube Music oturumu /account/account_menu ile doğrulandı$accountLabel! Beğenilen Şarkılar (VLLM) hazır.",
                        accountName
                    )
                } else {
                    Triple(
                        false,
                        "Çerezler CookieManager'a yazıldı (${active.size} adet) ancak /account/account_menu aktif oturum döndürmedi. __Secure-1PSID ve SAPISID çerezlerinin güncel olduğundan emin olun.",
                        null
                    )
                }
            } else {
                conn.disconnect()
                Triple(
                    false,
                    "Sunucu doğrulama yanıtı: HTTP $code. Çerezlerin süresi dolmuş olabilir.",
                    null
                )
            }
        } catch (e: Exception) {
            Triple(
                false,
                "Ağ doğrulaması sırasında hata oluştu (${e.localizedMessage ?: "Bağlantı hatası"}). Çerezler (${active.size} adet) yerel CookieManager'a kaydedildi.",
                null
            )
        }
    }

    private fun extractAccountNameFromMenuJson(rawJson: String): String? {
        return try {
            val regex = Regex(""""accountName"\s*:\s*\{\s*"runs"\s*:\s*\[\s*\{\s*"text"\s*:\s*"([^"]+)"""")
            regex.find(rawJson)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    fun writeNetscapeCookieFile(
        context: Context,
        cookies: List<CookieItem> = emptyList()
    ): File? {
        val activeCookies = resolveActiveCookies(context, cookies)
        if (activeCookies.isEmpty()) return null

        return try {
            val cookieDir = File(context.cacheDir, "ytdl_cookies").apply { mkdirs() }
            val cookieFile = File(cookieDir, "cookies_netscape.txt")
            val expirationEpoch = (System.currentTimeMillis() / 1000L) + 31_536_000L

            val sb = StringBuilder()
            sb.appendLine("# Netscape HTTP Cookie File")
            sb.appendLine("# https://curl.se/docs/http-cookies.html")
            sb.appendLine("# This is a generated file! Do not edit.")
            sb.appendLine()

            val writtenEntries = HashSet<String>()

            fun appendNetscapeRow(
                domain: String,
                path: String,
                isSecure: Boolean,
                name: String,
                value: String
            ) {
                val cleanName = name.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
                val cleanVal = value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim()
                if (cleanName.isEmpty()) return

                val normalizedDomain = if (domain.startsWith(".")) domain else ".$domain"
                val dedupKey = "$normalizedDomain|$path|$cleanName"
                if (!writtenEntries.add(dedupKey)) return

                val secureFlag = if (isSecure || cleanName.startsWith("__Secure-") || cleanName.startsWith("__Host-")) {
                    "TRUE"
                } else {
                    "FALSE"
                }

                sb.append(normalizedDomain).append('\t')
                    .append("TRUE").append('\t')
                    .append(path.ifBlank { "/" }).append('\t')
                    .append(secureFlag).append('\t')
                    .append(expirationEpoch).append('\t')
                    .append(cleanName).append('\t')
                    .append(cleanVal).appendLine()
            }

            for (cookie in activeCookies) {
                val name = cookie.name.trim()
                val value = cookie.value.trim()
                if (name.isEmpty()) continue

                val rawDomain = cookie.domain.trim().lowercase(Locale.US).removePrefix(".")
                val path = cookie.path.trim().ifEmpty { "/" }

                if (rawDomain.isEmpty() ||
                    rawDomain.contains("youtube.com") ||
                    rawDomain.contains("youtu.be")
                ) {
                    appendNetscapeRow(".youtube.com", path, cookie.isSecure, name, value)
                    appendNetscapeRow(".music.youtube.com", path, cookie.isSecure, name, value)
                } else if (rawDomain.contains("google.com")) {
                    appendNetscapeRow(".google.com", path, cookie.isSecure, name, value)
                    appendNetscapeRow(".youtube.com", path, cookie.isSecure, name, value)
                    appendNetscapeRow(".music.youtube.com", path, cookie.isSecure, name, value)
                } else {
                    appendNetscapeRow(".$rawDomain", path, cookie.isSecure, name, value)
                }
            }

            cookieFile.writeText(sb.toString(), Charsets.UTF_8)
            if (cookieFile.exists() && cookieFile.length() > 0L) cookieFile else null
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Morphe CookieBridge.getCookies() uyumlu Cookie header oluşturucu:
     * Hem aktif CookieItem listesini hem de WebView CookieManager'daki music.youtube.com
     * çerezlerini birleştirerek tek bir "name=value; name2=value2" dizgesi üretir.
     */
    fun buildCookieHeaderString(
        cookies: List<CookieItem>,
        context: Context? = null
    ): String? {
        val source = if (context != null) {
            resolveActiveCookies(context, cookies)
        } else {
            cookies
        }
        val uniqueByName = LinkedHashMap<String, String>()

        // Önce WebView CookieManager'daki doğrudan music.youtube.com ve www.youtube.com çerezlerini oku
        try {
            val cm = CookieManager.getInstance()
            for (u in listOf(YT_MAIN_URL, YT_MUSIC_URL)) {
                val raw = cm.getCookie(u)
                if (!raw.isNullOrBlank()) {
                    for (part in raw.split(";")) {
                        val trimmed = part.trim()
                        val eq = trimmed.indexOf('=')
                        if (eq > 0) {
                            val k = trimmed.substring(0, eq).trim()
                            val v = trimmed.substring(eq + 1).trim()
                            if (k.isNotEmpty() && v.isNotEmpty()) {
                                uniqueByName[k] = v
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        for (c in source) {
            val n = c.name.trim()
            val v = c.value.trim()
            if (n.isNotEmpty() && v.isNotEmpty()) {
                uniqueByName[n] = v
            }
        }
        if (uniqueByName.isEmpty()) return null
        return uniqueByName.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    /**
     * Morphe LikedSongsDownloader.extractSapisid + generateSapisidHash birebir karşılığı:
     * SAPISID, __Secure-3PAPISID veya __Secure-1PAPISID değerini hem listeden hem de
     * CookieManager'dan arar ve "SAPISIDHASH <timestamp>_<sha1>" başlığını üretir.
     */
    fun buildSapisidHashHeader(
        cookies: List<CookieItem>,
        origin: String = YT_MUSIC_URL,
        context: Context? = null
    ): String? {
        val activeCookies = if (cookies.isNotEmpty() || context == null) {
            cookies
        } else {
            resolveActiveCookies(context)
        }
        var sapisid = activeCookies.firstOrNull { it.name.equals("SAPISID", ignoreCase = true) && it.value.isNotBlank() }?.value
            ?: activeCookies.firstOrNull { it.name.equals("__Secure-3PAPISID", ignoreCase = true) && it.value.isNotBlank() }?.value
            ?: activeCookies.firstOrNull { it.name.equals("__Secure-1PAPISID", ignoreCase = true) && it.value.isNotBlank() }?.value

        if (sapisid.isNullOrBlank()) {
            try {
                val raw = CookieManager.getInstance().getCookie(YT_MUSIC_URL).orEmpty() + "; " +
                    CookieManager.getInstance().getCookie(YT_MAIN_URL).orEmpty()
                for (part in raw.split(";")) {
                    val kv = part.trim().split("=", limit = 2)
                    if (kv.size == 2) {
                        val k = kv[0].trim()
                        val v = kv[1].trim()
                        if ((k == "SAPISID" || k == "__Secure-3PAPISID" || k == "__Secure-1PAPISID") && v.isNotEmpty()) {
                            sapisid = v
                            break
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        if (sapisid.isNullOrBlank()) return null

        return try {
            val timestamp = System.currentTimeMillis() / 1000L
            val input = "$timestamp ${sapisid.trim()} $origin"
            val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
            val sha1Hex = digest.joinToString("") { "%02x".format(it) }
            "SAPISIDHASH ${timestamp}_$sha1Hex"
        } catch (_: Throwable) {
            null
        }
    }
}
