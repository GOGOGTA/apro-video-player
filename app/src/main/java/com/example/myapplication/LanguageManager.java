package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;

import androidx.annotation.StringRes;

import java.util.Locale;

public final class LanguageManager {

    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_LANGUAGE_TAG = "language_tag";

    public static final String TAG_SYSTEM = "system";
    public static final String TAG_ZH = "zh-CN";
    public static final String TAG_EN = "en";
    public static final String TAG_FR = "fr";
    public static final String TAG_ES = "es";
    public static final String TAG_RU = "ru";
    public static final String TAG_JA = "ja";
    public static final String TAG_KO = "ko";

    private static final String[] ORDER = {
            TAG_ZH, TAG_EN, TAG_FR, TAG_ES, TAG_RU, TAG_JA, TAG_KO
    };

    private LanguageManager() {
    }

    public static String cycleLanguage(Context context) {
        String currentTag = readTag(context);
        String nextTag = nextTag(currentTag);
        saveTag(context, nextTag);
        applyLanguageForContext(context, nextTag);
        return nextTag;
    }

    public static String currentLanguageTag(Context context) {
        return readTag(context);
    }

    public static void updateLanguage(Context context, String languageTag) {
        String normalizedTag = normalizeTag(languageTag);
        saveTag(context, normalizedTag);
        applyLanguageForContext(context, normalizedTag);
    }

    public static String currentLanguageLabel(Context context) {
        return labelForTag(readTag(context), context);
    }

    private static String labelForTag(String tag, Context context) {
        if (TAG_ZH.equals(tag)) {
            return context.getString(R.string.language_option_zh);
        }
        if (TAG_EN.equals(tag)) {
            return context.getString(R.string.language_option_en);
        }
        if (TAG_FR.equals(tag)) {
            return context.getString(R.string.language_option_fr);
        }
        if (TAG_ES.equals(tag)) {
            return context.getString(R.string.language_option_es);
        }
        if (TAG_RU.equals(tag)) {
            return context.getString(R.string.language_option_ru);
        }
        if (TAG_JA.equals(tag)) {
            return context.getString(R.string.language_option_ja);
        }
        if (TAG_KO.equals(tag)) {
            return context.getString(R.string.language_option_ko);
        }
        return context.getString(R.string.language_option_en);
    }

    private static String nextTag(String currentTag) {
        for (int i = 0; i < ORDER.length; i++) {
            if (ORDER[i].equals(currentTag)) {
                return ORDER[(i + 1) % ORDER.length];
            }
        }
        return ORDER[0];
    }

    public static void applySavedLanguage(Context context) {
        applyLanguageForContext(context, readTag(context));
    }

    public static void applyLanguageForContext(Context context, String languageTag) {
        Locale.setDefault(resolveLocale(languageTag));
    }

    @SuppressWarnings("deprecation")
    public static Context createLocalizedContext(Context context) {
        String languageTag = readTag(context);
        Locale locale = resolveLocale(languageTag);
        Configuration config = new Configuration(context.getResources().getConfiguration());
        config.setLocale(locale);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(new LocaleList(locale));
        }
        return context.createConfigurationContext(config);
    }

    public static String getLocalizedString(Context context, @StringRes int resId, Object... args) {
        Context localizedContext = createLocalizedContext(context);
        if (args == null || args.length == 0) {
            return localizedContext.getString(resId);
        }
        return localizedContext.getString(resId, args);
    }

    private static Locale resolveLocale(String languageTag) {
        if (TAG_ZH.equals(languageTag)) {
            return Locale.SIMPLIFIED_CHINESE;
        }
        if (TAG_EN.equals(languageTag)) {
            return Locale.ENGLISH;
        }
        if (TAG_FR.equals(languageTag)) {
            return Locale.FRENCH;
        }
        if (TAG_ES.equals(languageTag)) {
            return new Locale("es");
        }
        if (TAG_RU.equals(languageTag)) {
            return new Locale("ru");
        }
        if (TAG_JA.equals(languageTag)) {
            return Locale.JAPANESE;
        }
        if (TAG_KO.equals(languageTag)) {
            return Locale.KOREAN;
        }
        return mapSystemLocaleToSupportedLocale();
    }

    private static Locale systemLocale() {
        Configuration systemConfig = Resources.getSystem().getConfiguration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N
                && systemConfig.getLocales() != null
                && !systemConfig.getLocales().isEmpty()) {
            return systemConfig.getLocales().get(0);
        }
        return Locale.getDefault();
    }

    private static String readTag(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String rawTag = prefs.getString(KEY_LANGUAGE_TAG, TAG_SYSTEM);
        String normalizedTag = normalizeTag(rawTag);
        if (!normalizedTag.equals(rawTag)) {
            saveTag(context, normalizedTag);
        }
        return normalizedTag;
    }

    private static void saveTag(Context context, String tag) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_LANGUAGE_TAG, normalizeTag(tag)).apply();
    }

    private static String normalizeTag(String tag) {
        if (TAG_ZH.equals(tag)
                || TAG_EN.equals(tag)
                || TAG_FR.equals(tag)
                || TAG_ES.equals(tag)
                || TAG_RU.equals(tag)
                || TAG_JA.equals(tag)
                || TAG_KO.equals(tag)) {
            return tag;
        }
        return mapSystemLocaleToSupportedTag();
    }

    private static String mapSystemLocaleToSupportedTag() {
        Locale locale = systemLocale();
        String language = locale.getLanguage();
        if (language == null) {
            return TAG_EN;
        }
        if ("zh".equalsIgnoreCase(language)) {
            return TAG_ZH;
        }
        if ("fr".equalsIgnoreCase(language)) {
            return TAG_FR;
        }
        if ("es".equalsIgnoreCase(language)) {
            return TAG_ES;
        }
        if ("ru".equalsIgnoreCase(language)) {
            return TAG_RU;
        }
        if ("ja".equalsIgnoreCase(language)) {
            return TAG_JA;
        }
        if ("ko".equalsIgnoreCase(language)) {
            return TAG_KO;
        }
        return TAG_EN;
    }

    private static Locale mapSystemLocaleToSupportedLocale() {
        return resolveLocale(mapSystemLocaleToSupportedTag());
    }
}
