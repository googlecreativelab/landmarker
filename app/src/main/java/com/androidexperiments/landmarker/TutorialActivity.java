package com.androidexperiments.landmarker;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;

import com.google.creativelabs.androidexperiments.typecompass.R;
import com.androidexperiments.landmarker.util.AnimationChain;
import com.androidexperiments.landmarker.util.SimpleAnimationListener;
import com.androidexperiments.landmarker.widget.TutorialSkylineView;

import butterknife.ButterKnife;
import butterknife.InjectView;

public class TutorialActivity extends BaseActivity {

    private static final String TAG = TutorialActivity.class.getSimpleName();

    @InjectView(R.id.tut_skyline)    TutorialSkylineView mSkyline;
    @InjectView(R.id.tut_hand_with_phone)    View mHandWithPhone;
    @InjectView(R.id.tut_hand_pointing)    View mHandPointing;
    @InjectView(R.id.tut_header_text)    TextView mHeaderText;
    @InjectView(R.id.tut_screen_1)    View mScreen1;
    @InjectView(R.id.tut_screen_2)    View mScreen2;
    @InjectView(R.id.tut_screen_3)    View mScreen3;
    @InjectView(R.id.tut_screen_4)    View mScreen4;

    private boolean mIsFirstRun = true;

    private Handler mAnimationHandler = new Handler();

    private AnimationChain mCurrentAnimationChain;

    private float mHandHeight, mScreenHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tutorial);

        ButterKnife.inject(this);

        mHandHeight = getResources().getDimensionPixelSize(R.dimen.tut_hand_pointing_height);
        mScreenHeight = getResources().getDimensionPixelSize(R.dimen.tut_screen_height);
    }

    @Override
    protected void onResume() {
        if (mIsFirstRun) {
            startTutorial();
            mIsFirstRun = false;
        }
        super.onResume();
    }

    @Override
    protected void onPause()
    {
        if(mCurrentAnimationChain != null) {
            mCurrentAnimationChain.setShouldRun(false);
        }
        super.onPause();
    }

    private void startTutorial() {
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.tut_show_device);
        mCurrentAnimationChain = new AnimationChain(mStep2Runnable, mAnimationHandler, 1250);
        anim.setAnimationListener(mCurrentAnimationChain);
        mHandWithPhone.startAnimation(anim);

        Animation showText = AnimationUtils.loadAnimation(this, R.anim.show_from_top);
        mHeaderText.setVisibility(View.VISIBLE);
        mHeaderText.startAnimation(showText);
    }

    private Runnable mStep2Runnable = new Runnable() {
        @Override
        public void run() {
            showNextText("MOVE IT AROUND HORIZONTALLY");
            mSkyline.goToEnd(mScreen1, mScreen2, mStep3Runnable, mAnimationHandler, 500);
        }
    };

    private Runnable mStep3Runnable = new Runnable() {
        @Override
        public void run() {
            showNextText("SWIPE DOWN FOR A NEW LANDMARK");
            step3();
        }
    };


    private Runnable mStep4Runnable = new Runnable() {
        @Override
        public void run() {
            showNextText("TAP TO VIEW THE LANDMARK IN GOOGLE MAPS");
            step4();
        }
    };

    private Runnable mLastStepRunnable = new Runnable() {
        @Override
        public void run() {
            goToMainActivity();
        }
    };

    private void step3() {

        mHandPointing.setVisibility(View.VISIBLE);

        //animate the hand
        DecelerateInterpolator decel = new DecelerateInterpolator(1.5f);

        ObjectAnimator moveUp = ObjectAnimator.ofFloat(mHandPointing, "translationY", mHandHeight, 0);
        moveUp.setInterpolator(decel);
        moveUp.setDuration(550);

        ObjectAnimator moveDown = ObjectAnimator.ofFloat(mHandPointing, "translationY", 0, mHandHeight / 5);
        moveDown.setInterpolator(decel);
        moveDown.setDuration(250);

        ObjectAnimator moveBackUp = ObjectAnimator.ofFloat(mHandPointing, "translationY", mHandHeight / 5, mHandHeight / 10);
        moveDown.setInterpolator(new AccelerateDecelerateInterpolator());
        moveDown.setDuration(250);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(moveUp, moveDown, moveBackUp);
        set.start();


        //animate the screen

        ObjectAnimator screenDown = ObjectAnimator.ofFloat(mScreen2, "translationY", 0, mScreenHeight / 5);
        screenDown.setInterpolator(decel);
        screenDown.setDuration(250);

        ObjectAnimator screenUp = ObjectAnimator.ofFloat(mScreen2, "translationY", mScreenHeight / 5, -mScreenHeight / 2);
        screenUp.setInterpolator(decel);
        screenUp.setDuration(250);
        screenUp.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mScreen2.setVisibility(View.GONE);
            }
        });

        ObjectAnimator screen3Down = ObjectAnimator.ofFloat(mScreen3, "translationY", -mScreenHeight /2, 0);
        screen3Down.setInterpolator(new AccelerateInterpolator(1.5f));
        screen3Down.setDuration(250);
        screen3Down.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mScreen3.setVisibility(View.VISIBLE);
            }
        });

        AnimatorSet set2 = new AnimatorSet();
        set2.playSequentially(screenDown, screenUp, screen3Down);
        set2.setStartDelay(550);
        set2.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mAnimationHandler.postDelayed(mStep4Runnable, 1750);
            }
        });
        set2.start();
    }

    private void step4() {
        //animate hand
        DecelerateInterpolator decel = new DecelerateInterpolator(1.5f);
        ObjectAnimator tapOn = ObjectAnimator.ofFloat(mHandPointing, "translationY", mHandHeight / 10, -mHandHeight / 10);
        tapOn.setInterpolator(decel);
        tapOn.setDuration(250);

        ObjectAnimator tapOff = ObjectAnimator.ofFloat(mHandPointing, "translationY", -mHandHeight / 10, mHandHeight / 3);
        tapOff.setDuration(350);

        AnimatorSet set = new AnimatorSet();
        set.playSequentially(tapOn, tapOff);
        set.start();

        //bring in that beautiful graphic
        AlphaAnimation alphaIn = new AlphaAnimation(0.f, 1.f);
        alphaIn.setDuration(500);
        alphaIn.setStartOffset(350);
        alphaIn.setFillBefore(true);
        alphaIn.setFillEnabled(true);
        alphaIn.setAnimationListener(new SimpleAnimationListener() {
            @Override
            public void onAnimationEnd(Animation animation) {
                mAnimationHandler.postDelayed(mLastStepRunnable, 2500);
            }
        });
        mScreen4.setVisibility(View.VISIBLE);
        mScreen4.startAnimation(alphaIn);
    }

    private void goToMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);

        this.finish();
    }

    /**
     * convenience method for swapping in new text to header textview
     * @param text
     */
    private void showNextText(final String text) {
        final Animation hideText = AnimationUtils.loadAnimation(this, R.anim.hide_to_top);
        final Animation showText = AnimationUtils.loadAnimation(this, R.anim.show_from_top);

        hideText.setAnimationListener(new SimpleAnimationListener(){
            @Override
            public void onAnimationEnd(Animation animation) {
                hideText.setAnimationListener(null);

                mHeaderText.setText(text);
                mHeaderText.startAnimation(showText);
            }
        });
        mHeaderText.startAnimation(hideText);
    }


}
