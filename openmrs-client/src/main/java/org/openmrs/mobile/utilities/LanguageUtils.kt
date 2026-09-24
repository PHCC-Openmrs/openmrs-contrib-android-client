/*
 * The contents of this file are subject to the OpenMRS Public License
 * Version 1.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://license.openmrs.org
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific language governing rights and limitations
 * under the License.
 *
 * Copyright (C) OpenMRS, LLC.  All Rights Reserved.
 */
package org.openmrs.mobile.utilities

import android.content.res.Configuration
import com.openmrs.android_sdk.library.OpenmrsAndroid
import com.openmrs.android_sdk.utilities.ApplicationConstants
import org.openmrs.mobile.R
import java.util.Locale

/**
 * Which language the app runs in, and which languages it offers.
 *
 * Mirrors the web client: the languages on offer come from the server's allowed locales (the
 * `locale.allowed.list` global property, sent with every /session response), and until the user
 * picks one the app follows the server's locale for them. On top of that the app only offers a
 * language it actually has translations for - picking one it doesn't would change nothing but
 * the date formats and, for right-to-left languages, the layout direction.
 *
 * Whether a translation exists is read from the resources themselves rather than kept in a list:
 * every `values-xx` folder carries `R.string.language_tag` set to its own language, so adding a
 * translation folder is all it takes for that language to become selectable.
 */
object LanguageUtils {

    data class LanguageOption(val tag: String, val displayName: String)

    private val translatedCache = HashMap<String, Boolean>()

    /**
     * Gets the language the app should run in: the user's explicit choice if it's still one the
     * app has translations for, otherwise the server's locale for them, otherwise the language
     * of the app's default resources.
     *
     * @return a BCP 47 language tag
     */
    @JvmStatic
    fun getLanguage(): String {
        val chosen = OpenmrsAndroid.getOpenMRSSharedPreferences()
                .getString(ApplicationConstants.OpenMRSlanguage.KEY_LANGUAGE_MODE, null)
        if (chosen != null && isTranslated(toLocale(chosen))) {
            return toLocale(chosen).toLanguageTag()
        }
        val serverLocale = OpenmrsAndroid.getServerLocale()
        if (serverLocale != null && isTranslated(toLocale(serverLocale))) {
            return toLocale(serverLocale).toLanguageTag()
        }
        return getDefaultLanguage()
    }

    @JvmStatic
    fun setLanguage(language: String?) {
        val editor = OpenmrsAndroid.getOpenMRSSharedPreferences().edit()
        editor.putString(ApplicationConstants.OpenMRSlanguage.KEY_LANGUAGE_MODE, language)
        editor.apply()
    }

    /**
     * Gets the locale to apply to the app's resources.
     */
    @JvmStatic
    fun getLocale(): Locale = toLocale(getLanguage())

    /**
     * Gets the languages the user can pick from: the server's allowed locales that the app has
     * translations for, one entry per language, in the server's order. The current language is
     * always included, so the picker can show it; and until the server has reported its list at
     * all, the default language is offered too, so there's always a way back to it.
     */
    @JvmStatic
    fun getSelectableLanguages(): List<LanguageOption> {
        val allowed = OpenmrsAndroid.getServerAllowedLocales().map { toLocale(it) }
        val fallback = if (allowed.isEmpty()) listOf(toLocale(getDefaultLanguage())) else emptyList()
        val candidates = allowed + fallback + toLocale(getLanguage())

        val byLanguage = LinkedHashMap<String, Locale>()
        for (locale in candidates) {
            if (!isTranslated(locale)) continue
            // Several allowed tags can share one translation (en, en-GB) - keep a single entry,
            // preferring the bare language tag if the server lists it.
            val existing = byLanguage[locale.language]
            if (existing == null || (existing.toLanguageTag() != locale.language && locale.toLanguageTag() == locale.language)) {
                byLanguage[locale.language] = locale
            }
        }

        return byLanguage.values.map { LanguageOption(it.toLanguageTag(), displayName(it)) }
    }

    /**
     * Gets the position of [language] in [options] - an exact match if there is one, otherwise
     * one for the same language (en-GB is shown by the en entry), otherwise the first.
     */
    @JvmStatic
    fun indexOfLanguage(options: List<LanguageOption>, language: String): Int {
        val exact = options.indexOfFirst { it.tag == language }
        if (exact >= 0) return exact
        val sameLanguage = options.indexOfFirst { toLocale(it.tag).language == toLocale(language).language }
        return if (sameLanguage >= 0) sameLanguage else 0
    }

    /**
     * Parses a locale as OpenMRS writes it. The server uses Java's `en_GB` form, which
     * [Locale.forLanguageTag] would reject.
     */
    @JvmStatic
    fun toLocale(tag: String): Locale = Locale.forLanguageTag(tag.trim().replace('_', '-'))

    private fun getDefaultLanguage(): String = Locale(resourceLanguageFor(Locale.ROOT)).toLanguageTag()

    /** The language's name in that language itself, as the web client shows it. */
    private fun displayName(locale: Locale): String =
            locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }

    private fun isTranslated(locale: Locale): Boolean {
        // Unparseable tags come back from forLanguageTag with no language.
        if (locale.language.isEmpty() || locale.language == "und") return false
        return synchronized(translatedCache) {
            translatedCache.getOrPut(locale.language) {
                // Comparing through Locale normalises the legacy codes Java still reports (he as iw).
                Locale(resourceLanguageFor(locale)).language == locale.language
            }
        }
    }

    /**
     * The language of the resources Android would pick for [locale] - its own translation if
     * there is one, otherwise the default resources.
     */
    private fun resourceLanguageFor(locale: Locale): String {
        val context = OpenmrsAndroid.getInstance()!!
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        return context.createConfigurationContext(configuration).getString(R.string.language_tag)
    }
}
