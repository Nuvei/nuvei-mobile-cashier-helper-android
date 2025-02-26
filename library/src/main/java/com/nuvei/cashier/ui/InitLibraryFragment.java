package com.nuvei.cashier.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.fragment.app.Fragment;

import com.nuvei.cashier.R;
import com.nuvei.cashier.ScanCardIntent;
import com.nuvei.cashier.camera.RecognitionAvailabilityChecker;
import com.nuvei.cashier.camera.RecognitionCoreUtils;
import com.nuvei.cashier.camera.RecognitionUnavailableException;
import com.nuvei.cashier.camera.widget.CameraPreviewLayout;
import com.nuvei.cashier.ndk.RecognitionCore;

import java.lang.ref.WeakReference;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class InitLibraryFragment extends Fragment {

    public static final String TAG = "InitLibraryFragment";

    private InteractionListener mListener;

    private static final int REQUEST_CAMERA_PERMISSION_CODE = 1;

    private View mProgressBar;
    private CameraPreviewLayout mCameraPreviewLayout;
    private ViewGroup mMainContent;
    private @Nullable View mFlashButton;

    private DeployCoreTask mDeployCoreTask;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            mListener = (InteractionListener) getActivity();
        } catch (ClassCastException ex) {
            throw new RuntimeException("Parent must implement " + ScanCardFragment.InteractionListener.class.getSimpleName());
        }
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_scan_card, container, false);

        mMainContent = root.findViewById(R.id.main_content);
        mProgressBar = root.findViewById(R.id.progress_bar);
        mCameraPreviewLayout = root.findViewById(R.id.card_recognition_view);
        mFlashButton = root.findViewById(R.id.iv_flash_id);

        View enterManuallyButton = root.findViewById(R.id.tv_enter_card_number_id);
        enterManuallyButton.setVisibility(View.VISIBLE);
        enterManuallyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View clickview) {
                if (mListener != null) mListener.onScanCardCanceled(ScanCardIntent.BACK_PRESSED);
            }
        });
        return root;
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mProgressBar.setVisibility(View.GONE);
        mMainContent.setVisibility(View.VISIBLE);
        mCameraPreviewLayout.setVisibility(View.VISIBLE);
        mCameraPreviewLayout.getSurfaceView().setVisibility(View.GONE);
        mCameraPreviewLayout.setBackgroundColor(Color.BLACK);
        if (mFlashButton != null) mFlashButton.setVisibility(View.GONE);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        RecognitionAvailabilityChecker.Result checkResult = RecognitionAvailabilityChecker.doCheck(getContext());
        if (checkResult.isFailedOnCameraPermission()) {
            if (savedInstanceState == null) {
                requestPermissions(new String[] {Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION_CODE);
            }
        } else {
            subscribeToInitCore(getActivity());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION_CODE:
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    subscribeToInitCore(getActivity());
                } else {
                    if (mListener != null ) mListener.onInitLibraryFailed(
                            new RecognitionUnavailableException(RecognitionUnavailableException.ERROR_NO_CAMERA_PERMISSION));
                }
                return;
            default:
                break;
        }
    }

    private void subscribeToInitCore(Context context) {
        if (mProgressBar != null) mProgressBar.setVisibility(View.VISIBLE);
        if (mDeployCoreTask != null) mDeployCoreTask.cancel(false);
        mDeployCoreTask = new DeployCoreTask(this);
        mDeployCoreTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mDeployCoreTask != null) {
            mDeployCoreTask.cancel(false);
            mDeployCoreTask = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mProgressBar = null;

    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    public interface InteractionListener {
        void onScanCardCanceled(@ScanCardIntent.CancelReason int actionId);
        void onInitLibraryFailed(Throwable e);
        void onInitLibraryComplete();
    }

    private static class DeployCoreTask extends AsyncTask<Void, Void, Throwable> {

        private final WeakReference<InitLibraryFragment> fragmentRef;

        @SuppressLint("StaticFieldLeak")
        private final Context appContext;

        DeployCoreTask(InitLibraryFragment parent) {
            this.fragmentRef = new WeakReference<InitLibraryFragment>(parent);
            this.appContext = parent.getContext().getApplicationContext();
        }

        @Override
        protected Throwable doInBackground(Void... voids) {
            try {
                RecognitionAvailabilityChecker.Result checkResult = RecognitionAvailabilityChecker.doCheck(appContext);
                if (checkResult.isFailed()) {
                    throw new RecognitionUnavailableException();
                }
                RecognitionCoreUtils.deployRecognitionCoreSync(appContext);
                if (!RecognitionCore.getInstance(appContext).isDeviceSupported()) {
                    throw new RecognitionUnavailableException();
                }
                return null;
            } catch (RecognitionUnavailableException e) {
                return e;
            }
        }

        @Override
        protected void onPostExecute(@Nullable Throwable lastError) {
            super.onPostExecute(lastError);
            InitLibraryFragment fragment = fragmentRef.get();
            if (fragment == null
                    || fragment.mProgressBar == null
                    || fragment.mListener == null) return;

            fragment.mProgressBar.setVisibility(View.GONE);
            if (lastError == null) {
                fragment.mListener.onInitLibraryComplete();
            } else {
                fragment.mListener.onInitLibraryFailed(lastError);
            }
        }
    }
}
