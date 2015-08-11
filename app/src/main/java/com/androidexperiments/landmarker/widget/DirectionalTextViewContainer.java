package com.androidexperiments.landmarker.widget;

import android.content.Context;
import android.graphics.Point;
import android.location.Location;
import android.os.Handler;
import android.support.v4.view.GestureDetectorCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;

import com.google.creativelabs.androidexperiments.typecompass.R;
import com.androidexperiments.landmarker.data.NearbyPlace;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import de.greenrobot.event.EventBus;
import se.walkercrou.places.Place;

/**
 * Handles 4 textviews and displays them in NSEW orientation
 */
public class DirectionalTextViewContainer extends FrameLayout
{
    private static final String TAG = DirectionalTextViewContainer.class.getSimpleName();

    //defaults
    private int MIN_Y_MOVEMENT = -20;
    private int MAX_Y_MOVEMENT = -1000;
    private int TOTAL_Y_MOVEMENT = -980;


    @InjectView(R.id.dtv_north) DirectionalTextView mNorth;
    @InjectView(R.id.dtv_east) DirectionalTextView mEast;
    @InjectView(R.id.dtv_south) DirectionalTextView mSouth;
    @InjectView(R.id.dtv_west) DirectionalTextView mWest;

    ArrayList<NearbyPlace> mNorthernPlaces, mEasternPlaces, mSouthernPlaces, mWesternPlaces;

    private Handler mDrawingHandler = new Handler();
    private boolean mIsDrawing = true;

    private int mViewWidth = 0;

//    private double mCurrentDegrees = 0;

    public DirectionalTextViewContainer(Context context) {
        super(context);
    }

    public DirectionalTextViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DirectionalTextViewContainer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onFinishInflate()
    {
        super.onFinishInflate();
        ButterKnife.inject(this, this);

        mNorth.setDir("N");
        mEast.setDir("E");
        mWest.setDir("W");
        mSouth.setDir("S");

        setupMovementConstants();
        setupTouchListener();
    }

    private void setupMovementConstants()
    {
        WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Point windowSize = new Point();
        wm.getDefaultDisplay().getSize(windowSize);

        MAX_Y_MOVEMENT = -windowSize.y / 2;
        TOTAL_Y_MOVEMENT = MAX_Y_MOVEMENT - MIN_Y_MOVEMENT;
    }

    private void setupTouchListener()
    {
        final GestureDetectorCompat gd = new GestureDetectorCompat(getContext(), new GestureDetector.SimpleOnGestureListener(){
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e)
            {
                Log.d(TAG, "singleTap: " + getTappedView(e));
                EventBus.getDefault().post(new OnPlaceClickedEvent(getTappedView(e).getCurrentPlace()));
                return false;
            }
        });

