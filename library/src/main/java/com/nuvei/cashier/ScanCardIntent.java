package com.nuvei.cashier;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.IntDef;
import androidx.annotation.Keep;
import androidx.annotation.RestrictTo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.nuvei.cashier.ui.ScanCardActivity;
import com.nuvei.cashier.ui.ScanCardRequest;

@Keep
public final class ScanCardIntent {

    public static final int RESULT_CODE_ERROR = Activity.RESULT_FIRST_USER;

    public static final String RESULT_PAYCARDS_CARD = "RESULT_PAYCARDS_CARD";
    public static final String RESULT_CARD_IMAGE = "RESULT_CARD_IMAGE";
    public static final String RESULT_CANCEL_REASON = "RESULT_CANCEL_REASON";

    public static final int BACK_PRESSED = 1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {BACK_PRESSED})
    public @interface CancelReason {}

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static final String KEY_SCAN_CARD_REQUEST = "com.nuvei.cashier.ui.ScanCardActivity.SCAN_CARD_REQUEST";

    private ScanCardIntent() {
    }

    @Keep
    public final static class Builder {

        private final Context mContext;

        private boolean mEnableSound = ScanCardRequest.DEFAULT_ENABLE_SOUND;

        private boolean mScanExpirationDate = ScanCardRequest.DEFAULT_SCAN_EXPIRATION_DATE;

        private boolean mScanCardHolder = ScanCardRequest.DEFAULT_SCAN_CARD_HOLDER;

        private boolean mGrabCardImage = ScanCardRequest.DEFAULT_GRAB_CARD_IMAGE;


        public Builder(Context context) {
            mContext = context;
        }

        /**
         * Scan expiration date. Default: <b>true</b>
         */
        public Builder setScanExpirationDate(boolean scanExpirationDate) {
            mScanExpirationDate = scanExpirationDate;
            return this;
        }

        /**
         * Scan expiration date. Default: <b>true</b>
         */
        public Builder setScanCardHolder(boolean scanCardHolder) {
            mScanCardHolder = scanCardHolder;
            return this;
        }


        /**
         * Enables or disables sounds in the library.<Br>
         * Default: <b>enabled</b>
         */
        public Builder setSoundEnabled(boolean enableSound) {
            mEnableSound = enableSound;
            return this;
        }


        /**
         * Defines if the card image will be captured.
         * @param enable Defines if the card image will be captured. Default: <b>false</b>
         */
        public Builder setSaveCard(boolean enable) {
            mGrabCardImage = enable;
            return this;
        }

        public Intent build() {
            Intent intent = new Intent(mContext, ScanCardActivity.class);
            ScanCardRequest request = new ScanCardRequest(mEnableSound, mScanExpirationDate,
                    mScanCardHolder, mGrabCardImage);
            intent.putExtra(KEY_SCAN_CARD_REQUEST, request);
            return intent;
        }
    }
}
