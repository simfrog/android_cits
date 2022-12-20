package org.ros.android.android_cits.publisher;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorListener;
import android.hardware.SensorManager;
import android.os.Looper;
import android.os.SystemClock;

import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;
import org.ros.node.topic.Publisher;

import java.util.List;

public class OrientationPublisher implements NodeMain {

    private String robotName;
    private int sensorDelay;
    private SensorManager sensorManager;

    private OrientationThread orientationThread;
    private SensorListener sensorListener;
    private Publisher<geometry_msgs.PoseStamped> publisher;

    public OrientationPublisher(SensorManager manager, int sensorDelay, String robotName) {
        this.sensorManager = manager;
        this.sensorDelay = sensorDelay;
        this.robotName = robotName;
    }

    public GraphName getDefaultNodeName() {
        return GraphName.of("android_sensors_driver/orientation_publihser");
    }

    public void onError(Node node, Throwable throwable) {
    }

    public void onStart(ConnectedNode node) {
        try {
            this.publisher = node.newPublisher("phone" + robotName + "/android" + "/orientation", "geometry_msgs/PoseStamped");
            // 	Determine if we have the various needed sensors
            boolean hasAccel = false;
            boolean hasMagnetic = false;

            List<Sensor> accelList = this.sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);

            if (accelList.size() > 0) {
                hasAccel = true;
            }

            List<Sensor> mfList = this.sensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);

            if (mfList.size() > 0) {
                hasMagnetic = true;

                this.sensorListener = new SensorListener(publisher, hasAccel, hasMagnetic);
                this.orientationThread = new OrientationThread(this.sensorManager, this.sensorListener);
                this.orientationThread.start();
            }

        } catch (Exception e) {
            if (node != null) {
//                node.getLog().fatal(e);
            } else {
                e.printStackTrace();
            }
        }
    }

    public void onShutdown(Node arg0) {
        if (this.orientationThread == null) {
            return;
        }
        this.orientationThread.shutdown();

        try {
            this.orientationThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void onShutdownComplete(Node arg0) {
    }

    private class OrientationThread extends Thread {
        private final SensorManager sensorManager;
        private final Sensor accelSensor;
        private final Sensor gyroSensor;
        private final Sensor quatSensor;
        private final Sensor mfSensor;
        private OrientationPublisher.SensorListener sensorListener;
        private Looper threadLooper;

        private OrientationThread(SensorManager sensorManager, OrientationPublisher.SensorListener sensorListener) {
            this.sensorManager = sensorManager;
            this.sensorListener = sensorListener;
            this.accelSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            this.gyroSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            this.quatSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            this.mfSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }


        public void run() {
            Looper.prepare();
            this.threadLooper = Looper.myLooper();
            this.sensorManager.registerListener(this.sensorListener, this.accelSensor, sensorDelay);
            this.sensorManager.registerListener(this.sensorListener, this.gyroSensor, sensorDelay);
            this.sensorManager.registerListener(this.sensorListener, this.quatSensor, sensorDelay);
            this.sensorManager.registerListener(this.sensorListener, this.mfSensor, sensorDelay);
            Looper.loop();
        }


        public void shutdown() {
            this.sensorManager.unregisterListener(this.sensorListener);
            if (this.threadLooper != null) {
                this.threadLooper.quit();
            }
        }
    }

    private class SensorListener implements SensorEventListener {

        private Publisher<geometry_msgs.PoseStamped> publisher;

        private boolean hasAccel;
        private boolean hasMagnetic;

        private long accelTime;
        private long magneticTime;

        private float[] mLastAccelerometer = new float[3];
        private float[] mLastMagnetometer = new float[3];
        private boolean mLastAccelerometerSet = false;
        private boolean mLastMagnetometerSet = false;

        private float[] mR = new float[9];
        private float[] mOrientation = new float[3];
        private float mCurrentDegree = 0f;

        private SensorListener(Publisher<geometry_msgs.PoseStamped> publisher, boolean hasAccel, boolean hasMagnetic) {
            this.publisher = publisher;
            this.hasAccel = hasAccel;
            this.hasMagnetic = hasMagnetic;
            this.accelTime = 0;
        }

        //	@Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        //	@Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                System.arraycopy(event.values, 0, mLastAccelerometer, 0, event.values.length);
                mLastAccelerometerSet = true;
                this.accelTime = event.timestamp;
            } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                System.arraycopy(event.values, 0, mLastMagnetometer, 0, event.values.length);
                mLastMagnetometerSet = true;
                this.magneticTime = event.timestamp;;
            }

            if (mLastAccelerometerSet && mLastMagnetometerSet) {
                SensorManager.getRotationMatrix(mR, null, mLastAccelerometer, mLastMagnetometer);
                float azimuthinDegrees = (int) (Math.toDegrees(SensorManager.getOrientation(mR, mOrientation)[0])+360)%360;
                mCurrentDegree = azimuthinDegrees;
            }

            // Currently storing event times in case I filter them in the future.  Otherwise they are used to determine if all sensors have reported.
            if ((this.accelTime != 0 || !this.hasAccel) &&
                    (this.magneticTime != 0 || !this.hasMagnetic)) {
//                float[] cits_msg = new float[2];
//                cits_msg[0] = mCurrentDegree;

                // Convert event.timestamp (nanoseconds uptime) into system time, use that as the header stamp
                long time_delta_millis = System.currentTimeMillis() - SystemClock.uptimeMillis();
//                cits_msg[1] = (float) time_delta_millis;
                geometry_msgs.PoseStamped msg = this.publisher.newMessage();
                msg.getHeader().setStamp(Time.fromMillis(time_delta_millis+ event.timestamp / 1000000));
                msg.getHeader().setFrameId("/android/orientation");

                msg.getPose().getPosition().setX(0.0);
                msg.getPose().getPosition().setY(0.0);
                msg.getPose().getPosition().setZ(0.0);
                msg.getPose().getOrientation().setW(0.0);
                msg.getPose().getOrientation().setX(0.0);
                msg.getPose().getOrientation().setY(0.0);
                msg.getPose().getOrientation().setZ((double)mCurrentDegree);
//                msg.setData(cits_msg);

                publisher.publish(msg);

                // Reset times
                this.accelTime = 0;
                this.magneticTime = 0;
            }
        }
    }

}
