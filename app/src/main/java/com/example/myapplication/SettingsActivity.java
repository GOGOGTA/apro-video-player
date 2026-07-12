package com.example.myapplication;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.radiobutton.MaterialRadioButton;

public class SettingsActivity extends AppCompatActivity {

    private MaterialToolbar toolbar;
    private TextView tvLanguageTitle;
    private TextView tvLanguageValue;
    private RadioGroup rgLanguageOptions;
    private MaterialRadioButton rbLanguageZh;
    private MaterialRadioButton rbLanguageEn;
    private MaterialRadioButton rbLanguageFr;
    private MaterialRadioButton rbLanguageEs;
    private MaterialRadioButton rbLanguageRu;
    private MaterialRadioButton rbLanguageJa;
    private MaterialRadioButton rbLanguageKo;
    private TextView tvAboutTitle;
    private TextView tvPlayTitle;
    private MaterialSwitch switchAutoPlayNext;
    private MaterialSwitch switchRandomPlayOnStart;
    private PlaybackSettingsManager playbackSettingsManager;
    private TextView tvVersionValue;
    private View rowPrivacy;
    private View rowLicense;
    private View rowFeedback;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LanguageManager.createLocalizedContext(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        LanguageManager.applySavedLanguage(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        toolbar = findViewById(R.id.toolbarSettings);
        tvLanguageTitle = findViewById(R.id.tvLanguageTitle);
        tvLanguageValue = findViewById(R.id.tvLanguageValue);
        rgLanguageOptions = findViewById(R.id.rgLanguageOptions);
        rbLanguageZh = findViewById(R.id.rbLanguageZh);
        rbLanguageEn = findViewById(R.id.rbLanguageEn);
        rbLanguageFr = findViewById(R.id.rbLanguageFr);
        rbLanguageEs = findViewById(R.id.rbLanguageEs);
        rbLanguageRu = findViewById(R.id.rbLanguageRu);
        rbLanguageJa = findViewById(R.id.rbLanguageJa);
        rbLanguageKo = findViewById(R.id.rbLanguageKo);

        tvAboutTitle = findViewById(R.id.tvAboutTitle);
        tvPlayTitle = findViewById(R.id.tvPlayTitle);
        switchAutoPlayNext = findViewById(R.id.switchAutoPlayNext);
        switchRandomPlayOnStart = findViewById(R.id.switchRandomPlayOnStart);
        playbackSettingsManager = new PlaybackSettingsManager(this);
        tvVersionValue = findViewById(R.id.tvVersionValue);
        rowPrivacy = findViewById(R.id.rowPrivacy);
        rowLicense = findViewById(R.id.rowLicense);
        rowFeedback = findViewById(R.id.rowFeedback);

        toolbar.setNavigationOnClickListener(v -> finish());

        bindLanguageSelection();
        bindPlaySettings();
        bindAboutClicks();
        refreshLocalizedTexts();
    }

    private void refreshLocalizedTexts() {
        toolbar.setTitle(LanguageManager.getLocalizedString(this, R.string.settings_title));
        tvLanguageTitle.setText(LanguageManager.getLocalizedString(this, R.string.language_setting_title));
        tvPlayTitle.setText(LanguageManager.getLocalizedString(this, R.string.play_setting_title));
        rbLanguageZh.setText(getString(R.string.language_option_zh));
        rbLanguageEn.setText(getString(R.string.language_option_en));
        rbLanguageFr.setText(getString(R.string.language_option_fr));
        rbLanguageEs.setText(getString(R.string.language_option_es));
        rbLanguageRu.setText(getString(R.string.language_option_ru));
        rbLanguageJa.setText(getString(R.string.language_option_ja));
        rbLanguageKo.setText(getString(R.string.language_option_ko));
        tvLanguageValue.setText(LanguageManager.getLocalizedString(
                this,
                R.string.language_current_format,
                LanguageManager.currentLanguageLabel(this)
        ));

        String currentTag = LanguageManager.currentLanguageTag(this);
        if (LanguageManager.TAG_ZH.equals(currentTag)) {
            rgLanguageOptions.check(R.id.rbLanguageZh);
        } else if (LanguageManager.TAG_EN.equals(currentTag)) {
            rgLanguageOptions.check(R.id.rbLanguageEn);
        } else if (LanguageManager.TAG_FR.equals(currentTag)) {
            rgLanguageOptions.check(R.id.rbLanguageFr);
        } else if (LanguageManager.TAG_ES.equals(currentTag)) {
            rgLanguageOptions.check(R.id.rbLanguageEs);
        } else if (LanguageManager.TAG_RU.equals(currentTag)) {
            rgLanguageOptions.check(R.id.rbLanguageRu);
        } else if (LanguageManager.TAG_JA.equals(currentTag)) {
            rgLanguageOptions.check(R.id.rbLanguageJa);
        } else if (LanguageManager.TAG_KO.equals(currentTag)) {
            rgLanguageOptions.check(R.id.rbLanguageKo);
        } else {
            rgLanguageOptions.check(R.id.rbLanguageEn);
        }

        tvAboutTitle.setText(getString(R.string.about_title));
        tvVersionValue.setText(getString(R.string.version_format, getAppVersion()));
    }

    private void bindLanguageSelection() {
        rgLanguageOptions.setOnCheckedChangeListener((group, checkedId) -> {
            String tag = LanguageManager.TAG_EN;
            if (checkedId == R.id.rbLanguageZh) {
                tag = LanguageManager.TAG_ZH;
            } else if (checkedId == R.id.rbLanguageEn) {
                tag = LanguageManager.TAG_EN;
            } else if (checkedId == R.id.rbLanguageFr) {
                tag = LanguageManager.TAG_FR;
            } else if (checkedId == R.id.rbLanguageEs) {
                tag = LanguageManager.TAG_ES;
            } else if (checkedId == R.id.rbLanguageRu) {
                tag = LanguageManager.TAG_RU;
            } else if (checkedId == R.id.rbLanguageJa) {
                tag = LanguageManager.TAG_JA;
            } else if (checkedId == R.id.rbLanguageKo) {
                tag = LanguageManager.TAG_KO;
            }
            if (!tag.equals(LanguageManager.currentLanguageTag(this))) {
                LanguageManager.updateLanguage(this, tag);
                // Recreate so every XML-inflated label and future dialog uses the new locale.
                recreate();
            }
        });
    }

    private void bindPlaySettings() {
        switchAutoPlayNext.setChecked(playbackSettingsManager.isAutoPlayNext());
        switchAutoPlayNext.setOnCheckedChangeListener((buttonView, isChecked) ->
                playbackSettingsManager.setAutoPlayNext(isChecked));
        switchRandomPlayOnStart.setChecked(playbackSettingsManager.isRandomPlayOnStart());
        switchRandomPlayOnStart.setOnCheckedChangeListener((buttonView, isChecked) ->
                playbackSettingsManager.setRandomPlayOnStart(isChecked));
    }

    private void bindAboutClicks() {
        rowPrivacy.setOnClickListener(v -> showPrivacyDialog());
        rowLicense.setOnClickListener(v -> showLicenseDialog());
        rowFeedback.setOnClickListener(v -> showFeedbackDialog());
    }

    private void showPrivacyDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.privacy_entry))
                .setMessage(getString(R.string.privacy_content))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showLicenseDialog() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.license_entry))
                .setMessage(getString(R.string.license_content))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void showFeedbackDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_feedback, null);
        View emailBadge = dialogView.findViewById(R.id.emailBadge);

        emailBadge.setOnLongClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("email", getString(R.string.feedback_email));
            clipboard.setPrimaryClip(clip);
            Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(50);
                }
            }
            Toast.makeText(this, getString(R.string.feedback_copied), Toast.LENGTH_SHORT).show();
            return true;
        });

        new AlertDialog.Builder(this)
                .setView(dialogView)
                .setPositiveButton(getString(R.string.feedback_got_it), null)
                .show();
    }

    private String getAppVersion() {
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            return pInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0";
        }
    }
}
