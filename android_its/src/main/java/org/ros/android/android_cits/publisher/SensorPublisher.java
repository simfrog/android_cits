package org.ros.android.android_cits.publisher;

import android.content.IntentSender;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import org.ros.android.android_cits.MainActivity;
import org.ros.android.android_cits.R;
import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;
import org.ros.node.topic.Publisher;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import geometry_msgs.PoseStamped;
import sensor_msgs.NavSatFix;
import sensor_msgs.NavSatStatus;

/* Call List */
// 1. onCreate
// 2. onStart
// 3. onStart:Call mFusedLocationClient.requestLocationUpdates
// 4. onMapReady
// 5. startLocationUpdates

public class SensorPublisher implements NodeMain, OnMapReadyCallback {

    private String robotName;

    /* NavSatFixPublisher */
    private static final int REQUEST_CHECK_SETTINGS = 0x1;
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;

    // Fused Location Provider
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private LocationCallback mLocationCallback;

    // My Cureent Location
    private GoogleMap mMap;
    private Location mFilterLocation;
    private Location mEstimatedLocation;
    private Location mPostLocation;
    private Location mCurrentLocation;
    private List<Location> mCurrentListLocation;
    private LatLng mCurrentLatLng;
    private double Longitude;
    private double Latitude;
    private Boolean isUpdate = false;

    // View Objects & MainActivity
    private MainActivity mainAct;
    private TextView tvPubLocation;
    private MapFragment mapFragment;
    private String mLastUpdateTime;
    private String TAG = "NavSatFixPublisher";

    // NavSat ROS Pub Node
    private NavSatFix fix;
    private Publisher<NavSatFix> fix_publisher;

    /* OrientationPublisher */
    private int sensorDelay;
    private SensorManager sensorManager;
    private OrientationThread orientationThread;
    private SensorListener sensorListener;

    // PoseStamped ROS Pub Node
    private Publisher<geometry_msgs.PoseStamped> ori_publisher;

    public SensorPublisher(MainActivity mainAct, SensorManager manager, int sensorDelay, String robotName) {
        // Init
        this.mainAct = mainAct;
        this.sensorManager = manager;
        this.sensorDelay = sensorDelay;
        this.robotName = robotName;

        // Init Map View
        tvPubLocation = (TextView) mainAct.findViewById(R.id.PubGPS);
        mapFragment = (MapFragment)mainAct.getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        // Set our clients
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mainAct);
        mSettingsClient = LocationServices.getSettingsClient(mainAct);

        mPostLocation = new Location("Post Location");
        mFilterLocation = new Location("Speed2Pose");
        mEstimatedLocation = new Location("Estimated Result");


