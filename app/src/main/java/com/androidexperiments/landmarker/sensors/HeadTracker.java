/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.androidexperiments.landmarker.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.Matrix;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;

import com.google.vrtoolkit.cardboard.sensors.Clock;
import com.google.vrtoolkit.cardboard.sensors.SensorEventProvider;
import com.google.vrtoolkit.cardboard.sensors.SystemClock;
import com.google.vrtoolkit.cardboard.sensors.internal.GyroscopeBiasEstimator;
import com.google.vrtoolkit.cardboard.sensors.internal.Matrix3x3d;
import com.google.vrtoolkit.cardboard.sensors.internal.OrientationEKF;
import com.google.vrtoolkit.cardboard.sensors.internal.Vector3d;

import java.util.concurrent.TimeUnit;


/**
 * @hide
 * Provides head tracking information from the device IMU.
 */
public class HeadTracker implements SensorEventListener {
    // The neck model parameters may be exposed as a per-user preference in the
    // future, but that's only a marginal improvement, since getting accurate eye
    // offsets would require full positional tracking. For now, use hardcoded
    // defaults. The values only have an effect when the neck model is enabled.
    private static final float DEFAULT_NECK_HORIZONTAL_OFFSET = 0.080f;  // meters
    private static final float DEFAULT_NECK_VERTICAL_OFFSET = 0.075f;  // meters
    private static final float DEFAULT_NECK_MODEL_FACTOR = 1.0f;

    // Amount of time we should predict forward to fight sensor and display latency.
    // According to tests done around January 2015, this is composed of:
    // - 48ms (3 frames at 60Hz) to account for latency due to triple buffer rendering.
    // - 10ms for the average delay between the time a raw gyro sample is acquired by the IMU and
    //   the time it is accessible through {@link android.hardware.SensorManager}.
    private static final float PREDICTION_TIME_IN_SECONDS = 0.058f;

    // Android display that is used to know the local orientation of the screen.
    private final Display display;

    // This matrix converts the coordinate system of the OrientationEKF tracker
    // to our coordinate system.
    private final float[] ekfToHeadTracker = new float[16];
    // This matrix rotates the sensor coordinate system to the current display
    // orientation (e.g. portrait to landscape).
    private final float[] sensorToDisplay = new float[16];
    // Current rotation value from the display.
    private float displayRotation = Float.NaN;
    // Translation matrix for the neck model.
    private final float[] neckModelTranslation = new float[16];
    // Temporary matrices used during headView computation.
    private final float[] tmpHeadView = new float[16];
    private final float[] tmpHeadView2 = new float[16];

    private float neckModelFactor = DEFAULT_NECK_MODEL_FACTOR;

    /** Guards {@link #setNeckModelFactor}. */
    private final Object neckModelFactorMutex = new Object();

    private volatile boolean tracking;

    // Kalman filter based orientation tracker.
    private final OrientationEKF tracker;

    /** Guards {@link #gyroBiasEstimator}. */
    private final Object gyroBiasEstimatorMutex = new Object();

    /** Used to estimate gyro bias. Disabled when set to {@code null}, which is the default. */
    private GyroscopeBiasEstimator gyroBiasEstimator;

    /**
     * Local sensor event provider. {@link android.hardware.SensorEvent} may be provided through
     * sensor device or recorded data.
     */
    private SensorEventProvider sensorEventProvider;

    // Globally synchronized clock.
    private Clock clock;

    // Clock timestamp of the latest gyro event update in nanoseconds.
    private long latestGyroEventClockTimeNs;

    /** Set to false after we've processed our first gyro value. */
    private volatile boolean firstGyroValue = true;

    /**
     * If TYPE_GYROSCOPE_UNCALIBRATED is available, this contains the initial gyroscope bias as
     * returned by the system.
     */
    private float[] initialSystemGyroBias = new float[3];

    /** The gyroscope bias. (0, 0, 0) if bias correction is disabled. */
    private final Vector3d gyroBias = new Vector3d();

    /** The last gyro values, after subtracting the bias in {@link #gyroBias}. */
    private final Vector3d latestGyro = new Vector3d();

    /** The last accelerometer values. */
    private final Vector3d latestAcc = new Vector3d();