        this.setOnTouchListener(new OnTouchListener()
        {
            float mStartY = 0.f;
            DirectionalTextView view = null;

            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                gd.onTouchEvent(event);

                int type = event.getActionMasked();
                float dif, percent;

                switch(type)
                {
                    case MotionEvent.ACTION_DOWN:
                        view = getTappedView(event);
                        Log.d(TAG, "onTouchDown: " + view.getDir());

                        mStartY = event.getRawY();
                        break;

                    case MotionEvent.ACTION_MOVE:
                        dif = mStartY - event.getRawY();
                        if(dif < MIN_Y_MOVEMENT && dif > MAX_Y_MOVEMENT)
                        {
                            percent = dif / TOTAL_Y_MOVEMENT;

                            if(view != null) {
                                view.updatePostition(percent);
                            }
                        }
                        break;

                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        if(view!= null)
                        {
                            dif = mStartY - event.getRawY();
                            percent = dif / TOTAL_Y_MOVEMENT;

                            if(percent > .5f)
                                view.springUp();
                            else
                                view.returnToPosition();

                            view = null;
                        }
                        break;
                }
                return true;
            }
        });
    }

    private DirectionalTextView getTappedView(MotionEvent e)
    {
        float tapX = e.getX();
        float nx = mNorth.getX(), ex = mEast.getX(),
                sx = mSouth.getX(), wx = mWest.getX();

        //the weird one - since south will move to complete opposite side of screen
        //depending on whether its to the right or left.
        if((tapX >= ex && tapX < sx) || (tapX >= ex && sx < mViewWidth * -2))
            return mEast;

        //everything else is pretty straight forward
        else if(tapX >= nx && tapX < ex)
            return mNorth;

        else if(tapX >= wx && tapX < nx)
            return mWest;

        //south is most finicky but if we test for all above cases, only remaining woudld be south
        else
            return mSouth;
    }

    public void updatePlaces(List<Place> places, Location lastLocation)
    {
        //wipe old places
        mNorthernPlaces = new ArrayList<>();
        mEasternPlaces = new ArrayList<>();
        mSouthernPlaces = new ArrayList<>();
        mWesternPlaces = new ArrayList<>();

        for(Place place : places)
        {
            Location placeLoc = new Location("placeLoc");
            placeLoc.setLatitude(place.getLatitude());
            placeLoc.setLongitude(place.getLongitude());

//            Log.d(TAG, "degrees to " + place.getName() + ": " + lastLocation.bearingTo(placeLoc) + " distance: " + lastLocation.distanceTo(placeLoc));

            float bearing = lastLocation.bearingTo(placeLoc);
            float distance = lastLocation.distanceTo(placeLoc);

            NearbyPlace newPlace = new NearbyPlace(distance, place.getName());

            //simple but useful
            if(bearing > -45.f && bearing < 45.f) // north
                mNorthernPlaces.add(newPlace);
            else if(bearing > 45.f && bearing < 135.f) // east
                mEasternPlaces.add(newPlace);
            else if(bearing < -45.f && bearing > -135.f) // west
                mWesternPlaces.add(newPlace);
            else
                mSouthernPlaces.add(newPlace);
        }

        mNorth.setPlaces(mNorthernPlaces);
        mEast.setPlaces(mEasternPlaces);
        mWest.setPlaces(mWesternPlaces);
        mSouth.setPlaces(mSouthernPlaces);
    }

    public void updateFakePlaces()
    {
        mNorth.setText("Empire State Building");
        mEast.setText("Williamsburg Bridge");
        mSouth.setText("One World Trade");
        mWest.setText("Chelsea Piers");
    }

    /**
     * degrees range from -180 -> 180, 0 being due EAST and -90 being north
     * @param degrees
     */
    public void updateView(double degrees)
    {
        //hack
        degrees = (degrees + 180) % 360 - 90;
        if(degrees < 0)
            degrees = 360 + degrees;

        if(mViewWidth == 0)
            mViewWidth = mEast.getWidth();

//        mCurrentDegrees = degrees;

        //180 north 0 south 270 east 90 west
        float DEGREE = 90.f;
        float northOffset = ((float) degrees - 180.f) / DEGREE;
        float westOffset = ((float) degrees - 270) / DEGREE;
        float eastOffset = ((float) degrees - 90) / DEGREE;

        //south is weird
        float southOffset = 0.f;
        if(degrees > 0.f && degrees < 90.f)
            southOffset = ((float)degrees) / DEGREE;
        else
            southOffset = ((float)degrees - 360) / DEGREE;

        mNorth.setTranslation(northOffset, mViewWidth);
        mWest.setTranslation(westOffset, mViewWidth);
        mEast.setTranslation(eastOffset, mViewWidth);
        mSouth.setTranslation(southOffset, mViewWidth);
    }

    public void animateIn()
    {
        if(this.getVisibility() == GONE)
        {
            Animation anim = AnimationUtils.loadAnimation(getContext(), R.anim.show_directional_text_views);
            this.startAnimation(anim);
            this.setVisibility(VISIBLE);
        }
    }

    public void startDrawing()
    {
        mIsDrawing = true;

        mDrawingHandler.post(new Runnable() {
            @Override
            public void run() {
                if(!mIsDrawing) return;

                mNorth.drawView();
                mWest.drawView();
                mEast.drawView();
                mSouth.drawView();

                mDrawingHandler.postDelayed(this, 16);
            }
        });
    }

    public void stopDrawing() {
        mIsDrawing = false;
    }

    public static class OnPlaceClickedEvent{
        public NearbyPlace place;
        public OnPlaceClickedEvent(NearbyPlace place) {
            this.place = place;
        }
    }
}
