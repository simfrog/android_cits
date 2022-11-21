/*
 * Copyright (c) 2011, Chad Rockey
 * Copyright (c) 2015, Tal Regev
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the Android Sensors Driver nor the names of its
 *       contributors may be used to endorse or promote products derived from
 *       this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package org.ros.android.android_cits.publisher;

import android.content.IntentSender;
import android.location.Location;
import android.os.Looper;
import androidx.annotation.NonNull;

import android.os.SystemClock;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

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

import org.ros.message.Time;
import org.ros.namespace.GraphName;
import org.ros.node.ConnectedNode;
import org.ros.node.Node;
import org.ros.node.NodeMain;
import org.ros.node.topic.Publisher;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

import sensor_msgs.NavSatFix;
import sensor_msgs.NavSatStatus;
import org.ros.android.android_cits.MainActivity;
import org.ros.android.android_cits.R;

public class NavSatFixPublisher implements NodeMain, OnMapReadyCallback {

    // "Constant used in the location settings dialog."
    // Not sure why this is needed... -pgeneva
    private static final int REQUEST_CHECK_SETTINGS = 0x1;

    // The desired interval for location updates. Inexact. Updates may be more or less frequent.
    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 10;
    // The fastest rate for active location updates. Exact. Updates will never be more frequent than this value.
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    // Fused location provider
    private FusedLocationProviderClient mFusedLocationClient;
    private SettingsClient mSettingsClient;
    private LocationRequest mLocationRequest;
    private LocationSettingsRequest mLocationSettingsRequest;
    private LocationCallback mLocationCallback;

    // My current location
    private GoogleMap mMap;
    private Location mCurrentLocation;
    private LatLng mCurrentLatLng;

    // View objects and the main activity
    private TextView tvLocation;
    private MapFragment mapFragment;
    private String mLastUpdateTime;
    private String robotName;
    private String TAG = "NavSatFixPublisher";
    private MainActivity mainAct;

    // Our ROS publish node
    private Publisher<NavSatFix> publisher;
    private NavSatFix fix;


    public NavSatFixPublisher(MainActivity mainAct, String robotName) {
        // Get our textzone
        this.mainAct = mainAct;
        this.robotName = robotName;
        tvLocation = (TextView) mainAct.findViewById(R.id.titleTextGPS);
        mapFragment = (MapFragment)mainAct.getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        // Set our clients
        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(mainAct);
        mSettingsClient = LocationServices.getSettingsClient(mainAct);
        // Create a location callback
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                mCurrentLocation = locationResult.getLastLocation();
                mCurrentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
                mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
                publishMessages(publisher, locationResult.getLocations());
                updateUI();
            }
        };
        // Build the location request
        mLocationRequest = new LocationRequest()
                .setInterval(UPDATE_INTERVAL_IN_MILLISECONDS)
                .setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS)
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        // Build the location settings request object
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
        builder.addLocationRequest(mLocationRequest);
        mLocationSettingsRequest = builder.build();
    }

    private void updateUI() {
        // Return if we do not have a position
        if (mCurrentLocation == null)
            return;
        // Else we are good to display
        String lat = String.valueOf(mCurrentLocation.getLatitude());
        String lng = String.valueOf(mCurrentLocation.getLongitude());
        tvLocation.setText("At Time: " + mLastUpdateTime + "\n" +
                "Latitude: " + lat + "\n" +
                "Longitude: " + lng + "\n" +
                "Accuracy: " + mCurrentLocation.getAccuracy() + "\n" +
                "Provider: " + mCurrentLocation.getProvider());

        LatLng currentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        MarkerOptions markerOptions = new MarkerOptions();
        markerOptions.position(currentLatLng);
        markerOptions.title("Phone GPS");
        markerOptions.draggable(true);
        mMap.clear();
        mMap.addMarker(markerOptions);

        mMap.moveCamera(CameraUpdateFactory.newLatLng(mCurrentLatLng));

        Log.i(TAG, tvLocation.getText().toString().replace("\n", " | "));
    }

    private void publishMessages(Publisher publisher, List<Location> locs) {
        // Check that we have a location
        if (locs == null || locs.size() < 1)
            return;
        // We are good, lets publish
        for (Location location : locs) {
            this.fix = this.publisher.newMessage();
            long time_delta_millis = System.currentTimeMillis() - SystemClock.uptimeMillis();
            this.fix.getHeader().setStamp(Time.fromMillis(time_delta_millis + mCurrentLocation.getElapsedRealtimeNanos() / 1000000));
            this.fix.getHeader().setFrameId("/fix");
            this.fix.getStatus().setStatus(NavSatStatus.STATUS_FIX);
            this.fix.getStatus().setService(NavSatStatus.SERVICE_GPS);
            this.fix.setLatitude(mCurrentLocation.getLatitude());
            this.fix.setLongitude(mCurrentLocation.getLongitude());
            this.fix.setAltitude(mCurrentLocation.getAltitude());
            this.fix.setPositionCovarianceType(NavSatFix.COVARIANCE_TYPE_APPROXIMATED);
            double covariance = mCurrentLocation.getAccuracy()*mCurrentLocation.getAccuracy();
            double[] tmpCov = {covariance, 0, 0, 0, covariance, 0, 0, 0, covariance};
            this.fix.setPositionCovariance(tmpCov);
            this.publisher.publish(this.fix);
        }
        // Debug
        Log.i(TAG, "published = "+locs.size()+" "+this.fix.getLatitude());
    }


    //===========================================================================================
    //===========================================================================================
    //===========================================================================================
    //===========================================================================================

    @Override
    public GraphName getDefaultNodeName() {
        return GraphName.of("sensors_driver/navsatfix_publisher");
    }

    @Override
    public void onMapReady(final GoogleMap googleMap) {
        mMap = googleMap;
        mMap.isMyLocationEnabled();
        mMap.getUiSettings().setMyLocationButtonEnabled(true);
        mMap.animateCamera(CameraUpdateFactory.zoomTo(10));
    }

    @Override
    public void onStart(ConnectedNode connectedNode) {
        // Start location updates
        startLocationUpdates();
        // Create our publisher
        try {
            this.publisher = connectedNode.newPublisher( robotName + "/android" + "/fix", "sensor_msgs/NavSatFix");
//            mLocationCallback = new LocationCallback() {
//                @Override
//                public void onLocationResult(LocationResult locationResult) {
//                    super.onLocationResult(locationResult);
//                    mCurrentLocation = locationResult.getLastLocation();
//                    mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());

//                    LatLng currentLatLng = new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
//                    MarkerOptions markerOptions = new MarkerOptions();
//                    markerOptions.position(currentLatLng);
//                    markerOptions.draggable(true);

//                    publishMessages(publisher, locationResult.getLocations());
//                    updateUI();
//                }
//            };
        } catch (Exception e) {
            if (connectedNode != null) {
//                connectedNode.getLog().fatal(e);
            } else {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onShutdown(Node node) {
        stopLocationUpdates();
    }

    @Override
    public void onShutdownComplete(Node node) {}

    @Override
    public void onError(Node node, Throwable throwable) {}

    //===========================================================================================
    //===========================================================================================
    //===========================================================================================
    //===========================================================================================


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

}

//import android.Manifest;
//import android.content.pm.PackageManager;
//import android.location.Location;
//import android.location.LocationListener;
//import android.location.LocationManager;
//import android.location.LocationProvider;
//import android.location.LocationRequest;
//import android.os.Bundle;
//import android.os.Looper;
//import android.util.Log;
//
//import androidx.core.app.ActivityCompat;
//
//import com.google.android.gms.location.FusedLocationProviderClient;
//import com.google.android.gms.location.LocationCallback;
//import com.google.android.gms.location.LocationResult;
//import com.google.android.gms.maps.model.LatLng;
//
//import org.ros.message.Time;
//import org.ros.namespace.GraphName;
//import org.ros.node.ConnectedNode;
//import org.ros.node.Node;
//import org.ros.node.NodeMain;
//import org.ros.node.topic.Publisher;
//
//import java.util.List;
//
//import sensor_msgs.NavSatFix;
//import sensor_msgs.NavSatStatus;
//
///**
// * @author chadrockey@gmail.com (Chad Rockey)
// * @author axelfurlan@gmail.com (Axel Furlan)
// * @author tal.regev@gmail.com  (Tal Regev)
// */
//public class NavSatFixPublisher implements NodeMain {
//
//    protected String robotName;
//    protected NavSatThread navSatThread;
//    protected Location location;
////    protected NavSatListener navSatFixListener;
//    protected Publisher<NavSatFix> publisher;
//
//    public NavSatFixPublisher(Location location, String robotName) {
//        this.location = location;
//        this.robotName = robotName;
//    }
//
//    @Override
//    public void onStart(ConnectedNode node) {
//        try {
//            this.publisher = node.newPublisher(robotName + "/android/fix", "sensor_msgs/NavSatFix");
////            this.navSatFixListener = new NavSatListener(publisher);
//            this.navSatThread = new NavSatThread(location, publisher);
//            this.navSatThread.start();
//        } catch (Exception e) {
//            if (node != null) {
////                node.getLog().fatal(e);
//            } else {
//                e.printStackTrace();
//            }
//        }
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            // TODO: Consider calling
//            //    ActivityCompat#requestPermissions
//            // here to request the missing permissions, and then overriding
//            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//            //                                          int[] grantResults)
//            // to handle the case where the user grants the permission. See the documentation
//            // for ActivityCompat#requestPermissions for more details.
//            return;
//        }
//    }
//
//    LocationCallback locationCallback = new LocationCallback() {
//        @Override
//        public void onLocationResult(LocationResult locationResult) {
//            super.onLocationResult(locationResult);
//
//            List<Location> locationList = locationResult.getLocations();
//
//            if (locationList.size() > 0)
//            {
//                location = locationList.get(locationList.size()-1);
////                currentPosition = new LatLng(location.getLatitude(), location.getLongitude());
//
////                String markerSnippet = "Latitude: " + String.valueOf(location.getLatitude())
////                        + " Longitude: " + String.valueOf(location.getLongitude());
////                Log.d(TAG, "onLocationResult: " + markerSnippet);
//
////                setCurrentLocation(location, markerSnippet);
//            }
//        }
//    };
//
//    @Override
//    public void onShutdown(Node arg0) {
//        if (this.navSatThread == null) {
//            return;
//        }
//
//        this.navSatThread.shutdown();
//        try {
//            this.navSatThread.join();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        }
//    }
//
//    @Override
//    public void onShutdownComplete(Node arg0) {
//    }
//
//    @Override
//    public GraphName getDefaultNodeName() {
//        return GraphName.of("android_sensors_driver/imuPublisher");
//    }
//
//    @Override
//    public void onError(Node node, Throwable throwable) {
//    }
//
//    private class NavSatThread extends Thread {
//        Location location;
//
//        private Publisher<NavSatFix> publisher;
//        private Looper threadLooper;
//
//        private volatile byte currentStatus;
//
//        private NavSatThread(Location location, Publisher<NavSatFix> publisher) {
//            this.location = location;
//            this.publisher = publisher;
//            this.currentStatus = NavSatStatus.STATUS_FIX;
//        }
//
//        @Override
//        public void run() {
//            NavSatFix fix = this.publisher.newMessage();
//            fix.getHeader().setStamp(Time.fromMillis(System.currentTimeMillis()));
//            fix.getHeader().setFrameId("/gps");
//
//            fix.getStatus().setStatus(currentStatus);
//            fix.getStatus().setService(NavSatStatus.SERVICE_GPS);
//
//            fix.setLatitude(location.getLatitude());
//            fix.setLongitude(location.getLongitude());
//            fix.setAltitude(location.getAltitude());
//            fix.setPositionCovarianceType(NavSatFix.COVARIANCE_TYPE_APPROXIMATED);
//            double deviation = location.getAccuracy();
//            double covariance = deviation * deviation;
//            double[] tmpCov = {covariance, 0, 0, 0, covariance, 0, 0, 0, covariance};
//            fix.setPositionCovariance(tmpCov);
//            publisher.publish(fix);
//        }
//
//        public void shutdown() {
////            this.locationManager.removeUpdates(this.navSatListener);
//            if (threadLooper != null) {
//                threadLooper.quit();
//            }
//        }
//    }

