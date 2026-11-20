package com.qcenaman.francesinha.logic

import java.util.Locale

enum class Lang(val code: String, val label: String, val locale: Locale) {
    PT("pt", "Português (Portugal)", Locale("pt", "PT")),
    FR("fr", "Français", Locale.FRANCE),
    EN("en", "English", Locale.UK);

    companion object {
        fun from(code: String?): Lang = entries.firstOrNull { it.code == code } ?: PT
    }
}

object I18n {
    @Volatile
    var lang: Lang = Lang.PT

    fun tr(key: String, vararg args: Any?): String {
        val tpl = when (lang) {
            Lang.PT -> Translations.pt[key]
            Lang.FR -> Translations.fr[key]
            Lang.EN -> null
        } ?: key
        return if (args.isEmpty()) tpl else String.format(lang.locale, tpl, *args)
    }
}

fun tr(key: String, vararg args: Any?): String = I18n.tr(key, *args)
