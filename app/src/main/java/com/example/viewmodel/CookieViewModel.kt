package com.example.viewmodel

import android.app.Application
import android.webkit.CookieManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CookieStorageHelper
import com.example.downloader.NetscapeCookieHelper
import com.example.model.CookieItem
import com.example.model.CookieLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CookieUiState(
    val currentUrl: String = "https://music.youtube.com",
    val pageTitle: String = "YouTube Music Cookie Import",
    val isLoading: Boolean = false,
    val cookies: List<CookieItem> = emptyList(),
    val searchQuery: String = "",
    val logEntries: List<CookieLogEntry> = emptyList(),
    val statusMessage: String? = null,
    val isSessionValidated: Boolean = false,
    val isValidatingSession: Boolean = false,
    val verifiedAccountName: String? = null
) {
    val filteredCookies: List<CookieItem>
        get() {
            if (searchQuery.isBlank()) return cookies
            val q = searchQuery.trim().lowercase()
            return cookies.filter {
                it.name.lowercase().contains(q) ||
                    it.value.lowercase().contains(q) ||
                    it.domain.lowercase().contains(q)
            }
        }
}

class CookieViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val storageHelper = CookieStorageHelper(appContext)
    private val cookieManager: CookieManager? by lazy {
        runCatching { CookieManager.getInstance() }.getOrNull()
    }

    private val _uiState = MutableStateFlow(CookieUiState())
    val uiState: StateFlow<CookieUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cookieManager?.setAcceptCookie(true)
            } catch (_: Throwable) {
            }
            val (savedUrl, savedCookies) = runCatching { storageHelper.loadCookies() }
                .getOrElse { "" to emptyList() }
            val activeCookies = runCatching {
                NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
            }.getOrDefault(savedCookies)
            if (activeCookies.isNotEmpty()) {
                runCatching { NetscapeCookieHelper.writeNetscapeCookieFile(appContext, activeCookies) }
                val hasAuth = NetscapeCookieHelper.hasYouTubeAuthCookies(activeCookies)
                _uiState.update {
                    it.copy(
                        currentUrl = savedUrl.ifBlank { it.currentUrl },
                        cookies = activeCookies,
                        isSessionValidated = hasAuth
                    )
                }
            }
        }
    }

    /**
     * Dış tarayıcıdan alınan cookies.txt (Netscape), JSON veya ham Cookie başlığını
     * doğrudan Android CookieManager'a yazar, SAPISIDHASH üretir ve
     * `https://music.youtube.com/youtubei/v1/account/account_menu` üzerinden doğrular.
     */
    fun injectRawCookieText(rawText: String) {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) {
            _uiState.update { it.copy(statusMessage = "Lütfen yapıştırılacak cookies.txt veya Cookie başlığı metnini girin.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isValidatingSession = true,
                    statusMessage = "Çerezler Android CookieManager'a yazılıyor ve SAPISIDHASH ile /account/account_menu doğrulanıyor..."
                )
            }
            val importResult = NetscapeCookieHelper.importAndVerifyCookies(appContext, trimmed)
            if (!importResult.isSuccess || importResult.importedCookies.isEmpty()) {
                _uiState.update {
                    it.copy(
                        isValidatingSession = false,
                        statusMessage = importResult.message
                    )
                }
                return@launch
            }

            val rawHeader = NetscapeCookieHelper.buildCookieHeaderString(importResult.importedCookies, appContext).orEmpty()
            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val logEntry = CookieLogEntry(
                timestamp = timeStr,
                url = "https://music.youtube.com/youtubei/v1/account/account_menu",
                rawCookieHeader = rawHeader,
                cookieCount = importResult.importedCookies.size
            )

            _uiState.update { state ->
                state.copy(
                    cookies = importResult.importedCookies,
                    isValidatingSession = false,
                    isSessionValidated = importResult.isSessionVerified ||
                        NetscapeCookieHelper.hasYouTubeAuthCookies(importResult.importedCookies),
                    verifiedAccountName = importResult.accountName ?: state.verifiedAccountName,
                    statusMessage = importResult.message,
                    logEntries = (listOf(logEntry) + state.logEntries).take(40)
                )
            }
        }
    }

    fun validateCurrentSessionWithInnerTube() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isValidatingSession = true,
                    statusMessage = "SAPISIDHASH üretiliyor ve /account/account_menu doğrulanıyor..."
                )
            }
            val merged = NetscapeCookieHelper.resolveActiveCookies(appContext, _uiState.value.cookies)
            val (valid, msg, accountName) = NetscapeCookieHelper.validateSessionWithInnerTubeDetailed(appContext, merged)
            _uiState.update {
                it.copy(
                    cookies = merged,
                    isValidatingSession = false,
                    isSessionValidated = valid,
                    verifiedAccountName = accountName ?: it.verifiedAccountName,
                    statusMessage = msg
                )
            }
        }
    }

    fun refreshCookiesForCurrentUrl() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                cookieManager?.flush()
            } catch (_: Throwable) {
            }
            val allMerged = NetscapeCookieHelper.resolveActiveCookies(appContext, _uiState.value.cookies)
            if (allMerged.isNotEmpty()) {
                storageHelper.saveCookies("https://music.youtube.com", allMerged)
                NetscapeCookieHelper.writeNetscapeCookieFile(appContext, allMerged)
                val hasAuth = NetscapeCookieHelper.hasYouTubeAuthCookies(allMerged)
                _uiState.update {
                    it.copy(
                        cookies = allMerged,
                        isSessionValidated = hasAuth,
                        statusMessage = if (hasAuth) {
                            "${allMerged.size} çerez Android CookieManager ile senkronize edildi (SAPISID aktif)."
                        } else {
                            "${allMerged.size} çerez mevcut (SAPISID oturum çerezi bekleniyor)."
                        }
                    )
                }
            } else {
                _uiState.update {
                    it.copy(statusMessage = "Henüz kayıtlı çerez bulunamadı. Lütfen çerez yapıştırın.")
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun addOrUpdateCookie(cookie: CookieItem) {
        try {
            cookieManager?.setAcceptCookie(true)
            cookieManager?.setCookie("https://music.youtube.com", cookie.toRawCookieString())
            cookieManager?.setCookie("https://www.youtube.com", cookie.toRawCookieString())
            cookieManager?.flush()
        } catch (_: Throwable) {
        }
        val updated = _uiState.value.cookies.toMutableList().apply {
            val idx = indexOfFirst { it.id == cookie.id || it.name == cookie.name }
            if (idx >= 0) set(idx, cookie) else add(0, cookie)
        }
        storageHelper.saveCookies("https://music.youtube.com", updated)
        NetscapeCookieHelper.writeNetscapeCookieFile(appContext, updated)
        _uiState.update {
            it.copy(
                cookies = updated,
                isSessionValidated = NetscapeCookieHelper.hasYouTubeAuthCookies(updated),
                statusMessage = "'${cookie.name}' çerezi CookieManager ve yerel depoya kaydedildi."
            )
        }
    }

    fun deleteCookie(cookie: CookieItem) {
        val updated = _uiState.value.cookies.filterNot { it.id == cookie.id }
        storageHelper.saveCookies(_uiState.value.currentUrl, updated)
        NetscapeCookieHelper.writeNetscapeCookieFile(appContext, updated)
        _uiState.update {
            it.copy(
                cookies = updated,
                isSessionValidated = NetscapeCookieHelper.hasYouTubeAuthCookies(updated),
                statusMessage = "'${cookie.name}' silindi."
            )
        }
    }

    fun clearAllCookies() {
        try {
            cookieManager?.removeAllCookies(null)
            cookieManager?.flush()
        } catch (_: Throwable) {
        }
        storageHelper.clearSavedCookies()
        _uiState.update {
            it.copy(
                cookies = emptyList(),
                isSessionValidated = false,
                verifiedAccountName = null,
                statusMessage = "Tüm çerezler CookieManager ve yerel depodan temizlendi."
            )
        }
    }

    fun saveCurrentCookiesToStorage() {
        val state = _uiState.value
        val merged = NetscapeCookieHelper.resolveActiveCookies(appContext, state.cookies)
        storageHelper.saveCookies("https://music.youtube.com", merged)
        NetscapeCookieHelper.writeNetscapeCookieFile(appContext, merged)
        val hasAuth = NetscapeCookieHelper.hasYouTubeAuthCookies(merged)
        _uiState.update {
            it.copy(
                cookies = merged,
                isSessionValidated = hasAuth,
                statusMessage = if (hasAuth) {
                    "${merged.size} çerez kaydedildi! Beğenilen Şarkılar (VLLM) indirmeye hazır."
                } else {
                    "${merged.size} çerez kaydedildi (SAPISID bulunamadı, lütfen tam çerez yapıştırın)."
                }
            )
        }
    }

    fun loadCookiesFromStorage() {
        val (savedUrl, savedCookies) = storageHelper.loadCookies()
        val merged = NetscapeCookieHelper.resolveActiveCookies(appContext, savedCookies)
        NetscapeCookieHelper.writeNetscapeCookieFile(appContext, merged)
        val hasAuth = NetscapeCookieHelper.hasYouTubeAuthCookies(merged)
        _uiState.update {
            it.copy(
                currentUrl = savedUrl.ifBlank { it.currentUrl },
                cookies = merged,
                isSessionValidated = hasAuth,
                statusMessage = "${merged.size} kayıtlı çerez yüklendi ve CookieManager'a aktarıldı."
            )
        }
    }

    fun exportCookiesAsJson(): String {
        val state = _uiState.value
        val merged = NetscapeCookieHelper.resolveActiveCookies(appContext, state.cookies)
        return storageHelper.exportCookiesAsJson(state.currentUrl, merged)
    }

    fun clearLogs() {
        _uiState.update { it.copy(logEntries = emptyList()) }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
