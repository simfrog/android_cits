# android_cits

Android App for publishing data of phone to a ROS master. 
The sensor data (GPS, Azimuth) on the phone is published as the ROS topic and show the GPS on the Google Map.

## ROS API
#### Pubs
* ```/'robot_name'/android/fix``` ([sensor_msgs/NavSatFix](http://docs.ros.org/en/melodic/api/sensor_msgs/html/msg/NavSatFix.html))  
  Information of GPS - longitude, latitude, elevation
* ```/'robot_name'/android/orientation``` ([geometry_msgs/PoseStamped](http://docs.ros.org/en/noetic/api/geometry_msgs/html/msg/PoseStamped.html))
  Information of Azimuth - Calculation of azimuth using imu and magnetic field sensors
  
## Screenshots
#### Choose ROS Master
![android_cits_masterchooser](https://user-images.githubusercontent.com/31130917/203456917-3aa2c87d-956b-4ce8-8cc9-487450f0e5f8.png)

#### Setting
![android_config_modify](https://user-images.githubusercontent.com/31130917/208560631-b90a506d-ab18-46f4-859e-796b116ea2f2.png)

#### Main
![android_cits_main](https://user-images.githubusercontent.com/31130917/203457054-ea52f957-535a-4d4f-8ecf-d25f7f1871f7.png)

## Incomplete
* Subscriber

## Reference
* android_core (https://github.com/rosjava/android_core)
* android_sensors_driver (https://github.com/rpng/android_sensors_driver)
