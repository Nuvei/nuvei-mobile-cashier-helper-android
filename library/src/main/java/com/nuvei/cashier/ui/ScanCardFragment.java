package com.nuvei.cashier.ui;

import static com.nuvei.cashier.ndk.RecognitionConstants.RECOGNIZER_MODE_DATE;
import static com.nuvei.cashier.ndk.RecognitionConstants.RECOGNIZER_MODE_GRAB_CARD_IMAGE;
import static com.nuvei.cashier.ndk.RecognitionConstants.RECOGNIZER_MODE_NAME;
import static com.nuvei.cashier.ndk.RecognitionConstants.RECOGNIZER_MODE_NUMBER;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.fragment.app.Fragment;

import java.io.ByteArrayOutputStream;

import com.nuvei.cashier.Card;
import com.nuvei.cashier.R;
import com.nuvei.cashier.ScanCardIntent;
import com.nuvei.cashier.camera.ScanManager;
import com.nuvei.cashier.camera.widget.CameraPreviewLayout;
import com.nuvei.cashier.ndk.RecognitionResult;
import com.nuvei.cashier.ui.views.ProgressBarIndeterminate;
import com.nuvei.cashier.utils.Constants;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class ScanCardFragment extends Fragment {
    @SuppressWarnings("unused")
    public static final String TAG = "ScanCardFragment";

    private CameraPreviewLayout mCameraPreviewLayout;

    private ProgressBarIndeterminate mProgressBar;

    private ViewGroup mMainContent;

    @Nullable
    private View mFlashButton;

    @Nullable
    private ScanManager mScanManager;

    private SoundPool mSoundPool;

    private int mCapturedSoundId = -1;

    private InteractionListener mListener;

    private ScanCardRequest mRequest;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (InteractionListener) getActivity();
        } catch (ClassCastException ex) {
            throw new RuntimeException("Parent must implement " + InteractionListener.class.getSimpleName());
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRequest = null;
        if (getArguments() != null) {
            mRequest = getArguments().getParcelable(ScanCardIntent.KEY_SCAN_CARD_REQUEST);
        }
        if (mRequest == null) mRequest = ScanCardRequest.getDefault();
    }

    @Override
    public Animation onCreateAnimation(int transit, boolean enter, int nextAnim) {
        if (Constants.DEBUG)
            Log.d(TAG, "onCreateAnimation() called with: " + "transit = [" + transit + "], enter = [" + enter + "], nextAnim = [" + nextAnim + "]");
        // SurfaceView is hard to animate
        Animation a = new Animation() {
        };
        a.setDuration(0);
        return a;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_scan_card, container, false);

        mProgressBar = root.findViewById(R.id.progress_bar);

        mCameraPreviewLayout = root.findViewById(R.id.card_recognition_view);
        mMainContent = root.findViewById(R.id.main_content);
        mFlashButton = root.findViewById(R.id.iv_flash_id);

        initView(root);

        showMainContent();
        mProgressBar.setVisibility(View.VISIBLE);
        return root;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);


        getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        int recognitionMode = RECOGNIZER_MODE_NUMBER;
        if (mRequest.isScanCardHolderEnabled()) recognitionMode |= RECOGNIZER_MODE_NAME;
        if (mRequest.isScanExpirationDateEnabled()) recognitionMode |= RECOGNIZER_MODE_DATE;
        if (mRequest.isGrabCardImageEnabled()) recognitionMode |= RECOGNIZER_MODE_GRAB_CARD_IMAGE;

        mScanManager = new ScanManager(recognitionMode, getActivity(), mCameraPreviewLayout, new ScanManager.Callbacks() {

            private byte mLastCardImage[] = null;

            @Override
            public void onCameraOpened(Camera.Parameters cameraParameters) {
                boolean isFlashSupported = (cameraParameters.getSupportedFlashModes() != null
                        && !cameraParameters.getSupportedFlashModes().isEmpty());
                if (getView() == null) return;
                mProgressBar.hideSlow();
                mCameraPreviewLayout.setBackgroundDrawable(null);
                if (mFlashButton != null)
                    mFlashButton.setVisibility(isFlashSupported ? View.VISIBLE : View.GONE);

                innitSoundPool();
            }

            @Override
            public void onOpenCameraError(Exception exception) {
                mProgressBar.hideSlow();
                hideMainContent();
                finishWithError(exception);
            }

            @Override
            public void onRecognitionComplete(RecognitionResult result) {
                if (result.isFirst()) {
                    if (mScanManager != null) mScanManager.freezeCameraPreview();
                    playCaptureSound();
                }
                if (result.isFinal()) {
                    String date;
                    if (TextUtils.isEmpty(result.getDate())) {
                        date = null;
                    } else {
                        date = result.getDate().substring(0, 2) + '/' + result.getDate().substring(2);
                    }

                    Card card = new Card(result.getNumber(), result.getName(), date);
                    byte cardImage[] = mLastCardImage;
                    mLastCardImage = null;
                    finishWithResult(card, cardImage);
                }
            }

            @Override
            public void onCardImageReceived(Bitmap cardImage) {
                mLastCardImage = compressCardImage(cardImage);
            }

            @Override
            public void onFpsReport(String report) {
            }

            @Override
            public void onAutoFocusMoving(boolean start, String cameraFocusMode) {
            }

            @Override
            public void onAutoFocusComplete(boolean success, String cameraFocusMode) {
            }

            @Nullable
            private byte[] compressCardImage(Bitmap img) {
                byte result[];
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                if (img.compress(Bitmap.CompressFormat.JPEG, 80, stream)) {
                    result = stream.toByteArray();
                } else {
                    result = null;
                }
                return result;
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mScanManager != null) mScanManager.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mScanManager != null) mScanManager.onPause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mSoundPool != null) {
            mSoundPool.release();
            mSoundPool = null;
        }
        mCapturedSoundId = -1;
    }

    private void innitSoundPool() {
        if (mRequest.isSoundEnabled()) {
            mSoundPool = new SoundPool(1, AudioManager.STREAM_SYSTEM, 0);
            mCapturedSoundId = mSoundPool.load(getActivity(), R.raw.wocr_capture_card, 0);
        }
    }

    private void initView(View view) {
        view.findViewById(R.id.tv_enter_card_number_id).setOnClickListener(v -> {
            if (v.isEnabled()) {
                v.setEnabled(false);
                if (mListener != null)
                    mListener.onScanCardCanceled(ScanCardIntent.BACK_PRESSED);
            }
        });
        if (mFlashButton != null) {
            mFlashButton.setOnClickListener(v -> {
                if (mScanManager != null) mScanManager.toggleFlash();
            });
        }
    }

    private void showMainContent() {
        mMainContent.setVisibility(View.VISIBLE);
        mCameraPreviewLayout.setVisibility(View.VISIBLE);
    }

    private void hideMainContent() {
        mMainContent.setVisibility(View.INVISIBLE);
        mCameraPreviewLayout.setVisibility(View.INVISIBLE);
    }

    private void finishWithError(Exception exception) {
        if (mListener != null) mListener.onScanCardFailed(exception);
    }

    private void finishWithResult(Card card, @Nullable byte cardImage[]) {
        if (mListener != null) mListener.onScanCardFinished(card, cardImage);
    }

    private void playCaptureSound() {
        if (mCapturedSoundId >= 0) mSoundPool.play(mCapturedSoundId, 1, 1, 0, 0, 1);
    }

    public interface InteractionListener {
        void onScanCardCanceled(@ScanCardIntent.CancelReason int cancelReason);

        void onScanCardFailed(Exception e);

        void onScanCardFinished(Card card, byte cardImage[]);
    }
}
