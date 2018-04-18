package edu.harvard.schepens.eyebit;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

/**
 * A class for head gesture detection using Gyroscope and Orientation.
 *
 * @author DongZhuoran (dongzhuorn@gmail.com)
 */

public class HeadGestureDetector {

    private static final String TAG = "HeadGestureDetector";

    // For head gesture detection with Orientation
    private static final int MATRIX_SIZE = 16;
    private float[] inR = new float[MATRIX_SIZE];
    private float[] outR = new float[MATRIX_SIZE];
    private float[] I = new float[MATRIX_SIZE];

    // For head gesture detection with gyro
    private static final long STATE_TIMEOUT_NSEC = 1000 * 1000 * 1000;

    private static final float STABLE_ANGULAR_VELOCITY = 0.10F;
    private static final float MIN_MOV_ANGULAR_VELOCITY = 1.00F;

    private float[] orientationValues = new float[3];
    private float[] magneticValues = new float[3];
    private float[] accelerometerValues = new float[3];
    private float[] orientationVelocity = new float[3];

    private SensorManager mSensorManager;
    private SensorEventListener mSensorEventListener;
    private OnHeadGestureListener mListener;

    private enum State {
        IDLE, SHAKE_TO_RIGHT, SHAKE_BACK_TO_LEFT, SHAKE_TO_LEFT, SHAKE_BACK_TO_RIGHT, GO_DOWN, BACK_UP,
        GO_UP, BACK_DOWN
    }

    private State mState = State.IDLE;
    private long mLastStateChanged = -1;

    private float mCurrAzimuthValue = 0;

    public HeadGestureDetector(Context context) {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mSensorEventListener = new HeadGestureSensorEventListener();
    }

    private static final int[] REQUIRED_SENSORS = { Sensor.TYPE_MAGNETIC_FIELD, Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE };

    private static final int[] SENSOR_RATES = { SensorManager.SENSOR_DELAY_FASTEST, SensorManager.SENSOR_DELAY_FASTEST,
            SensorManager.SENSOR_DELAY_NORMAL };

    public void start() {
        for (int i = 0; i < REQUIRED_SENSORS.length; i++) {
            Sensor sensor = mSensorManager.getDefaultSensor(REQUIRED_SENSORS[i]);
            if (sensor != null) {
                Log.d(TAG, "registered: " + sensor.getName());
                mSensorManager.registerListener(mSensorEventListener, sensor, SENSOR_RATES[i]);
            }
        }
    }

    public void stop() {
        mSensorManager.unregisterListener(mSensorEventListener);
    }

    public void setOnHeadGestureListener(OnHeadGestureListener listener) {
        this.mListener = listener;
    }

