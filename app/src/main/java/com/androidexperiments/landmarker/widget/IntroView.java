package com.androidexperiments.landmarker.widget;

import android.content.Context;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.androidexperiments.landmarker.util.SimpleAnimationListener;
import com.google.creativelabs.androidexperiments.typecompass.R;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Intro view for app startup
 */
public class IntroView extends RelativeLayout {
    private static final String TAG = IntroView.class.getSimpleName();

    @Bind(R.id.intro_compass)
    View mCompass;
    @Bind(R.id.intro_compass_spin)
    View mCompassSpinner;
    @Bind(R.id.intro_load_text)
    TextView mSubTextView;

    private Animation mSpinnerAnim;
    private Handler mSpinnerHandler;
    private boolean mIsDone = false;

    public IntroView(Context context) {
        super(context);
    }

    public IntroView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IntroView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        ButterKnife.bind(this, this);

        mSpinnerAnim = AnimationUtils.loadAnimation(getContext(), R.anim.intro_compass_spinner);
        mSpinnerHandler = new Handler();

        mSubTextView.setVisibility(INVISIBLE);
        mCompass.setVisibility(INVISIBLE);

        super.onFinishInflate();
    }

    public void animateIn(final Runnable completeRunner) {
        Animation scale = AnimationUtils.loadAnimation(this.getContext(), R.anim.intro_compass_in);
        scale.setInterpolator(new DecelerateInterpolator(1.5f));
        mCompass.startAnimation(scale);
        mCompass.setVisibility(VISIBLE);

        //subtext
        Animation fromBottom = AnimationUtils.loadAnimation(getContext(), R.anim.show_from_bottom);
        fromBottom.setInterpolator(new DecelerateInterpolator(2.f));
        fromBottom.setStartOffset(750);
        fromBottom.setAnimationListener(new SimpleAnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {
                mSubTextView.setVisibility(VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animation animation) {
                startSpinner();
                completeRunner.run();
            }
        });
        mSubTextView.startAnimation(fromBottom);
    }

    private void startSpinner() {
        mSpinnerHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mIsDone)
                    return;
                mCompassSpinner.startAnimation(mSpinnerAnim);
                mSpinnerHandler.postDelayed(this, 1500);
            }
        });
    }

    public void animateOut() {
        Animation hide = AnimationUtils.loadAnimation(getContext(), R.anim.hide_intro_view);
        mIsDone = true;
        this.startAnimation(hide);
        this.setVisibility(GONE);
    }

    public void setIsFindingPlaces() {
        mSubTextView.setText(R.string.intro_finding_places);
    }
}
