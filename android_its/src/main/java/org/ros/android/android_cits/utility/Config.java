package org.ros.android.android_cits.utility;

import android.hardware.SensorManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import org.ros.address.InetAddressFactory;
import org.ros.android.android_cits.MainActivity;
import org.ros.android.android_cits.R;
import org.ros.android.android_cits.publisher.NavSatFixPublisher;
import org.ros.android.android_cits.publisher.OrientationPublisher;
import org.ros.node.NodeConfiguration;
import org.ros.node.NodeMainExecutor;

import java.net.URI;

public class Config {

    private MainActivity mainActivity;
    protected final int currentApiVersion = android.os.Build.VERSION.SDK_INT;
    protected URI masterURI;
    protected NodeMainExecutor nodeMainExecutor;

    protected EditText robot_name;
    protected CheckBox checkbox_orientation;
    protected CheckBox checkbox_navsat;
    protected Button button_config;

    protected String old_robot_name;
    protected boolean old_orientation;
    protected boolean old_navsat;

    protected OrientationPublisher pub_orientation;
    protected NavSatFixPublisher pub_navsat2;

    protected SensorManager mSensorManager;


    public Config(MainActivity mainActivity) {
        // Save our activity pointer
        this.mainActivity = mainActivity;
        this.nodeMainExecutor = null;

        // Load our references
        robot_name = (EditText) mainActivity.findViewById(R.id.robot_name);
        checkbox_orientation = (CheckBox) mainActivity.findViewById(R.id.checkbox_orientation);
        checkbox_navsat = (CheckBox) mainActivity.findViewById(R.id.checkbox_navsat);
        button_config = (Button) mainActivity.findViewById(R.id.config_sensor);

        // Load old variables, booleans default to false
        old_robot_name = robot_name.getText().toString();

        // Start the services we need
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
            nodeMainExecutor.shutdownNodeMain(pub_orientation);
            nodeMainExecutor.shutdownNodeMain(pub_navsat2);
            old_orientation = false;
            old_navsat = false;
        }

        // Orientation node startup
        if(checkbox_orientation.isChecked() != old_orientation && checkbox_orientation.isChecked()) {
            NodeConfiguration nodeConfiguration3 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration3.setMasterUri(masterURI);
            nodeConfiguration3.setNodeName("orientation_driver" + robot_name_text);
            this.pub_orientation = new OrientationPublisher(mSensorManager, sensorDelay, robot_name_text);
            nodeMainExecutor.execute(this.pub_orientation, nodeConfiguration3);
        }
        // Orientation node shutdown
        else if(checkbox_orientation.isChecked() != old_orientation) {
            nodeMainExecutor.shutdownNodeMain(pub_orientation);
            pub_orientation = null;
        }

        // Navigation satellite node startup
        if(checkbox_navsat.isChecked() != old_navsat && checkbox_navsat.isChecked()) {
            NodeConfiguration nodeConfiguration5 = NodeConfiguration.newPublic(InetAddressFactory.newNonLoopback().getHostAddress());
            nodeConfiguration5.setMasterUri(masterURI);
            nodeConfiguration5.setNodeName("navsatfix_driver" + robot_name_text);
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
        old_orientation = checkbox_orientation.isChecked();
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
