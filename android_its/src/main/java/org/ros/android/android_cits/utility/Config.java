package org.ros.android.android_cits.utility;

import android.hardware.SensorManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import org.ros.address.InetAddressFactory;
import org.ros.android.android_cits.MainActivity;
import org.ros.android.android_cits.R;
import org.ros.android.android_cits.publisher.ImuPublisher;
import org.ros.android.android_cits.publisher.MagneticFieldPublisher;
import org.ros.android.android_cits.publisher.NavSatFixPublisher;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.net.URI;

public class Config {

    private MainActivity mainActivity;
    protected final int currentApiVersion = android.os.Build.VERSION.SDK_INT;
    protected URI masterURI;
    protected NodeMainExecutor nodeMainExecutor;

    protected EditText robot_name;
    protected CheckBox checkbox_fluid;
    protected CheckBox checkbox_illuminance;
    protected CheckBox checkbox_imu;
    protected CheckBox checkbox_magnetic;
    protected CheckBox checkbox_navsat;
    protected CheckBox checkbox_temp;
    protected Button button_config;

    protected String old_robot_name;
    protected boolean old_fluid;
    protected boolean old_illuminance;
    protected boolean old_imu;
    protected boolean old_magnetic;
    protected boolean old_navsat;
    protected boolean old_temp;

    protected ImuPublisher pub_imu;
    protected MagneticFieldPublisher pub_magnetic;
    protected NavSatFixPublisher pub_navsat2;

    //protected LocationManager mLocationManager;
    protected SensorManager mSensorManager;


    public Config(MainActivity mainActivity) {
        // Save our activity pointer
        this.mainActivity = mainActivity;
        this.nodeMainExecutor = null;

        // Load our references
        robot_name = (EditText) mainActivity.findViewById(R.id.robot_name);
        checkbox_imu = (CheckBox) mainActivity.findViewById(R.id.checkbox_imu);
        checkbox_magnetic = (CheckBox) mainActivity.findViewById(R.id.checkbox_magnetic);
        checkbox_navsat = (CheckBox) mainActivity.findViewById(R.id.checkbox_navsat);
        button_config = (Button) mainActivity.findViewById(R.id.config_sensor);

        // Load old variables, booleans default to false
        old_robot_name = robot_name.getText().toString();

        // Start the services we need
        //mLocationManager = (LocationManager) mainActivity.getSystemService(Context.LOCATION_SERVICE);
        mSensorManager = (SensorManager) mainActivity.getSystemService(MainActivity.SENSOR_SERVICE);
    }

    /**
     * This function creates all the event nodes that publish information
     * Each sensor gets its own node, and is responsible for publishing its events
     * If a new name is entered, we restart all them
     * Each node's state is stored so we do not restart already started nodes
     */
    public void update_publishers() {
        // Dis-enable button
        button_config.setEnabled(false);
        // Get the name of the robot
        String robot_name_text = robot_name.getText().toString();
        // 10,000 us == 100 Hz for Android 3.1 and above
        int sensorDelay = 100;
        // 16.7Hz for older devices.
        // They only support enum values, not the microsecond version.
        if (currentApiVersion <= android.os.Build.VERSION_CODES.HONEYCOMB) {
            sensorDelay = SensorManager.SENSOR_DELAY_NORMAL;
        }

        // If we have a new name, then we need to redo all publishing nodes
        if(!old_robot_name.equals(robot_name_text)) {
            nodeMainExecutor.shutdownNodeMain(pub_imu);
            nodeMainExecutor.shutdownNodeMain(pub_magnetic);
            nodeMainExecutor.shutdownNodeMain(pub_navsat2);
            old_imu = false;
            old_magnetic = false;
            old_navsat = false;
        }

        // IMU node startup
        if(checkbox_imu.isChecked() != old_imu && checkbox_imu.isChecked()) {
            NodeConfiguration nodeConfiguration3 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration3.setMasterUri(masterURI);
            nodeConfiguration3.setNodeName("sensors_driver_imu");
            this.pub_imu = new ImuPublisher(mSensorManager, sensorDelay, robot_name_text);
            nodeMainExecutor.execute(this.pub_imu, nodeConfiguration3);
        }
        // IMU node shutdown
        else if(checkbox_imu.isChecked() != old_imu) {
            nodeMainExecutor.shutdownNodeMain(pub_imu);
            pub_imu = null;
        }

        // Magnetic node startup
        if(checkbox_magnetic.isChecked() != old_magnetic && checkbox_magnetic.isChecked()) {
            NodeConfiguration nodeConfiguration4 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration4.setMasterUri(masterURI);
            nodeConfiguration4.setNodeName("driver_magnetic_field");
            this.pub_magnetic = new MagneticFieldPublisher(mSensorManager, sensorDelay, robot_name_text);
            nodeMainExecutor.execute(this.pub_magnetic, nodeConfiguration4);
        }
        // Magnetic node shutdown
        else if(checkbox_magnetic.isChecked() != old_magnetic) {
            nodeMainExecutor.shutdownNodeMain(pub_magnetic);
            pub_magnetic = null;
        }

        // Navigation satellite node startup
        if(checkbox_navsat.isChecked() != old_navsat && checkbox_navsat.isChecked()) {
            NodeConfiguration nodeConfiguration5 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration5.setMasterUri(masterURI);
            nodeConfiguration5.setNodeName("driver_navsatfix_publisher");
            this.pub_navsat2 = new NavSatFixPublisher(mainActivity, robot_name_text);
            nodeMainExecutor.execute(this.pub_navsat2, nodeConfiguration5);
        }
        // Navigation satellite node shutdown
        else if(checkbox_navsat.isChecked() != old_navsat) {
            nodeMainExecutor.shutdownNodeMain(pub_navsat2);
            pub_navsat2 = null;
        }

        // Finally, update our old states
        old_robot_name = robot_name.getText().toString();
        old_imu = checkbox_imu.isChecked();
        old_magnetic = checkbox_magnetic.isChecked();
        old_navsat = checkbox_navsat.isChecked();

        // Re-enable button
        button_config.setEnabled(true);
    }

    /**
     * Sets our node executor
     * This is called when the activity has been loaded
     */
    public void setNodeExecutor(NodeMainExecutor nodeExecutor) {
        this.masterURI = mainActivity.getMasterUri();
        this.nodeMainExecutor = nodeExecutor;
    }
}
