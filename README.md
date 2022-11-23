# android_cits

Android App for publishing data of phone to a ROS master. 
The sensor data (GPS, IMU, Magnetic Field) on the phone is published as the ROS topic and show the GPS on the Google Map.

## ROS API
#### Pubs
* ```/'robot_name'/android/fix``` ([sensor_msgs/NavSatFix](http://docs.ros.org/en/melodic/api/sensor_msgs/html/msg/NavSatFix.html))  
  Information of GPS - longitude, latitude, elevation
* ```/'robot_name'/android/imu``` ([sensor_msgs/Imu](http://docs.ros.org/en/melodic/api/sensor_msgs/html/msg/Imu.html))  
  Information of IMU - orientation, angular_velocity
* ```/'robot_name'/android/magnetic_feild``` ([sensor_msgs/MagneticField](http://docs.ros.org/en/melodic/api/sensor_msgs/html/msg/MagneticField.html))  
  Information of Magnetic Field - magentic_field  
  
## Screenshots
#### Choose ROS Master
![android_cits_masterchooser](https://user-images.githubusercontent.com/31130917/203456917-3aa2c87d-956b-4ce8-8cc9-487450f0e5f8.png)

#### Setting
![android_cits_config](https://user-images.githubusercontent.com/31130917/203457034-6003327b-f723-4348-bcf1-4945ee28a804.png)

#### Main
![android_cits_main](https://user-images.githubusercontent.com/31130917/203457054-ea52f957-535a-4d4f-8ecf-d25f7f1871f7.png)

## Reference
* android_core (https://github.com/rosjava/android_core)
* android_sensors_driver (https://github.com/rpng/android_sensors_driver)
