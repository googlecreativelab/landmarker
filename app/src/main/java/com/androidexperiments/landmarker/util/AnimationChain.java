package com.androidexperiments.landmarker.util;

import android.app.Activity;
import android.app.Fragment;
import android.os.Handler;
import android.view.animation.Animation;

/**
 * Simple class for an easier way to chain animations together
 */
public class AnimationChain extends SimpleAnimationListener
{
    private Runnable mNext;
    private Handler mHandler;
    private Activity mActivity;
    private long mDelay = 0;

    private boolean mShouldRun = true;

    /**
     * When you want the next animation in the chain to run immediately and don't
     * want to worry about dealing with your own Handler
     * @param next {@link Runnable} containing the next animation to run on UI Thread
     * @param activity {@link Activity} the activity calling the next runnable
     */
    public AnimationChain(Runnable next, Activity activity) {
        mNext = next;
        mActivity = activity;
    }

    public AnimationChain(Runnable next, Fragment fragment)
    {
        this(next, fragment.getActivity());
    }

    /**
     * Constructor to be used when you want to post a delay or use your own Handler
     * to control timing and such.
     * @param next
     * @param handler
     * @param delay
     */
    public AnimationChain(Runnable next, Handler handler, long delay)
    {
        mNext = next;
        mHandler = handler;
        mDelay = delay;
    }

    /**
     * If we still have an active chain in onPause or onStop we should
     * set shouldRun to false so that we don't trigger the runnables in the background
     * @param shouldRun
     */
    public void setShouldRun(boolean shouldRun) {
        this.mShouldRun = shouldRun;
    }

    @Override
    public void onAnimationEnd(Animation animation)
    {
        if(mShouldRun) {
            if(mHandler != null) {
                mHandler.postDelayed(mNext, mDelay);
            }
            else
                mActivity.runOnUiThread(mNext);
        }

        super.onAnimationEnd(animation);
    }
}