        // Create a location callback
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                mCurrentLocation = locationResult.getLastLocation();
                mCurrentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
                mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                mCurrentListLocation = locationResult.getLocations();
                Latitude = mCurrentLocation.getLatitude();
                Longitude = mCurrentLocation.getLongitude();
                isUpdate = true;
//                mFilterLocation = mCurrentLocation;
//                publishMessages(locationResult.getLocations());
                updateUI();
            }
        };

        isUpdate = false;

        // Build the location request
        mLocationRequest = LocationRequest.create()
                .setInterval(1000)
                .setFastestInterval(1000)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        // Build the location settings request object
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    private void publishMessages(List<Location> locs) {
//        updateUI();
        // Check that we have a location
        if (locs == null || locs.size() < 1)
            return;

        // We are good, lets publish
        for (Location location : locs) {
            this.fix = this.fix_publisher.newMessage();
            long time_delta_millis = System.currentTimeMillis() - SystemClock.uptimeMillis();
            this.fix.getHeader().setStamp(Time.fromMillis(time_delta_millis + mCurrentLocation.getElapsedRealtimeNanos() / 1000000));
            this.fix.getHeader().setFrameId("/fix");
            this.fix.getStatus().setStatus(NavSatStatus.STATUS_FIX);
            this.fix.getStatus().setService(NavSatStatus.SERVICE_GPS);
            this.fix.setLatitude(Latitude);
            this.fix.setLongitude(Longitude);
//            this.fix.setLatitude(mCurrentLocation.getLongitude());
//            this.fix.setLongitude(mCurrentLocation.getLongitude());
            this.fix.setAltitude(mCurrentLocation.getAltitude());
            this.fix.setPositionCovarianceType(NavSatFix.COVARIANCE_TYPE_APPROXIMATED);
            double covariance = mCurrentLocation.getAccuracy()*mCurrentLocation.getAccuracy();
            double[] tmpCov = {covariance, 0, 0, 0, covariance, 0, 0, 0, covariance};
            this.fix.setPositionCovariance(tmpCov);
            this.fix_publisher.publish(this.fix);
        }
        // Debug
        Log.i(TAG, "published = "+locs.size()+" "+this.fix.getLatitude()+" "+mCurrentLocation.getLongitude());
    }

    private void updateUI() {
        // Return if we do not have a position
        if (mCurrentLocation == null)
            return;

        // Else we are good to display
        String lat = String.valueOf(Latitude);
        String lng = String.valueOf(Longitude);
        tvPubLocation.setText("At Time: " + mLastUpdateTime + "\n" +
                "Latitude: " + lat + "\n" +
                "Longitude: " + lng + "\n" +
                "Accuracy: " + mCurrentLocation.getAccuracy() + "\n" +
                "Provider: " + mCurrentLocation.getProvider());

        LatLng currentLatLng = new LatLng(Latitude, Longitude);
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(currentLatLng);
        markerOptions.title("Phone GPS");
        markerOptions.draggable(true);
        mMap.clear();
        mMap.addMarker(markerOptions);

        mMap.moveCamera(CameraUpdateFactory.newLatLng(currentLatLng));

        Log.i(TAG, tvPubLocation.getText().toString().replace("\n", " | "));
    }

    private void startLocationUpdates() {
        // Begin by checking if the device has the necessary location settings.
        mSettingsClient.checkLocationSettings(mLocationSettingsRequest)
                .addOnSuccessListener(mainAct, new OnSuccessListener<LocationSettingsResponse>() {
                    @Override
                    public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                        Log.i(TAG, "All location settings are satisfied.");
                        //noinspection MissingPermission
                        mFusedLocationClient.requestLocationUpdates(mLocationRequest, mLocationCallback, Looper.myLooper());
                        updateUI();
                    }
                })
                .addOnFailureListener(mainAct, new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        int statusCode = ((ApiException) e).getStatusCode();
                        switch (statusCode) {
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                Log.i(TAG, "Location settings are not satisfied. Attempting to upgrade location settings ");
                                try {
                                    // Show the dialog by calling startResolutionForResult(), and check the
                                    // result in onActivityResult().
                                    ResolvableApiException rae = (ResolvableApiException) e;
                                    rae.startResolutionForResult(mainAct, REQUEST_CHECK_SETTINGS);
                                } catch (IntentSender.SendIntentException sie) {
                                    Log.i(TAG, "PendingIntent unable to execute request.");
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                String errorMessage = "Location settings are inadequate, and cannot be fixed here. Fix in Settings.";
                                Log.e(TAG, errorMessage);
                                Toast.makeText(mainAct, errorMessage, Toast.LENGTH_LONG).show();
                        }

                        updateUI();
                    }
                });
    }


    private void stopLocationUpdates() {
        mFusedLocationClient.removeLocationUpdates(mLocationCallback);
    }

    // ====================================================================================================================================
    // ====================================================================================================================================
    // ====================================================================================================================================
    // ====================================================================================================================================
    // ====================================================================================================================================

    public GraphName getDefaultNodeName() {
        return GraphName.of("android_sensors_driver/sensor_publihser");
    }

    public void onStart(ConnectedNode node) {
        // Start location updates
        startLocationUpdates();

        // Create Our Publisher
        try {
            this.fix_publisher = node.newPublisher( "/phone" + robotName + "/android" + "/fix", "sensor_msgs/NavSatFix");
            this.ori_publisher = node.newPublisher("phone" + robotName + "/android" + "/orientation", "geometry_msgs/PoseStamped");

            // 	Determine if we have the various needed sensors
            boolean hasAccel = false;
            boolean hasMagnetic = false;
            boolean hasLinearAccel = false;

            List<Sensor> laccelList = this.sensorManager.getSensorList(Sensor.TYPE_LINEAR_ACCELERATION);

            if (laccelList.size() > 0) {
                hasLinearAccel = true;
            }

            List<Sensor> accelList = this.sensorManager.getSensorList(Sensor.TYPE_ACCELEROMETER);

            if (accelList.size() > 0) {
                hasAccel = true;
            }

            List<Sensor> mfList = this.sensorManager.getSensorList(Sensor.TYPE_MAGNETIC_FIELD);

            if (mfList.size() > 0) {
                hasMagnetic = true;

                this.sensorListener = new SensorListener(ori_publisher, hasAccel, hasMagnetic, hasLinearAccel);
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

    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;
        mMap.isMyLocationEnabled();
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.animateCamera(CameraUpdateFactory.zoomTo(10));
    }

    public void onShutdown(Node arg0) {
        // Stop Location Updates
        stopLocationUpdates();

        // OrientationPublisher Shutdown
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

    public void onError(Node node, Throwable throwable) {
    }

    // ====================================================================================================================================
    // ====================================================================================================================================
    // ====================================================================================================================================
    // ====================================================================================================================================
    // ====================================================================================================================================


    private class OrientationThread extends Thread {
        private final SensorManager sensorManager;
        private final Sensor accelSensor;
        private final Sensor gyroSensor;
        private final Sensor quatSensor;
        private final Sensor mfSensor;
        private final Sensor linAccelSensor;
        private SensorPublisher.SensorListener sensorListener;
        private Looper threadLooper;

        private OrientationThread(SensorManager sensorManager, SensorPublisher.SensorListener sensorListener) {
            this.sensorManager = sensorManager;
            this.sensorListener = sensorListener;
            this.accelSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            this.gyroSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            this.quatSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            this.mfSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            this.linAccelSensor = this.sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        }

        public void run() {
            Looper.prepare();
            this.threadLooper = Looper.myLooper();
            this.sensorManager.registerListener(this.sensorListener, this.accelSensor, sensorDelay);
            this.sensorManager.registerListener(this.sensorListener, this.gyroSensor, sensorDelay);
            this.sensorManager.registerListener(this.sensorListener, this.quatSensor, sensorDelay);
            this.sensorManager.registerListener(this.sensorListener, this.mfSensor, sensorDelay);
            this.sensorManager.registerListener(this.sensorListener, this.linAccelSensor, sensorDelay);
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

        /* ori */
        private Publisher<geometry_msgs.PoseStamped> oriPublisher;

        private boolean hasAccel;
        private boolean hasMagnetic;
        private boolean hasLinearAccel;

        private long accelTime;
        private long magneticTime;

        private float[] mR = new float[9];
        private float[] mOrientation = new float[3];
        private float[] mLastAccelerometer = new float[3];
        private float[] mLastMagnetometer = new float[3];
        private float[] mLastLinAccelerometer = new float[3];

        private boolean mLastAccelerometerSet = false;
        private boolean mLastMagnetometerSet = false;
        private boolean mLastLinAccelerometerSet = false;

        private float mCurrentDegree = 0f;

        private KalmanFilter mKalmanAccX;
        private KalmanFilter mKalmanAccY;

        private double poseX;
        private double poseY;
        private double speedX;
        private double speedY;
        private double latitude = 0.0;
        private double longitude = 0.0;
        private double postTime = 0.0;

        private static final float NS2S = 1.0f/1000000000.0f;


        private SensorListener(Publisher<geometry_msgs.PoseStamped> oriPublisher, boolean hasAccel, boolean hasMagnetic, boolean hasLinearAccel) {
            this.oriPublisher = oriPublisher;
            this.hasAccel = hasAccel;
            this.hasMagnetic = hasMagnetic;
            this.hasLinearAccel = hasLinearAccel;
            this.accelTime = 0;

            this.mKalmanAccX = new KalmanFilter(0.0f);
            this.mKalmanAccY = new KalmanFilter(0.0f);
        }

        //	@Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
            Log.i("Sensor Accuracy", "Accuracy =  " +accuracy);
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

            } else if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                System.arraycopy(event.values, 0, mLastLinAccelerometer, 0, event.values.length);
                mLastLinAccelerometerSet = true;

//                if (isUpdate == false) {
//                    double Ax = event.values[0];
//                    double Ay = event.values[1];
//
////                    double filteredAX = mKalmanAccX.Update(Ax);
////                    double filteredAY = mKalmanAccY.Update(Ay);
//
//                    double currentTime = event.timestamp * NS2S;
//                    double dt = currentTime - postTime;
//
//                    postTime = currentTime;
//                    speedX = speedX + Ax * dt;
//                    speedY = speedY + Ay * dt;
//                    poseX = speedX * dt + 0.5 * Ax * dt * dt;
//                    poseY = speedY * dt + 0.5 * Ay * dt * dt;
//                    latitude += poseX;
//                    longitude += poseX;
//
//                    Latitude = latitude;
//                    Longitude = longitude;
//
//                    Log.i("Time", "accelTime = " + currentTime + " pre_accelTime = " + postTime);
//                    Log.i("Debug", "<Not Update> "+" Latitude = "+Latitude+" Longitude = "+Longitude);
////                    Log.i("Debug", "Ax = " + filteredAX + " Ay = " + filteredAY + " speedX = " + speedX + " speedY = " + speedY);
////                    Log.i("Debug", "dt = " + dt + " poseX = " + poseX + " poseY = " + poseY + " Latitude = " + Latitude + " Longitude = " + Longitude);
//
//                } else {
//                    speedX = 0;
//                    speedY = 0;
//                    poseX = 0;
//                    poseY = 0;
//
//                    Latitude = mCurrentLocation.getLatitude();
//                    Longitude = mCurrentLocation.getLongitude();
//
//                    latitude = Latitude;
//                    longitude = Longitude;
//
//                    isUpdate = false;
//
//                    Log.i("Debug", "<Update> "+" Latitude = "+Latitude+" Longitude = "+Longitude);
//                }
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

                publishMessages(mCurrentListLocation);

                // Convert event.timestamp (nanoseconds uptime) into system time, use that as the header stamp
                long time_delta_millis = System.currentTimeMillis() - SystemClock.uptimeMillis();
//                cits_msg[1] = (float) time_delta_millis;
                geometry_msgs.PoseStamped msg = this.oriPublisher.newMessage();
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

                oriPublisher.publish(msg);

                // Reset times
                this.accelTime = 0;
                this.magneticTime = 0;
            }
        }

        private class KalmanFilter {
            private double Q = 0.00001;
            private double R = 0.001;
            private double X = 0;
            private double P = 1;
            private double K;

            KalmanFilter(double initValue) {
                X = initValue;
            }

            private void MeasurementUpdate() {
                K = (P+Q) / (P+Q+R);
                P = R * (P+Q) / (R+P+Q);
            }

            public double Update(double measurement) {
                MeasurementUpdate();
                X = X + (measurement-X) * K;
                return X;
            }
        }
    }
}