    private static int maxAbsIndex(float[] array) {
        float maxValue = Float.MIN_VALUE;
        int maxIndex = -1;
        for (int i = 0; i < array.length; i++) {
            float val = Math.abs(array[i]);
            if (val > maxValue) {
                maxValue = val;
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private class HeadGestureSensorEventListener implements SensorEventListener {

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        @Override
        public void onSensorChanged(SensorEvent event) {

            if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                // Log.w(TAG, "Unreliable event...");
            }

            int sensorType = event.sensor.getType();

            if (sensorType == Sensor.TYPE_MAGNETIC_FIELD) {
                magneticValues = event.values.clone();
                return;
            }

            if (sensorType == Sensor.TYPE_ACCELEROMETER) {
                accelerometerValues = event.values.clone();
                SensorManager.getRotationMatrix(inR, I, accelerometerValues, magneticValues);
                SensorManager.remapCoordinateSystem(inR, SensorManager.AXIS_Z, SensorManager.AXIS_MINUS_Y, outR);
                SensorManager.getOrientation(outR, orientationValues);

                if (isPutOn(orientationValues)) {
//                    Log.d(TAG, "orientation values: "
//                            + " azimuth: " + Math.toDegrees(orientationValues[0])
//                            + " pitch: " + Math.toDegrees(orientationValues[1])
//                            + " roll " + Math.toDegrees(orientationValues[2]));

                    if (mListener != null) {
                        mListener.onOrientationChanged(orientationValues);
                    }
                    mCurrAzimuthValue = orientationValues[0];
                }

                return;
            }

            if (sensorType == Sensor.TYPE_GYROSCOPE) {
                if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    // Log.w(TAG, "Unreliable gyroscope event...");
                    // return;
                }

                orientationVelocity = event.values.clone();

                // state timeout check
                if (event.timestamp - mLastStateChanged > STATE_TIMEOUT_NSEC
                        && mState != State.IDLE) {
                    Log.d(TAG, "state timeout");
                    mLastStateChanged = event.timestamp;
                    mState = State.IDLE;
                }

//                Log.d(TAG, "Velocity:" + Arrays.toString(orientationVelocity));

               // check if the goggle is put on
               if (!isPutOn(orientationValues)) {
                   Log.d(TAG, "Goggle is off");
                   return;
               }

                int maxVelocityIndex = maxAbsIndex(orientationVelocity);
                if (isStable(orientationValues, orientationVelocity)) {
//                    Log.d(TAG, "isStable");
                } else if (maxVelocityIndex == 0) {
                    if (orientationVelocity[0] < -MIN_MOV_ANGULAR_VELOCITY) {
                        if (mState == State.IDLE) {
                            Log.d(TAG, "shake to right");
                            mState = State.SHAKE_TO_RIGHT;
                            mLastStateChanged = event.timestamp;
                            if (mListener != null) {
                                mListener.onShakeToRight();
                            }
                        } else if (mState == State.SHAKE_TO_LEFT) {
                            Log.d(TAG, "shake back to right");
                            mState = State.SHAKE_BACK_TO_RIGHT;
                            mLastStateChanged = event.timestamp;
                            if (mListener != null) {
                                mListener.onShakeBackToRight();
                            }
                        }
                    } else if (orientationVelocity[0] > MIN_MOV_ANGULAR_VELOCITY) {
                        if (mState == State.IDLE) {
                            Log.d(TAG, "shake to left");
                            mState = State.SHAKE_TO_LEFT;
                            mLastStateChanged = event.timestamp;
                            if (mListener != null) {
                                mListener.onShakeToLeft();
                            }
                        } else if (mState == State.SHAKE_TO_RIGHT) {
                            Log.d(TAG, "shake back to left");
                            mState = State.SHAKE_BACK_TO_LEFT;
                            mLastStateChanged = event.timestamp;
                            if (mListener != null) {
                                mListener.onShakeBackToLeft();
                            }
                        }
                    }
                } else if (maxVelocityIndex == 1) {
                    if (orientationVelocity[1] < -MIN_MOV_ANGULAR_VELOCITY) {
                        if (mState == State.IDLE) {
                            Log.d(TAG, "isLookUp");
                            mState = State.GO_UP;
                            mLastStateChanged = event.timestamp;
                            if (mListener != null) {
                                mListener.onLookUp();
                            }
                        } else if (mState == State.GO_DOWN) {
                            Log.d(TAG, "look back up");
                            mState = State.BACK_UP;
                            mLastStateChanged = event.timestamp;
                            if (mListener != null) {
                                mListener.onBackLookUp(Math.toDegrees(mCurrAzimuthValue));
                            }
                        }
                    } else if (orientationVelocity[1] > MIN_MOV_ANGULAR_VELOCITY) {
                        if (mState == State.IDLE) {
                            Log.d(TAG, "isLookDown");
                            mState = State.GO_DOWN;
                            mLastStateChanged = event.timestamp;
                            if (mListener != null) {
                                mListener.onLookDown();
                            }
                        } else if (mState == State.GO_UP) {
                            Log.d(TAG, "look back down");
                            mState = State.BACK_DOWN;
                            mLastStateChanged = event.timestamp;
                            if (mListener != null) {
                                mListener.onBackLookDown();
                            }
                        }
                    }
                }
            }
        }
    }

    private static boolean isStable(float[] orientationValues, float[] orientationVelocity) {
        if (Math.abs(orientationVelocity[0]) < STABLE_ANGULAR_VELOCITY
                && Math.abs(orientationVelocity[1]) < STABLE_ANGULAR_VELOCITY
                && Math.abs(orientationVelocity[2]) < STABLE_ANGULAR_VELOCITY) {
            return true;
        }
        return false;
    }

    private static final float MAX_PUT_ON_PITCH_RADIAN = (float) Math.toRadians(10.0F);
    private static final float MIN_PUT_ON_PITCH_RADIAN = (float) Math.toRadians(-10.0F);
    private static final float MAX_PUT_ON_ROLL_RADIAN = (float) Math.toRadians(40.0F);
    private static final float MIN_PUT_ON_ROLL_RADIAN = (float) Math.toRadians(-40.0F);

    private static boolean isPutOn(float[] orientationValues) {
        if (orientationValues[1] > MIN_PUT_ON_PITCH_RADIAN
                && orientationValues[1] < MAX_PUT_ON_PITCH_RADIAN
                && orientationValues[2] > MIN_PUT_ON_ROLL_RADIAN
                && orientationValues[2] < MAX_PUT_ON_ROLL_RADIAN) {
            return true;
        }
        return false;
    }

    public interface OnHeadGestureListener {

        public void onOrientationChanged(float[] orientationValues);

        public void onLookUp();

        public void onLookDown();

        public void onBackLookUp(double initAzimuth);

        public void onBackLookDown();

        public void onShakeToLeft();

        public void onShakeToRight();

        public void onShakeBackToLeft();

        public void onShakeBackToRight();
    }

}
