package com.familysonar;

import android.content.SharedPreferences;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.ViewFlipper;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

/**
 * First-run informational screens shown before any SMS or location permission
 * is requested. They explain why FindMe uses SMS, when messages are sent or
 * received, how emergency location requests work, and the privacy limits.
 * Completing the last page stores the SMS/location consent flag.
 */
public class OnboardingActivity extends AppCompatActivity {

    static final String PREFS_ONBOARDING = "findme_onboarding";
    static final String KEY_SMS_CONSENT = "sms_consent_v1";

    private ViewFlipper flipper;
    private TextView indicator;
    private MaterialButton backButton;
    private MaterialButton nextButton;
    private MaterialButton disagreeButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        flipper = findViewById(R.id.onboardingFlipper);
        indicator = findViewById(R.id.onboardingIndicator);
        backButton = findViewById(R.id.onboardingBackButton);
        nextButton = findViewById(R.id.onboardingNextButton);
        disagreeButton = findViewById(R.id.onboardingDisagreeButton);

        backButton.setOnClickListener(view -> {
            if (flipper.getDisplayedChild() > 0) {
                flipper.showPrevious();
                updateControls();
            }
        });

        nextButton.setOnClickListener(view -> {
            if (isLastPage()) {
                completeOnboarding();
            } else {
                flipper.showNext();
                updateControls();
            }
        });

        disagreeButton.setOnClickListener(view -> showDeclineDialog());

        updateControls();
    }

    private boolean isLastPage() {
        return flipper.getDisplayedChild() == flipper.getChildCount() - 1;
    }

    private void updateControls() {
        int current = flipper.getDisplayedChild();
        int total = flipper.getChildCount();

        indicator.setText(getString(R.string.onboarding_indicator, current + 1, total));
        backButton.setVisibility(current == 0 ? View.GONE : View.VISIBLE);
        disagreeButton.setVisibility(isLastPage() ? View.VISIBLE : View.GONE);
        nextButton.setText(isLastPage() ? R.string.onboarding_agree : R.string.onboarding_next);
    }

    private void completeOnboarding() {
        SharedPreferences.Editor editor =
                getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE).edit();
        editor.putBoolean(KEY_SMS_CONSENT, true);
        editor.apply();

        setResult(RESULT_OK);
        finish();
    }

    private void showDeclineDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.onboarding_decline_title)
                .setMessage(R.string.onboarding_decline_message)
                .setNegativeButton(R.string.onboarding_back, null)
                .setPositiveButton(R.string.onboarding_decline_uninstall, (dialog, which) -> declineAndUninstall())
                .show();
    }

    private void declineAndUninstall() {
        setResult(RESULT_CANCELED);
        try {
            Intent uninstall = new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + getPackageName()));
            uninstall.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(uninstall);
        } catch (RuntimeException ignored) {
            // If the uninstall screen cannot be opened, still close the app below.
        }
        finishAffinity();
    }

    @Override
    public void onBackPressed() {
        if (flipper.getDisplayedChild() > 0) {
            flipper.showPrevious();
            updateControls();
        } else {
            super.onBackPressed();
        }
    }
}
