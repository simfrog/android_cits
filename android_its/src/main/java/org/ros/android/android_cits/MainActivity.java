/*
 * Copyright (C) 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ros.android.android_cits;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import org.ros.android.RosActivity;
import org.ros.android.android_cits.utility.Config;
import org.ros.node.NodeMainExecutor;

/**
 * @author chadrockey@gmail.com (Chad Rockey)
 * @author axelfurlan@gmail.com (Axel Furlan)
 */
public class MainActivity extends RosActivity {
    
    protected Config config;
    protected Button button_config;

    public View mMainView;
    public View mConfigView;
    private int mShortAnimationDuration;

    public MainActivity() {
        super("ROS CITS", "ROS CITS");
    }

    /**
     * Method called on app creation
     * Load our views, and display our activity view
     * Load our config and its values
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Set our content view as our main activity that has fragments in it
        setContentView(R.layout.activity_main);

        // Load the different views
        mMainView = findViewById(R.id.view_main);
        mConfigView = findViewById(R.id.view_config);

        // Initially hide the main view, we want to configure the unit first
        mMainView.setVisibility(View.GONE);
        mConfigView.setVisibility(View.VISIBLE);

        // Retrieve and cache the system's default "short" animation time.
        mShortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

        // Create our config
        config = new Config(this);

        // Load our listeners on all the objects
        loadListeners();
    }

    /**
     * This function loads all needed listeners for the project
     * Handles all the views inside the main_activity
     */
    public void loadListeners() {
        // Config menu update button
        button_config = (Button) findViewById(R.id.config_sensor);
        button_config.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                // Update the publishers
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            // Try to update
                            config.update_publishers();
                        } catch(Exception e) {
                            // Tell the user it failed
                            Toast toast = Toast.makeText(MainActivity.this,
                                    "Driver Configuration Failed", Toast.LENGTH_SHORT);
                            toast.show();
                            // Re-enable button
                            button_config.setEnabled(true);
                            // Debug
                            e.printStackTrace();
                        }
                    }
                });
                // Toggle the views
                toggleConfigView();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    /**
     * When we have initialize pass our executor to the config
     * The config needs this to launch nodes
     */
    @Override
    protected void init(NodeMainExecutor nodeMainExecutor) {
        config.setNodeExecutor(nodeMainExecutor);
    }

    /**
     * Cross-fades between {@link #mMainView} and {@link #mConfigView}
     * This is called when the config button is pressed, or after submitting the config form
     * http://developer.android.com/training/animation/crossfade.html#views
     */
    private void toggleConfigView() {
        // Decide which view to hide and which to show.
        boolean state = mConfigView.getVisibility() != View.VISIBLE;
        final View showView = state ? mConfigView : mMainView;
        final View hideView = state ? mMainView : mConfigView;

        // Set the "show" view to 0% opacity but visible, so that it is visible
        // (but fully transparent) during the animation.
        showView.setAlpha(0f);
        showView.setVisibility(View.VISIBLE);

        // Animate the "show" view to 100% opacity, and clear any animation listener set on
        // the view. Remember that listeners are not limited to the specific animation
        // describes in the chained method calls. Listeners are set on the
        // ViewPropertyAnimator object for the view, which persists across several
        // animations.
        showView.animate().alpha(1f).setDuration(mShortAnimationDuration).setListener(null);

        // Animate the "hide" view to 0% opacity. After the animation ends, set its visibility
        // to GONE as an optimization step (it won't participate in layout passes, etc.)
        hideView.animate()
                .alpha(0f)
                .setDuration(mShortAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        hideView.setVisibility(View.GONE);
                    }
                });
    }


}