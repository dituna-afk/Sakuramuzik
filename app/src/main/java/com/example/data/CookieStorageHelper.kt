package com.example.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.model.CookieItem
import org.json.JSONArray
import org.json.JSONObject

class CookieStorageHelper(context: Context) {
    private val appContext = context.applicationContext

    private val sharedPreferences: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (_: Exception) {
            appContext.getSharedPreferences(FALLBACK_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    private fun getSafePrefs(): SharedPreferences {
        return try {
            sharedPreferences
        } catch (_: Throwable) {
            appContext.getSharedPreferences(FALLBACK_PREFS_FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    fun saveCookies(url: String, cookies: List<CookieItem>) {
        try {
            val jsonArray = JSONArray()
            cookies.forEach { cookie ->
                val obj = JSONObject().apply {
                    put("id", cookie.id)
                    put("name", cookie.name)
                    put("value", cookie.value)
                    put("domain", cookie.domain)
                    put("path", cookie.path)
                    put("isSecure", cookie.isSecure)
                    put("isHttpOnly", cookie.isHttpOnly)
                }
                jsonArray.put(obj)
            }
            getSafePrefs().edit()
                .putString(KEY_SAVED_URL, url)
                .putString(KEY_SAVED_COOKIES, jsonArray.toString())
                .apply()
        } catch (_: Throwable) {
            // Fallback write
            runCatching {
                appContext.getSharedPreferences(FALLBACK_PREFS_FILE_NAME, Context.MODE_PRIVATE).edit()
                    .putString(KEY_SAVED_URL, url)
                    .apply()
            }
        }
    }

    fun loadCookies(): Pair<String, List<CookieItem>> {
        return try {
            val prefs = getSafePrefs()
            val savedUrl = try {
                prefs.getString(KEY_SAVED_URL, "") ?: ""
            } catch (_: Throwable) {
                ""
            }
            val rawJson = try {
                prefs.getString(KEY_SAVED_COOKIES, null)
            } catch (_: Throwable) {
                null
            } ?: return savedUrl to emptyList()

            val array = JSONArray(rawJson)
            val list = mutableListOf<CookieItem>()
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                list.add(
                    CookieItem(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", ""),
                        value = obj.optString("value", ""),
                        domain = obj.optString("domain", ""),
                        path = obj.optString("path", "/"),
                        isSecure = obj.optBoolean("isSecure", true),
                        isHttpOnly = obj.optBoolean("isHttpOnly", false)
                    )
                )
            }
            savedUrl to list
        } catch (_: Throwable) {
            "" to emptyList()
        }
    }

    fun clearSavedCookies() {
        try {
            getSafePrefs().edit()
                .remove(KEY_SAVED_URL)
                .remove(KEY_SAVED_COOKIES)
                .apply()
        } catch (_: Throwable) {
            runCatching {
                appContext.getSharedPreferences(FALLBACK_PREFS_FILE_NAME, Context.MODE_PRIVATE).edit()
                    .remove(KEY_SAVED_URL)
                    .remove(KEY_SAVED_COOKIES)
                    .apply()
            }
        }
    }

    fun exportCookiesAsJson(url: String, cookies: List<CookieItem>): String {
        val root = JSONObject()
        root.put("url", url)
        root.put("exportedAt", System.currentTimeMillis())
        val array = JSONArray()
        cookies.forEach { c ->
            array.put(
                JSONObject().apply {
                    put("name", c.name)
                    put("value", c.value)
                    put("domain", c.domain)
                    put("path", c.path)
                    put("secure", c.isSecure)
                    put("httpOnly", c.isHttpOnly)
                }
            )
        }
        root.put("cookies", array)
        return root.toString(2)
    }

    companion object {
        private const val PREFS_FILE_NAME = "secure_cookie_prefs"
        private const val FALLBACK_PREFS_FILE_NAME = "fallback_cookie_prefs"
        private const val KEY_SAVED_URL = "saved_url"
        private const val KEY_SAVED_COOKIES = "saved_cookies_json"
    }
}
