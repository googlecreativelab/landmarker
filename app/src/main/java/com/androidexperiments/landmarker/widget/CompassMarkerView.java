package com.androidexperiments.landmarker.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.widget.TextView;

import com.google.creativelabs.androidexperiments.typecompass.R;

/**
 * letter and a silly line
 */
public class CompassMarkerView extends TextView {
    private Paint mPaint;
    private Paint mLightPaint;

    private float mPadding = 0.f, mLineWidth = 0.f;

    public CompassMarkerView(Context context) {
        super(context);
    }

    public CompassMarkerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CompassMarkerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mPaint = new Paint();
        mPaint.setColor(0xffffffff);
        mPaint.setStyle(Paint.Style.FILL);

        mLightPaint = new Paint();
        mLightPaint.setColor(0x99ffffff);
        mLightPaint.setStyle(Paint.Style.FILL);

        mPadding = getResources().getDimension(R.dimen.compass_marker_padding);
        mLineWidth = getResources().getDimension(R.dimen.compass_marker_line_width);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        //vars
        int halfWidth = canvas.getWidth() / 2;
        int quartWidth = halfWidth / 2;
        int halfLine = (int) mLineWidth / 2;
        int height = canvas.getHeight();

        //main
        canvas.drawRect(halfWidth - halfLine, 65, halfWidth + halfLine, height, mPaint);

        //secondary
        canvas.drawRect(quartWidth - halfLine, 90, quartWidth + halfLine, height, mLightPaint);
        canvas.drawRect(halfWidth + quartWidth - halfLine, 90, halfWidth + quartWidth + halfLine, height, mLightPaint);
        canvas.drawRect(canvas.getWidth() - halfLine, 90, canvas.getWidth(), height, mLightPaint);
    }
}
