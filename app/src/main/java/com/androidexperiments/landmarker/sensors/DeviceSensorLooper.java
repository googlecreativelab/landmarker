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

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.google.vrtoolkit.cardboard.sensors.SensorEventProvider;

import java.util.ArrayList;

/**
 * @hide
 * This class registers two sensor listeners for accelerometer and gyroscope to the device
 * {@link SensorManager} and broadcasts all received SensorEvent to registered listeners.
 * <p>This class launches its own thread when {@link #start()} is called.
 */
public class DeviceSensorLooper implements SensorEventProvider {

    private static final String LOG_TAG = DeviceSensorLooper.class.getSimpleName();

    /** Is the inner looper thread started. */
    private boolean isRunning;

    /** Sensor manager used to register and unregister listeners. */
    private SensorManager sensorManager;

    /** Looper thread that listen to SensorEvent */
    private Looper sensorLooper;

    /** Sensor event listener for the internal sensors event. */
    private SensorEventListener sensorEventListener;

    /** List of registered listeners see {@link #registerListener()}. */
    private final ArrayList<SensorEventListener> registeredListeners =
            new ArrayList<SensorEventListener>();

    /**
     * Default constructor.
     * @param sensorManager Android sensor manager that will be used to register and unregister the
     * listeners.
     */
    public DeviceSensorLooper(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
    }

    private Sensor getUncalibratedGyro() {
        // Don't use uncalibrated gyro on HTC phones as it can make the phones unusable.
        // See b/21444644 for details.
        if (Build.MANUFACTURER.equals("HTC")) {
            return null;
        }
        return sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
    }

    /**
     * This registers two {@link SensorEventListener} and start a Looper.
     */
    @Override
    public void start() {
        if (isRunning) {
            // The looper is already started nothing needs to be done.
            return;
        }

        sensorEventListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                // Pass the event to all the listeners.
                synchronized (registeredListeners) {
                    for (SensorEventListener listener : registeredListeners) {
                        listener.onSensorChanged(event);
                    }
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                synchronized (registeredListeners) {
                    for (SensorEventListener listener : registeredListeners) {
                        listener.onAccuracyChanged(sensor, accuracy);
                    }
                }
            }
        };

        HandlerThread sensorThread = new HandlerThread("sensor") {
            @Override
            protected void onLooperPrepared() {
                Handler handler = new Handler(Looper.myLooper());

                // Initialize the accelerometer.
                Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                sensorManager.registerListener(sensorEventListener, accelerometer,
                        SensorManager.SENSOR_DELAY_GAME, handler);

                // Initialize the gyroscope.
                // If it's available, prefer to use the uncalibrated gyroscope sensor.
                // The regular gyroscope sensor is calibrated with a bias offset in the system. As we cannot
                // influence the behavior of this algorithm and it will affect the gyro while moving,
                // it is safer to initialize to the uncalibrated one and handle the gyro bias estimation
                // ourselves in a way which is optimized for our use case.
                Sensor gyroscope = getUncalibratedGyro();
                if (gyroscope == null) {
                    Log.i(LOG_TAG, "Uncalibrated gyroscope unavailable, default to regular gyroscope.");
                    gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
                }

                sensorManager.registerListener(sensorEventListener, gyroscope, SensorManager.SENSOR_DELAY_GAME, handler);

                //init the magnetometer so we can point north properly
                Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
                sensorManager.registerListener(sensorEventListener, magnetometer, SensorManager.SENSOR_DELAY_GAME, handler);
            }
        };

        sensorThread.start();
        sensorLooper = sensorThread.getLooper();  // Blocks till looper is ready.
        isRunning = true;
    }

    /**
     * Stops the looper and deregister the listener from the sensor manager.
     */
    @Override
    public void stop() {
        if (!isRunning) {
            // No need to stop.
            return;
        }

        sensorManager.unregisterListener(sensorEventListener);
        sensorEventListener = null;

        sensorLooper.quit();
        sensorLooper = null;
        isRunning = false;
    }

    @Override
    public void registerListener(SensorEventListener listener) {
        synchronized (registeredListeners) {
            registeredListeners.add(listener);
        }
    }

    @Override
    public void unregisterListener(SensorEventListener listener) {
        synchronized (registeredListeners) {
            registeredListeners.remove(listener);
        }
    }
}