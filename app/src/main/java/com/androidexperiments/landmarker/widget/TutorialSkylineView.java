package com.androidexperiments.landmarker.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.LinearLayout;

import com.google.creativelabs.androidexperiments.typecompass.R;

/**
 * Scrollable skyline vector
 */
public class TutorialSkylineView extends LinearLayout {
    private static final String TAG = TutorialSkylineView.class.getSimpleName();

    private int mMaxWidth;
    private int mWidth;

    public TutorialSkylineView(Context context) {
        super(context);
        init();
    }

    public TutorialSkylineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public TutorialSkylineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        this.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                getViewTreeObserver().removeOnGlobalLayoutListener(this);
                mMaxWidth = (int) getResources().getDimension(R.dimen.tut_full_skyline_width);//TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, , getResources().getDisplayMetrics());
                mWidth = getWidth();
            }
        });
    }

    /**
     * used for animating from the beginning of image to the end
     *
     * @param screen1 first screen visible on phone
     * @param screen2 second screen to come into view with background animation
     * @param next    next step of animation to run
     * @param handler handle that will post next runnable
     * @param delay
     */
    public void goToEnd(final View screen1, final View screen2, final Runnable next, final Handler handler, final int delay) {
        final int sw = screen1.getWidth(); //screen widths
        screen2.setVisibility(VISIBLE);

        ValueAnimator animator = ValueAnimator.ofInt(0, -mMaxWidth + mWidth);
        animator.setDuration(2000);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                for (int i = 0; i < getChildCount(); i++) {
                    getChildAt(i).setTranslationX((int) animation.getAnimatedValue());
                }
                screen1.setTranslationX(-sw * animation.getAnimatedFraction());
                screen2.setTranslationX(-sw * animation.getAnimatedFraction() + sw);

                postInvalidate();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                handler.postDelayed(next, delay);
            }
        });
        animator.start();
    }

}
