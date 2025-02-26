package com.nuvei.cashier.ui.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatButton;

public class ButtonWithTopBorder extends AppCompatButton {

    private Paint mTopLinePaint;

    public ButtonWithTopBorder(Context context) {
        super(context);
        init();
    }

    public ButtonWithTopBorder(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ButtonWithTopBorder(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mTopLinePaint = new Paint();
        mTopLinePaint.setStrokeWidth(getResources().getDisplayMetrics().density);
        mTopLinePaint.setStyle(Paint.Style.STROKE);
        mTopLinePaint.setColor(0x61ffffff);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawLine(0, 0, getWidth(), 0, mTopLinePaint);
    }
}
