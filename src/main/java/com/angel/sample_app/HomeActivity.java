/*
 * Copyright (c) 2015, Seraphim Sense Ltd.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 *    and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 *    conditions and the following disclaimer in the documentation and/or other materials provided
 *    with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 *    endorse or promote products derived from this software without specific prior written
 *    permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
 * USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.angel.sample_app;

import android.app.Activity;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.TextView;

import com.angel.sdk.BleCharacteristic;
import com.angel.sdk.BleDevice;
import com.angel.sdk.ChAccelerationEnergyMagnitude;
import com.angel.sdk.ChAccelerationWaveform;
import com.angel.sdk.ChBatteryLevel;
import com.angel.sdk.ChHeartRateMeasurement;
import com.angel.sdk.ChOpticalWaveform;
import com.angel.sdk.ChStepCount;
import com.angel.sdk.ChTemperatureMeasurement;
import com.angel.sdk.SrvActivityMonitoring;
import com.angel.sdk.SrvBattery;
import com.angel.sdk.SrvHealthThermometer;
import com.angel.sdk.SrvHeartRate;
import com.angel.sdk.SrvWaveformSignal;

import junit.framework.Assert;

public class HomeActivity extends Activity {

    String mBleDeviceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurements);
        orientation = getResources().getConfiguration().orientation;

        mHandler = new Handler(this.getMainLooper());

        mPeriodicReader = new Runnable() {
            @Override
            public void run() {
                mBleDevice.readRemoteRssi();
                if (mChAccelerationEnergyMagnitude != null) {
                    mChAccelerationEnergyMagnitude.readValue(mAccelerationEnergyMagnitudeListener);
                }

                mHandler.postDelayed(mPeriodicReader, RSSI_UPDATE_INTERVAL);
            }
        };

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mGreenOpticalWaveformView = (GraphView) findViewById(R.id.graph_green);
            mGreenOpticalWaveformView.setStrokeColor(0xffffffff);
            mBlueOpticalWaveformView = (GraphView) findViewById(R.id.graph_blue);
            mBlueOpticalWaveformView.setStrokeColor(0xffffffff);
            mAccelerationWaveformView = (GraphView) findViewById(R.id.graph_acceleration);
            mAccelerationWaveformView.setStrokeColor(0xfff7a300);
        }
    }

    protected void onStart() {
        super.onStart();

        Bundle extras = getIntent().getExtras();
        assert(extras != null);
        mBleDeviceAddress = extras.getString("ble_device_address");
        mBleDeviceName = extras.getString("ble_device_name");

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            connectGraphs(mBleDeviceAddress);
        } else {
            connect(mBleDeviceAddress);
        }

    }

    @Override
    protected void onStop() {
        super.onStop();
        if (orientation == Configuration.ORIENTATION_PORTRAIT) {
            displaySignalStrength(0);
        }
        unscheduleUpdaters();
        mBleDevice.disconnect();
    }

    private void connectGraphs(String deviceAddress) {

        if (mBleDevice != null) {
            mBleDevice.disconnect();
        }
        mBleDevice = new BleDevice(this, mDeviceGraphLifecycleCallback, mHandler);

        try {
            mBleDevice.registerServiceClass(SrvWaveformSignal.class);

        } catch (NoSuchMethodException e) {
            throw new AssertionError();
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        } catch (InstantiationException e) {
            throw new AssertionError();
        }

        mBleDevice.connect(deviceAddress);

    }


    private void connect(String deviceAddress) {
        // A device has been chosen from the list. Create an instance of BleDevice,
        // populate it with interesting services and then connect

        if (mBleDevice != null) {
            mBleDevice.disconnect();
        }
        mBleDevice = new BleDevice(this, mDeviceLifecycleCallback, mHandler);

        try {
            mBleDevice.registerServiceClass(SrvHeartRate.class);
            mBleDevice.registerServiceClass(SrvHealthThermometer.class);
            mBleDevice.registerServiceClass(SrvBattery.class);
            mBleDevice.registerServiceClass(SrvActivityMonitoring.class);

        } catch (NoSuchMethodException e) {
            throw new AssertionError();
        } catch (IllegalAccessException e) {
            throw new AssertionError();
        } catch (InstantiationException e) {
            throw new AssertionError();
        }

        mBleDevice.connect(deviceAddress);

        scheduleUpdaters();
        displayOnDisconnect();
    }

    private final BleDevice.LifecycleCallback mDeviceGraphLifecycleCallback = new BleDevice.LifecycleCallback() {
        @Override
        public void onBluetoothServicesDiscovered(BleDevice bleDevice) {
            bleDevice.getService(SrvWaveformSignal.class).getAccelerationWaveform().enableNotifications(mAccelerationWaveformListener);
            bleDevice.getService(SrvWaveformSignal.class).getOpticalWaveform().enableNotifications(mOpticalWaveformListener);
        }

        @Override
        public void onBluetoothDeviceDisconnected() {
            unscheduleUpdaters();
            connectGraphs(mBleDeviceAddress);
        }

        @Override
        public void onReadRemoteRssi(int i) {

        }
    };


    /**
     * Upon Heart Rate Service discovery starts listening to incoming heart rate
     * notifications. {@code onBluetoothServicesDiscovered} is triggered after
     * {@link BleDevice#connect(String)} is called.
     */
    private final BleDevice.LifecycleCallback mDeviceLifecycleCallback = new BleDevice.LifecycleCallback() {
        @Override
        public void onBluetoothServicesDiscovered(BleDevice device) {
            if (mBleDeviceName.equals("Angel Sensor 03957")){
                device.getService(SrvHeartRate.class).getHeartRateMeasurement().enableNotifications(mHeartRateListener);
                device.getService(SrvHealthThermometer.class).getTemperatureMeasurement().enableNotifications(mTemperatureListener);
                device.getService(SrvBattery.class).getBatteryLevel().enableNotifications(mBatteryLevelListener);
                device.getService(SrvActivityMonitoring.class).getStepCount().enableNotifications(mStepCountListener);
                mChAccelerationEnergyMagnitude = device.getService(SrvActivityMonitoring.class).getChAccelerationEnergyMagnitude();
                Assert.assertNotNull(mChAccelerationEnergyMagnitude);
            }
            else if (mBleDeviceName.equals("CC2650 SensorTag")){
                //Get service for sensortag device
            }
        }


        @Override
        public void onBluetoothDeviceDisconnected() {
            displayOnDisconnect();
            unscheduleUpdaters();

            // Re-connect immediately
            connect(mBleDeviceAddress);
        }

        @Override
        public void onReadRemoteRssi(final int rssi) {
            displaySignalStrength(rssi);
        }
    };

    private final BleCharacteristic.ValueReadyCallback<ChAccelerationWaveform.AccelerationWaveformValue> mAccelerationWaveformListener = new BleCharacteristic.ValueReadyCallback<ChAccelerationWaveform.AccelerationWaveformValue>() {
        @Override
        public void onValueReady(ChAccelerationWaveform.AccelerationWaveformValue accelerationWaveformValue) {
            if (accelerationWaveformValue != null && accelerationWaveformValue.wave != null && mAccelerationWaveformView != null)
                for (Integer item : accelerationWaveformValue.wave) {
                    mAccelerationWaveformView.addValue(item);
                }

        }
    };

    private final BleCharacteristic.ValueReadyCallback<ChOpticalWaveform.OpticalWaveformValue> mOpticalWaveformListener = new BleCharacteristic.ValueReadyCallback<ChOpticalWaveform.OpticalWaveformValue>() {
        @Override
        public void onValueReady(ChOpticalWaveform.OpticalWaveformValue opticalWaveformValue) {
            if (opticalWaveformValue != null && opticalWaveformValue.wave != null)
                for (ChOpticalWaveform.OpticalSample item : opticalWaveformValue.wave) {
                    mGreenOpticalWaveformView.addValue(item.green);
                    mBlueOpticalWaveformView.addValue(item.blue);
                }
        }
    };

    private final BleCharacteristic.ValueReadyCallback<ChHeartRateMeasurement.HeartRateMeasurementValue> mHeartRateListener = new BleCharacteristic.ValueReadyCallback<ChHeartRateMeasurement.HeartRateMeasurementValue>() {
        @Override
        public void onValueReady(final ChHeartRateMeasurement.HeartRateMeasurementValue hrMeasurement) {
            displayHeartRate(hrMeasurement.getHeartRateMeasurement());
        }
    };

    private final BleCharacteristic.ValueReadyCallback<ChBatteryLevel.BatteryLevelValue> mBatteryLevelListener =
            new BleCharacteristic.ValueReadyCallback<ChBatteryLevel.BatteryLevelValue>() {
                @Override
                public void onValueReady(final ChBatteryLevel.BatteryLevelValue batteryLevel) {
                    displayBatteryLevel(batteryLevel.value);
                }
            };

    private final BleCharacteristic.ValueReadyCallback<ChTemperatureMeasurement.TemperatureMeasurementValue> mTemperatureListener =
            new BleCharacteristic.ValueReadyCallback<ChTemperatureMeasurement.TemperatureMeasurementValue>() {
                @Override
                public void onValueReady(final ChTemperatureMeasurement.TemperatureMeasurementValue temperature) {
                    displayTemperature(temperature.getTemperatureMeasurement());
                }
            };

    private final BleCharacteristic.ValueReadyCallback<ChStepCount.StepCountValue> mStepCountListener =
            new BleCharacteristic.ValueReadyCallback<ChStepCount.StepCountValue>() {
                @Override
                public void onValueReady(final ChStepCount.StepCountValue stepCountValue) {
                    displayStepCount(stepCountValue.value);
                }
            };

    private final BleCharacteristic.ValueReadyCallback<ChAccelerationEnergyMagnitude.AccelerationEnergyMagnitudeValue> mAccelerationEnergyMagnitudeListener =
            new BleCharacteristic.ValueReadyCallback<ChAccelerationEnergyMagnitude.AccelerationEnergyMagnitudeValue>() {
                @Override
                public void onValueReady(final ChAccelerationEnergyMagnitude.AccelerationEnergyMagnitudeValue accelerationEnergyMagnitudeValue) {
                    displayAccelerationEnergyMagnitude(accelerationEnergyMagnitudeValue.value);
                }
            };

    private void displayHeartRate(final int bpm) {
        if (mBleDeviceName.equals("Angel Sensor 03957")) {
            TextView textViewPhrase = (TextView) findViewById(R.id.textview_heartRatePhrase);
            TextView textView = (TextView) findViewById(R.id.textview_heart_rate);
            textViewPhrase.setText("Heart Rate:");
            textView.setText(bpm + " bpm");

        }
        else{
            System.out.print("");
        }
    }

    private void displaySignalStrength(int db) {
        if (mBleDeviceName.equals("Angel Sensor 03957")) {
            int iconId;
            if (db > -70) {
                iconId = R.drawable.ic_signal_4;
            } else if (db > -80) {
                iconId = R.drawable.ic_signal_3;
            } else if (db > -85) {
                iconId = R.drawable.ic_signal_2;
            } else if (db > -87) {
                iconId = R.drawable.ic_signal_1;
            } else {
                iconId = R.drawable.ic_signal_0;
            }
            ImageView imageView = (ImageView) findViewById(R.id.imageview_signal);
            imageView.setImageResource(iconId);
            TextView textView = (TextView) findViewById(R.id.textview_signal);
            textView.setText(db + "db");
        }
    }

    private void displayBatteryLevel(int percents) {
        if (mBleDeviceName.equals("Angel Sensor 03957")) {
            int iconId;
            if (percents < 20) {
                iconId = R.drawable.ic_battery_0;
            } else if (percents < 40) {
                iconId = R.drawable.ic_battery_1;
            } else if (percents < 60) {
                iconId = R.drawable.ic_battery_2;
            } else if (percents < 80) {
                iconId = R.drawable.ic_battery_3;
            } else {
                iconId = R.drawable.ic_battery_4;
            }

            ImageView imageView = (ImageView) findViewById(R.id.imageview_battery);
            imageView.setImageResource(iconId);
            TextView textView = (TextView) findViewById(R.id.textview_battery);
            textView.setText(percents + "%");
        }
    }

    private void displayTemperature(final float degreesCelsius) {
        if (mBleDeviceName.equals("Angel Sensor 03957")) {
            TextView textViewPhrase = (TextView) findViewById(R.id.textview_tempPhrase);
            TextView textView = (TextView) findViewById(R.id.textview_temperature);
            textViewPhrase.setText("Temperature: ");
            textView.setText(degreesCelsius + "\u00b0C");
        }
    }

    private void displayStepCount(final int stepCount) {
        if (mBleDeviceName.equals("Angel Sensor 03957")) {
            TextView textViewPhrase = (TextView) findViewById(R.id.textview_stepsPhrase);
            TextView textView = (TextView) findViewById(R.id.textview_step_count);
            Assert.assertNotNull(textView);
            textViewPhrase.setText("Step Count: ");
            textView.setText(stepCount + " steps");
        }

    }

    private void displayAccelerationEnergyMagnitude(final int accelerationEnergyMagnitude) {
        if (mBleDeviceName.equals("Angel Sensor 03957")) {
            TextView textViewPhrase = (TextView) findViewById(R.id.textview_accelEnergyPhrase);
            TextView textView = (TextView) findViewById(R.id.textview_acceleration);
            Assert.assertNotNull(textView);
            textViewPhrase.setText("Accel Energy: ");
            textView.setText(accelerationEnergyMagnitude + "g");
        }

    }

    private void displayOnDisconnect() {
        if (mBleDeviceName.equals("Angel Sensor 03957")) {
            displaySignalStrength(-99);
            displayBatteryLevel(0);
        }
    }

    //Methods for CC2650 SensorTag:

    private void displayCCAmbientTemp() {
        if (mBleDeviceName.equals("CC2650 SensorTag")) {
            TextView cctextViewPhrase = (TextView) findViewById(R.id.textview_ccAmbientTempPhrase);
            //TextView textView = (TextView) findViewById(R.id.textview_temperature);
            cctextViewPhrase.setText("Ambient Temp: ");
            //textView.setText(degreesCelsius + "\u00b0C");
        }
    }

    private void scheduleUpdaters() {
        mHandler.post(mPeriodicReader);
    }

    private void unscheduleUpdaters() {
        mHandler.removeCallbacks(mPeriodicReader);
    }

    private static final int RSSI_UPDATE_INTERVAL = 1000; // Milliseconds

    private int orientation;

    private GraphView mAccelerationWaveformView, mBlueOpticalWaveformView, mGreenOpticalWaveformView;

    private BleDevice mBleDevice;
    private String mBleDeviceAddress;

    private Handler mHandler;
    private Runnable mPeriodicReader;
    private ChAccelerationEnergyMagnitude mChAccelerationEnergyMagnitude = null;
}
