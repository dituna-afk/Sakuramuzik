package com.example.data

import android.content.Context
import com.example.ui.theme.AppTheme

class ThemeStorageHelper(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveTheme(theme: AppTheme) {
        prefs.edit().putString(KEY_SELECTED_THEME, theme.name).apply()
    }

    fun loadTheme(): AppTheme {
        val raw = prefs.getString(KEY_SELECTED_THEME, null)
        return AppTheme.fromName(raw)
    }

    fun saveTreeStyle(treeStyle: SakuraTreeStyle) {
        prefs.edit().putString(KEY_SELECTED_TREE_STYLE, treeStyle.id).apply()
    }

    fun loadTreeStyle(): SakuraTreeStyle {
        val raw = prefs.getString(KEY_SELECTED_TREE_STYLE, null)
        return SakuraTreeStyle.fromId(raw)
    }

    fun saveTreeForm(treeForm: SakuraTreeForm) {
        prefs.edit().putString(KEY_SELECTED_TREE_FORM, treeForm.id).apply()
    }

    fun loadTreeForm(): SakuraTreeForm {
        val raw = prefs.getString(KEY_SELECTED_TREE_FORM, null)
        return SakuraTreeForm.fromId(raw)
    }

    companion object {
        private const val PREFS_NAME = "sakura_theme_prefs"
        private const val KEY_SELECTED_THEME = "selected_app_theme"
        private const val KEY_SELECTED_TREE_STYLE = "selected_sakura_tree_style_v27"
        private const val KEY_SELECTED_TREE_FORM = "selected_sakura_tree_form"
    }
}