    /**
     * Factory constructor that creates a {@link SensorEventProvider} from the
     * device SensorManager. It uses the system clock as global clock.
     *
     * @param context global context.
     * @return a usable HeadTracker that uses {@link DeviceSensorLooper} to provide sensor event.
     */
    public static HeadTracker createFromContext(Context context) {
        SensorManager sensorManager =
                (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Display display =
                ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
                        .getDefaultDisplay();

        return new HeadTracker(new DeviceSensorLooper(sensorManager), new SystemClock(), display);
    }

    /**
     * Default constructor.
     * @param sensorEventProvider provides SensorEvents to the head tracker.
     * @param clock globaly consistent clock that should be shared by all system that needs a
     *    synchronous time.
     * @param display device display to get access to the static rotation of the screen.
     */
    public HeadTracker(
            SensorEventProvider sensorEventProvider, Clock clock, Display display) {
        this.clock = clock;
        this.sensorEventProvider = sensorEventProvider;

        tracker = new OrientationEKF();
        this.display = display;

        // Enable gyroscope bias estimation by default.
        setGyroBiasEstimationEnabled(true);

        // Initialize the neck translation matrix.
        Matrix.setIdentityM(neckModelTranslation, 0);
    }

    /**
     * Pass the sensor data to the appropriate consumer.
     *
     * @param event Sensor data to process.
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            latestAcc.set(event.values[0], event.values[1], event.values[2]);
            tracker.processAcc(latestAcc, event.timestamp);

            synchronized (gyroBiasEstimatorMutex) {
                if (gyroBiasEstimator != null) {
                    gyroBiasEstimator.processAccelerometer(latestAcc, event.timestamp);
                }
            }
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE
                || event.sensor.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
            // Note that the event timestamp values probably don't match the system clock,
            // which is why we must sample it separately here.
            //
            // TODO(pfg): This timestamps might correspond to a time after the event happens. This
            // needs to be investigated further. We might want to substract the time it takes for
            // the sensor to integrate the measure (e.g 10 ms for an 100 Hz sensor).
            latestGyroEventClockTimeNs = clock.nanoTime();

            // If TYPE_GYROSCOPE_UNCALIBRATED is available, then we save the initial gyro bias estimation
            // returned by the system on the first frame. In subsequent frames, we always subtract
            // that initial bias. This way, we essentially A) initialize our own bias estimation with
            // the system values, and B) our own estimation is not conflicting with the system's one in
            // subsequent frames.
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE_UNCALIBRATED) {
                if (firstGyroValue && event.values.length == 6) {
                    // Store initial system bias estimation values.
                    initialSystemGyroBias[0] = event.values[3];
                    initialSystemGyroBias[1] = event.values[4];
                    initialSystemGyroBias[2] = event.values[5];
                }
                latestGyro.set(
                        event.values[0] - initialSystemGyroBias[0],
                        event.values[1] - initialSystemGyroBias[1],
                        event.values[2] - initialSystemGyroBias[2]);
            } else {
                // We only have access to TYPE_GYROSCOPE, simply copy the gyroscope data.
                latestGyro.set(event.values[0], event.values[1], event.values[2]);
            }

            firstGyroValue = false;

            synchronized (gyroBiasEstimatorMutex) {
                if (gyroBiasEstimator != null) {
                    gyroBiasEstimator.processGyroscope(latestGyro, event.timestamp);

                    // Subtract the gyro bias from the latest gyro reading.
                    gyroBiasEstimator.getGyroBias(gyroBias);
                    Vector3d.sub(this.latestGyro, gyroBias, latestGyro);
                }
            }
            tracker.processGyro(latestGyro, event.timestamp);
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            //add mag events to our tracker
            tracker.processMag(event.values, event.timestamp);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Do nothing.
    }

    /**
     * Starts reading sensor data for head tracking.
     */
    public void startTracking() {
        if (tracking) {
            return;
        }
        tracker.reset();

        synchronized (gyroBiasEstimatorMutex) {
            if (gyroBiasEstimator != null) {
                gyroBiasEstimator.reset();
            }
        }

        firstGyroValue = true;
        sensorEventProvider.registerListener(this);
        sensorEventProvider.start();
        tracking = true;
    }

    /**
     * Tell the head tracker to reset, clearing all orientation and alignment state.
     * It will realign on the next sensor readings.
     */
    public void resetTracker() {
        tracker.reset();
    }

    /**
     * Stops reading sensor data for head tracking.
     */
    public void stopTracking() {
        if (!tracking) {
            return;
        }

        sensorEventProvider.unregisterListener(this);
        sensorEventProvider.stop();
        tracking = false;
    }

    /**
     * @hide
     * Enables or disables use of the neck model for head tracking.
     * Refer to {@link CardboardView#setNeckModelEnabled} for more details.
     */
    public void setNeckModelEnabled(boolean enabled) {
        if (enabled) {
            setNeckModelFactor(1.0f);
        } else {
            setNeckModelFactor(0.0f);
        }
    }

    /**
     * @hide
     * Gets the neck model factor for head tracking. Refer to {@link CardboardView#getNeckModelFactor}
     * for more details.
     */
    public float getNeckModelFactor() {
        synchronized (neckModelFactorMutex) {
            return neckModelFactor;
        }
    }

    /**
     * @hide
     * Sets the neck model factor for head tracking. Refer to {@link CardboardView#setNeckModelFactor}
     * for more details.
     */
    public void setNeckModelFactor(float factor) {
        synchronized (neckModelFactorMutex) {
            if (factor < 0.0f || factor > 1.0f) {
                throw new IllegalArgumentException("factor should be within [0.0, 1.0]");
            }
            neckModelFactor = factor;
        }
    }

    /**
     * @hide
     * Enables or disables use of gyro bias estimation. This should preferably be called before
     * tracking is started to avoid jumps in head tracking.
     */
    public void setGyroBiasEstimationEnabled(boolean enabled) {
        synchronized (gyroBiasEstimatorMutex) {
            if (!enabled) {
                // Explicitly reset the estimator. This way a new one will be created from scratch
                // next time it's enabled.
                gyroBiasEstimator = null;
            } else if (gyroBiasEstimator == null) {
                gyroBiasEstimator = new GyroscopeBiasEstimator();
            }
        }
    }

    /**
     * @hide
     * Whether gyro bias estimation is enabled.
     */
    public boolean getGyroBiasEstimationEnabled() {
        synchronized (gyroBiasEstimatorMutex) {
            return gyroBiasEstimator != null;
        }
    }

    /**
     * Provides the most up-to-date transformation matrix.
     *
     * @param headView An array representing a 4x4 transformation matrix in column major order.
     * @param offset Offset in the array where data should be written.
     * @throws IllegalArgumentException If there is not enough space to write the result.
     */
    public void getLastHeadView(float[] headView, int offset) {
        // Ensure the result fits.
        if (offset + 16 > headView.length) {
            throw new IllegalArgumentException("Not enough space to write the result");
        }

        // Update rotation matrices for the current display orientation.
        float rotation = 0;
        switch (display.getRotation()) {
            case Surface.ROTATION_0:
                rotation = 0;
                break;
            case Surface.ROTATION_90:
                rotation = 90;
                break;
            case Surface.ROTATION_180:
                rotation = 180;
                break;
            case Surface.ROTATION_270:
                rotation = 270;
                break;
        }
        if (rotation != displayRotation) {
            displayRotation = rotation;
            Matrix.setRotateEulerM(sensorToDisplay, 0, 0, 0, -rotation);
            Matrix.setRotateEulerM(ekfToHeadTracker, 0, -90, 0, rotation);
        }

        // Read the latest orientation from the OrientationEKF tracker.
        synchronized (tracker) {
            if (!tracker.isReady()) {
                return;
            }
            double secondsSinceLastGyroEvent =
                    TimeUnit.NANOSECONDS.toSeconds(clock.nanoTime() - latestGyroEventClockTimeNs);
            double secondsToPredictForward = secondsSinceLastGyroEvent + PREDICTION_TIME_IN_SECONDS;
            double[] mat = tracker.getPredictedGLMatrix(secondsToPredictForward);
            for (int i = 0; i < headView.length; i++) {
                tmpHeadView[i] = (float) mat[i];
            }
        }

        // Convert from sensor coordinate frame to display orientation.
        Matrix.multiplyMM(tmpHeadView2, 0, sensorToDisplay, 0, tmpHeadView, 0);

        // Convert from OrientationEKF coordinate system to our coordinate system.
        Matrix.multiplyMM(headView, offset, tmpHeadView2, 0, ekfToHeadTracker, 0);

        // Use a simple neck model where the viewpoint rotates around the approximate base of
        // the neck, not the midpoint between the eyes. Pre-multiply the neck translation, and
        // then post-multiply the vertical offset. This way, effective player height remains
        // unchanged. Can't do this for horizontal offsets since that would require a reference
        // yaw angle.
        Matrix.setIdentityM(neckModelTranslation, 0);
        Matrix.translateM(neckModelTranslation, 0,
                0.0f,
                -neckModelFactor * DEFAULT_NECK_VERTICAL_OFFSET,
                neckModelFactor * DEFAULT_NECK_HORIZONTAL_OFFSET);
        Matrix.multiplyMM(tmpHeadView, 0, neckModelTranslation, 0, headView, offset);
        Matrix.translateM(headView, offset, tmpHeadView, 0,
                0.0f, neckModelFactor * DEFAULT_NECK_VERTICAL_OFFSET, 0.0f);
    }

    /**
     * Returns a current sensor to world transformation. This is a rotation matrix.
     * <p>
     * Visible for testing.
     */
    Matrix3x3d getCurrentPoseForTest() {
        return new Matrix3x3d(tracker.getRotationMatrix());
    }

    /**
     * Injects a custom estimator. Should only be used for testing.
     */
    void setGyroBiasEstimator(GyroscopeBiasEstimator estimator) {
        synchronized (gyroBiasEstimatorMutex) {
            gyroBiasEstimator = estimator;
        }
    }
}