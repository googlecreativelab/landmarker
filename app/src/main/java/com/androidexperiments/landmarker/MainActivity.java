package com.androidexperiments.landmarker;

import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.creativelabs.androidexperiments.typecompass.R;
import com.androidexperiments.landmarker.data.NearbyPlace;
import com.androidexperiments.landmarker.sensors.HeadTracker;
import com.androidexperiments.landmarker.util.HeadTransform;
import com.androidexperiments.landmarker.widget.DirectionalTextViewContainer;
import com.androidexperiments.landmarker.widget.IntroView;
import com.androidexperiments.landmarker.widget.SwingPhoneView;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnClick;
import de.greenrobot.event.EventBus;
import se.walkercrou.places.GooglePlaces;
import se.walkercrou.places.Place;


public class MainActivity extends BaseActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener
{
    private static final String TAG = MainActivity.class.getSimpleName();

    //go to https://code.google.com/apis/console to register an app and get a key!
    private static final String PLACES_API_KEY = Secrets.PLACES_API_KEY;

    private static final String STATE_RESOLVING_ERROR = "resolving_error";

    private static final double MAX_RADIUS = 1000;

    private static final int REQUEST_CHECK_SETTINGS = 100;

    /**
     * attempts at finding a location with decent accuracy
     */
    private static final int MAX_UPDATE_TRIES = 5;

    /**
     * if a location is older than an hour, try and get a new one
     */
    private static final int MIN_AGE_IN_HOURS = 1;

    private GoogleApiClient mGoogleApiClient;

    private boolean mResolvingError = false;

    private Location mLastLocation;
    private GooglePlaces mPlacesApi;

    @InjectView(R.id.intro_view) IntroView mIntroView;
    @InjectView(R.id.swing_phone_view) SwingPhoneView mSwingPhoneView;
    @InjectView(R.id.directional_text_view_container) DirectionalTextViewContainer mDirectionalTextViewContainer;
    @InjectView(R.id.maps_button_view_container) View mMapsButtonViewContainer;

    private NearbyPlace mCurrentPlace;

    private boolean mIsFirstRun = true;
    private boolean mIsConnectedToGApi = false;
    private boolean mIsReadyToCheckLastLocation = false;
    private LocationRequest mLocationReq;

    private HeadTracker mHeadTracker;
    private HeadTransform mHeadTransform;
    private Handler mTrackingHandler = new Handler();
    private boolean mIsTracking = false;
    private float[] mEulerAngles = new float[3];

    private boolean mHasPlaces = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mResolvingError = savedInstanceState != null && savedInstanceState.getBoolean(STATE_RESOLVING_ERROR, false);
        mHasPlaces = false;

        initViews();
        initSensors();

