package com.androidexperiments.landmarker.widget;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.androidexperiments.landmarker.data.NearbyPlace;
import com.google.creativelabs.androidexperiments.typecompass.R;

import java.util.ArrayList;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import de.greenrobot.event.EventBus;

/**
 * view that takes big text and little guy
 */
public class DirectionalTextView extends FrameLayout {
    private static final String TAG = DirectionalTextView.class.getSimpleName();

    /**
     * largest amount allowed to move across screen with easing, larger amounts, like
     * when south jumps from last to first, will just be translated and not eased
     */
    private static final float MAX_JUMP = 1920.f;

    /**
     * Name of prefs object we are using.
     * TODO - if shared prefs used anywhere else, extract and put into constants file
     */
    private static final String NAME_SHARED_PREFS = "LandmarkerPrefs";

    /**
     * Key for SharedPrefs storage of whether we are in metric mode or not
     */
    private static final String KEY_IS_METRIC = "key_is_metric";

    @Bind(R.id.dtv_main_text)
    TextView mMainText;
    @Bind(R.id.dtv_marker_view)
    TextView mMarkerText;
    @Bind(R.id.dtv_distance_text)
    TextView mDistanceText;

    private float mTranslationX = 0.f;
    private float mGotoX = 0.f;
    private float mLastX = 0.f;

    private ArrayList<NearbyPlace> mPlaces;
    private NearbyPlace mCurrentPlace;

    private float mDistanceInKilometers;
    private float mDistanceInMiles;

    private boolean mIsMetric = true;

    public DirectionalTextView(Context context) {
        super(context);
    }

    public DirectionalTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DirectionalTextView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        ButterKnife.bind(this, this);

        //restore saved metric settings
        SharedPreferences prefs = getContext().getSharedPreferences(NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        mIsMetric = prefs.getBoolean(KEY_IS_METRIC, true);
        setDistanceText();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        EventBus.getDefault().unregister(this);
    }

    public void setDir(String dir) {
        mMarkerText.setText(dir);
    }

    public void setPlaces(ArrayList<NearbyPlace> places) {
        mPlaces = places;
        if (mPlaces.size() > 0)
            setPlace(mPlaces.get(0));
        else
            setEmptyPlace();
    }

    @OnClick(R.id.dtv_distance_text)
    public void onDistanceClicked() {
        mIsMetric = !mIsMetric;

        SharedPreferences prefs = getContext().getSharedPreferences(NAME_SHARED_PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_IS_METRIC, mIsMetric);
        editor.apply();

        EventBus.getDefault().post(new OnDistanceUnitsChangedEvent(mIsMetric));
    }

    public void onEvent(OnDistanceUnitsChangedEvent event) {
        mIsMetric = event.isMetric;
        setDistanceText();
    }

    public void setDistance(float distance) {
        mDistanceInKilometers = distance / 1000;
        mDistanceInMiles = getMiles(distance);

        setDistanceText();
    }

    private void setDistanceText() {
        if (mIsMetric)
            mDistanceText.setText(String.format("%.1f", mDistanceInKilometers) + " km");
        else
            mDistanceText.setText(String.format("%.1f", mDistanceInMiles) + " mi");
    }

    private void setEmptyPlace() {
        this.setText("");
        mMainText.setTextColor(0xaaffffff);
        mMainText.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.directional_tv_main_text_size_small));
    }

    private void setPlace(NearbyPlace place) {
        mCurrentPlace = place;

        this.setText(place.getName());
        this.setDistance(place.getDistance());
    }

    public void setText(String name) {
        mMainText.setText(name);
    }


    private float getMiles(float meters) {
        return meters * 0.000621371192f;
    }

    public NearbyPlace getCurrentPlace() {
        return mCurrentPlace;
    }

    @Override
    public void setTranslationY(float translationY) {
        mMainText.setTranslationY(translationY);
    }

    private void setRandomPlace() {
        int size = mPlaces.size();
        if (size > 0)
            setPlace(mPlaces.get((int) Math.floor(Math.random() * size)));
    }

    /**
     * move the view the proper amount
     *
     * @param offset    -1 to 1 will be displaying on screen somehow
     * @param viewWidth total width of each view to mulitply by
     */
    public void setTranslation(float offset, int viewWidth) {
        mGotoX = (offset * viewWidth);

        if (Math.abs(mGotoX - mLastX) > MAX_JUMP) {
            mTranslationX = mGotoX;
            this.setTranslationX(mTranslationX);
        }

        mLastX = mGotoX;
    }

    public void springUp() {
        final ObjectAnimator anim = ObjectAnimator.ofFloat(mMainText, "translationY", mMainText.getTranslationY(), 0.f);
        anim.setInterpolator(new OvershootInterpolator(12.f));
        anim.setDuration(300);
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                if (animation.getAnimatedFraction() > 2.0f) {
                    setRandomPlace();
                    anim.removeUpdateListener(this);
                }
            }
        });
        anim.start();

        ValueAnimator distance = ValueAnimator.ofFloat(mDistanceText.getAlpha(), 1.f);
        distance.setInterpolator(new OvershootInterpolator(12.f));
        distance.setDuration(300);
        distance.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float frac = (float) animation.getAnimatedValue();
                mDistanceText.setAlpha(frac);
                mDistanceText.setScaleX(frac);
                mDistanceText.setScaleY(frac);
            }
        });
        distance.start();
    }

    public void returnToPosition() {
        ObjectAnimator anim = ObjectAnimator.ofFloat(mMainText, "translationY", mMainText.getTranslationY(), 0.f);
        anim.setDuration(250);
        anim.start();

        ValueAnimator distance = ValueAnimator.ofFloat(mDistanceText.getAlpha(), 1.f);
        distance.setDuration(250);
        distance.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float frac = (float) animation.getAnimatedValue();
                mDistanceText.setAlpha(frac);
                mDistanceText.setScaleX(frac);
                mDistanceText.setScaleY(frac);
            }
        });
        distance.start();
    }

    public void drawView() {
        mTranslationX += (mGotoX - mTranslationX) * 0.12f;
        this.setTranslationX((int) mTranslationX);
    }

    public String getDir() {
        return mMarkerText.getText().toString();
    }

    public void updatePostition(float percent) {
        int TOTAL_Y_MOVEMENT = -480;
        float HALF_PI = (float) Math.PI / 2.f;

        float v = (float) Math.sin(percent * HALF_PI) * -(TOTAL_Y_MOVEMENT / 2);
        this.setTranslationY(v);

        float reverse = 1.f - percent;

        mDistanceText.setAlpha(reverse);
        mDistanceText.setScaleX(reverse);
        mDistanceText.setScaleY(reverse);
    }

    public static class OnDistanceUnitsChangedEvent {
        public boolean isMetric;

        public OnDistanceUnitsChangedEvent(boolean isMetric) {
            this.isMetric = isMetric;
        }
    }
}
