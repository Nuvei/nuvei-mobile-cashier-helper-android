package com.nuvei.cashier.ndk;

import androidx.annotation.IntRange;

public interface DisplayConfiguration {
    @RecognitionConstants.WorkAreaOrientation
    int getNativeDisplayRotation();

    @IntRange(from=0, to=360)
    int getPreprocessFrameRotation(int width, int height);
}