        buildGoogleApiClient();
        buildPlacesApi();
    }

    private void initViews() {
        ButterKnife.inject(this);

        mSwingPhoneView.setVisibility(View.GONE);
        mDirectionalTextViewContainer.setVisibility(View.GONE);
    }

    private void initSensors()
    {
        mHeadTracker = HeadTracker.createFromContext(this);
        mHeadTransform = new HeadTransform();
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
    }

    private void buildPlacesApi() {
        mPlacesApi = new GooglePlaces(PLACES_API_KEY);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!mResolvingError && !mGoogleApiClient.isConnected()) {  // more about this later
            Log.d(TAG, "onStart() && Api.connect()");
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        //events
        EventBus.getDefault().register(this);

        //sensors
        mHeadTracker.startTracking();

        //drawing
        mDirectionalTextViewContainer.startDrawing();

        //animateIn
        if(mIsFirstRun)
        {
            animateTitleIn();
            mIsFirstRun = false;
            return;
        }

        //resuming from pause/maps
        if(mHasPlaces)
            startTracking();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_RESOLVING_ERROR, mResolvingError);
    }

    @Override
    protected void onPause() {
        super.onPause();

        EventBus.getDefault().unregister(this);

        mIsTracking = false;
        mHeadTracker.stopTracking();

        mDirectionalTextViewContainer.stopDrawing();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
    }

    //butterknife

    @OnClick(R.id.maps_button_view)
    public void onMapsButtonClick()
    {
        if(mCurrentPlace == null)
        {
            Log.w(TAG, "No currentPlace available - must be empty. Ignore click.");
            return;
        }

        try {
            Intent intent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("geo:0,0?q=" + URLEncoder.encode(mCurrentPlace.getName(), "UTF-8"))
            );
            //cheating!
            intent.setPackage("com.google.android.apps.maps");
            startActivity(intent);
        }
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
    }

    @OnClick(R.id.maps_button_close)
    public void onMapsViewCloseClicked()
    {
        hideMapsButtonView();
    }

    @OnClick(R.id.maps_button_view_container)
    public void onContainerClick() {
        //do nothing - just need registered for onClick so it doesnt get passed through
    }

    //overrides

    @Override
    public void onBackPressed()
    {
        if(mMapsButtonViewContainer.getVisibility() == View.VISIBLE)
            hideMapsButtonView();
        else
            super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_CHECK_SETTINGS)
        {
            if(resultCode == RESULT_OK) {
                //settings have been enabled, continue forward!
                setLocationListener();
            }
            else {
                //we need location enabled for this app to work, so exit if we can't
                Toast.makeText(
                        this,
                        "Location Services need to be enabled for app to function. Please enable and try again.",
                        Toast.LENGTH_LONG)
                        .show();

                this.finish();
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    //event bus

    /**
     * handle when a place is clicked
     * @param event custom EventBus event
     */
    public void onEvent(DirectionalTextViewContainer.OnPlaceClickedEvent event)
    {
        if(event.place == null) {
            Log.w(TAG, "ignoring because no place is currently available.");
            return;
        }

        mCurrentPlace = event.place;
        showMapsButtonView();
    }

    //private api

    private void animateTitleIn()
    {
        final Runnable completeRunner = new Runnable() {
            @Override
            public void run() {
                if(mIsConnectedToGApi)
                    checkLastLocation();
                else
                    mIsReadyToCheckLastLocation = true;
            }
        };

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mIntroView.animateIn(completeRunner);
                    }
                });
            }
        }, 500);
    }

    private void showMapsButtonView()
    {
        mMapsButtonViewContainer.setVisibility(View.VISIBLE);
        Animation anim = new AlphaAnimation(0.f, 1.f);
        anim.setDuration(300);
        mMapsButtonViewContainer.startAnimation(anim);
    }

    private void hideMapsButtonView()
    {
        mMapsButtonViewContainer.setVisibility(View.GONE);
        Animation anim = new AlphaAnimation(1.f, 0.f);
        anim.setDuration(300);
        mMapsButtonViewContainer.startAnimation(anim);
    }

    /**
     * method for refreshing content from Places API.
     * will check location if its latest and do as needed
     */
    private void checkLastLocation()
    {
        mLocationReq = new LocationRequest();
        mLocationReq.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationReq.setInterval(1000);
        mLocationReq.setFastestInterval(5000);
        mLocationReq.setNumUpdates(MAX_UPDATE_TRIES);

        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

        if(mLastLocation == null) {
            checkSettings();
            return;
        }

        //it exists, see how old it is

        int hours = getLocationAgeHours(mLastLocation);
        Log.d(TAG, mLastLocation + "\nHours since update: " + hours);

        if(hours > MIN_AGE_IN_HOURS) { // || seconds > 15 ) //for testing
            setLocationListener();
            return;
        }

        //location is fine, update places
        getNewPlaces();
    }

    private int getLocationAgeHours(Location loc)
    {
        long duration  = (SystemClock.elapsedRealtimeNanos() - loc.getElapsedRealtimeNanos()) / 1000000L;
        int seconds = (int) Math.floor(duration / 1000);

//        Log.d(TAG, "getLocationAge() elapsed: " + (SystemClock.elapsedRealtimeNanos() / 1000000L)  + " location: " +  (loc.getElapsedRealtimeNanos() / 1000000L) + " seconds: " + seconds);

        return (int) Math.floor(seconds / 60 / 60);
    }

    private void checkSettings()
    {
        //get settings request for our location request
        LocationSettingsRequest req = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationReq)
                .build();

        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, req);
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        setLocationListener();
                        break;

                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            Log.e(TAG, e.getLocalizedMessage());
                        }
                        break;

                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't animateIn the dialog.
                        break;
                }
            }
        });
    }

    private void setLocationListener()
    {
        Log.d(TAG, "setLocationListener() " + mLocationReq);

        PendingResult<Status> result = LocationServices.FusedLocationApi.requestLocationUpdates(
                mGoogleApiClient,
                mLocationReq,
                new LocationListener() {

                    int numTries = 0;

                    @Override
                    public void onLocationChanged(Location location)
                    {
                        numTries++;

                        Log.d(TAG, "onLocationChanged() attempt: " + numTries + " :: " + location);

                        if(getLocationAgeHours(location) <= MIN_AGE_IN_HOURS || numTries == MAX_UPDATE_TRIES) {
                            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
                            mLastLocation = location;
                            getNewPlaces();
                        }
                    }
                }
        );

        result.setResultCallback(new ResultCallback<Status>() {
            @Override
            public void onResult(Status status) {
                Log.d(TAG, "setLocationListener() result status: " + status);
            }
        });
    }

    private void getNewPlaces()
    {
        //update introview
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mIntroView.setIsFindingPlaces();
            }
        });

        //find some places!
        new AsyncTask<Void, Void, List<Place>>()
        {
            @Override
            protected List<Place> doInBackground(Void... params)
            {
                List<Place> places = null;

                try {
                    places = mPlacesApi.getNearbyPlaces(mLastLocation.getLatitude(), mLastLocation.getLongitude(), MAX_RADIUS, 60);
                }
                catch(Exception e) {
                    //if getNearbyPlaces fails, return null and directional will do what it needs to
                    Log.e(TAG, e.getLocalizedMessage());
                    e.printStackTrace();
                }
                return places;
            }

            @Override
            protected void onPostExecute(List<Place> places)
            {
                if(places == null)
                {
                    Toast.makeText(
                            MainActivity.this,
                            "There are no places near you - Please try again later.",
                            Toast.LENGTH_LONG
                    ).show();

                    goBackToSplash();
                    return;
                }

                mHasPlaces = true;
                startTracking();

                mDirectionalTextViewContainer.updatePlaces(places, mLastLocation);

                showSwingPhoneView();
            }
        }.execute();
    }

    private void showSwingPhoneView() {
        mIntroView.animateOut();

        //animate in triggers its own animate out once completed, the next method
        mSwingPhoneView.animateIn();
    }

    public void onEvent(SwingPhoneView.OnAnimateOutCompleteEvent event) {
        mDirectionalTextViewContainer.animateIn();
    }

    private void goBackToSplash() {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); //unneccessary with finish()?
        startActivity(intent);

        this.finish();
    }

    private void startTracking()
    {
        mIsTracking = true;

        mTrackingHandler.post(new Runnable() {
            @Override
            public void run() {
                if(!mIsTracking) return;

                mHeadTracker.getLastHeadView(mHeadTransform.getHeadView(), 0);
                mHeadTransform.getEulerAngles(mEulerAngles, 0);

                runOnUiThread(updateDirectionalTextView);

                mTrackingHandler.postDelayed(this, 100);
            }
        });
    }

    private Runnable updateDirectionalTextView = new Runnable() {
        @Override
        public void run() {
            mDirectionalTextViewContainer.updateView(Math.toDegrees(mEulerAngles[1]));
        }
    };

    //google api stuffs

    @Override
    public void onConnected(Bundle bundle)
    {
        Log.d(TAG, "onConnected() " + (bundle != null ? bundle.toString() : "null"));

        mIsConnectedToGApi = true;

        if(mIsReadyToCheckLastLocation) {
            checkLastLocation();
            mIsReadyToCheckLastLocation = false;
        }
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        Log.d(TAG, "onConnectionSuspended() " + i);
        mIsConnectedToGApi = false;
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        Log.e(TAG, "onConnectionFailed() " + connectionResult);
        GooglePlayServicesUtil.getErrorDialog(connectionResult.getErrorCode(), this, 0, new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                Log.d(TAG, "onCancelDialog()");
            }
        }).show();
    }
}
