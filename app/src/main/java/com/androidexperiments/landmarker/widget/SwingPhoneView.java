package com.androidexperiments.landmarker.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.google.creativelabs.androidexperiments.typecompass.R;
import com.androidexperiments.landmarker.util.SimpleAnimationListener;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import de.greenrobot.event.EventBus;

/**
 * Figure 8 animation
 */
public class SwingPhoneView extends RelativeLayout
{
    private static final String TAG = SwingPhoneView.class.getSimpleName();

    @InjectView(R.id.swipe_phone_figure_8) ImageView mFigure8View;
    @InjectView(R.id.swipe_phone_image) ImageView mPhoneImageView;
    @InjectView(R.id.swing_phone_text) TextView mText;

    private Animation mScaleIn, mFromBottom, mScaleOut;

    public SwingPhoneView(Context context) {
        super(context);
    }

    public SwingPhoneView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SwingPhoneView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        ButterKnife.inject(this,this);

        mScaleIn = AnimationUtils.loadAnimation(getContext(), R.anim.scale_in);
        mScaleIn.setInterpolator(new OvershootInterpolator(1.2f));

        mScaleOut = AnimationUtils.loadAnimation(getContext(), R.anim.scale_out);
        mScaleOut.setInterpolator(new AnticipateInterpolator(1.2f));

        mFromBottom = AnimationUtils.loadAnimation(getContext(), R.anim.show_from_bottom);
        mFromBottom.setStartOffset(250);
    }

    @OnClick(R.id.swipe_phone_image)
    public void onClickPhone() {
        startLoop();
    }

    public void animateIn() {
        this.setVisibility(VISIBLE);

        mFigure8View.startAnimation(mScaleIn);
        mText.startAnimation(mFromBottom);

        startLoop();
    }

    private void startLoop()
    {
        float pi =  (float)Math.PI;

        ValueAnimator animator = ValueAnimator.ofFloat(pi / 2, -pi - pi / 2);
        animator.setDuration(1750);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            float lastVal = 0.f;

            @Override
            public void onAnimationUpdate(ValueAnimator animation)
            {
                double t = Double.parseDouble(animation.getAnimatedValue().toString());
                double scale = 2 / (3 - Math.cos(2.*t));
                float unscaledX = (float)Math.cos(t);
                float x = (float)scale * unscaledX;
                float y = (float)scale * (float)Math.sin(2 * t) / 2;

                mPhoneImageView.setTranslationX(x * (mFigure8View.getWidth() / 2));
                mPhoneImageView.setTranslationY(y * (mFigure8View.getHeight()));

                float rotation;
                float half = .55f;

                // moving right
                if(x >= lastVal) {
                    if(x <= 0.f) {
                        if(x < -half)
                            rotation = map(x, -1, -half, -90, 0);
                        else
                            rotation = map(x, -half, 0f, 0, 30);
                    }
                    else {
                        if(x > half)
                            rotation = map(x, half, 1, 0, -90);
                        else
                            rotation = map(x, 0, half, 30, 0);
                    }
                }
                // moving left
                else {
                    if(x >= 0.f) {
                        if(x > half)
                            rotation = map(x, 1, half, -90, -180);
                        else
                            rotation = map(x, half, 0, -180, -210);
                    }
                    else {
                        if (x < -half)
                            rotation = map(x, -1, -half, -90, -180);
                        else
                            rotation = map(x, -half, 0, -180, -210);
                    }
                }

                mPhoneImageView.setRotation(rotation);
                lastVal = x;
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animateOut();
            }
        });
        animator.setInterpolator(null);
        animator.setRepeatCount(2);//ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.RESTART);
        animator.start();
    }

    private void animateOut()
    {
        this.setVisibility(GONE);
        this.startAnimation(mScaleOut);
        mScaleOut.setAnimationListener(new SimpleAnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                EventBus.getDefault().post(new OnAnimateOutCompleteEvent());
            }
        });
    }

    public final float map(float value, float start1, float stop1, float start2, float stop2) {
        return start2 + (stop2 - start2) * ((value - start1) / (stop1 - start1));
    }

    //event bus
    public static class OnAnimateOutCompleteEvent { }
}