//    private class NavSatListener {
//
//        private Publisher<NavSatFix> publisher;
//
//        private volatile byte currentStatus;
//
//        private NavSatListener(Publisher<NavSatFix> publisher) {
//            this.publisher = publisher;
//            this.currentStatus = NavSatStatus.STATUS_FIX; // Default to fix until we are told otherwise.
//        }
//
//        @Override
//        public void onLocationChanged(Location location) {
//            NavSatFix fix = this.publisher.newMessage();
//            fix.getHeader().setStamp(Time.fromMillis(System.currentTimeMillis()));
//            fix.getHeader().setFrameId("/gps");
//
//            fix.getStatus().setStatus(currentStatus);
//            fix.getStatus().setService(NavSatStatus.SERVICE_GPS);
//
//            fix.setLatitude(location.getLatitude());
//            fix.setLongitude(location.getLongitude());
//            fix.setAltitude(location.getAltitude());
//            fix.setPositionCovarianceType(NavSatFix.COVARIANCE_TYPE_APPROXIMATED);
//            double deviation = location.getAccuracy();
//            double covariance = deviation * deviation;
//            double[] tmpCov = {covariance, 0, 0, 0, covariance, 0, 0, 0, covariance};
//            fix.setPositionCovariance(tmpCov);
//            publisher.publish(fix);
//        }
//
//        @Override
//        public void onProviderDisabled(String provider) {
//        }
//
//        @Override
//        public void onProviderEnabled(String provider) {
//        }
//
//        @Override
//        public void onStatusChanged(String provider, int status, Bundle extras) {
//            switch (status) {
//                case LocationProvider.OUT_OF_SERVICE:
//                    currentStatus = NavSatStatus.STATUS_NO_FIX;
//                    break;
//                case LocationProvider.TEMPORARILY_UNAVAILABLE:
//                    currentStatus = NavSatStatus.STATUS_NO_FIX;
//                    break;
//                case LocationProvider.AVAILABLE:
//                    currentStatus = NavSatStatus.STATUS_FIX;
//                    break;
//            }
//        }
//    }
//}
