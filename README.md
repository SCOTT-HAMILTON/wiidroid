<p align="center">
      <a href="https://scott-hamilton.mit-license.org/"><img alt="MIT License" src="https://img.shields.io/badge/License-MIT-525252.svg?labelColor=292929&logo=creative%20commons&style=for-the-badge" /></a>
</p>
<h1 align="center">wiidroid - Android library to use the Wiimote</h1>

## Status
This library is not working yet, the pairing process is working but I still can't connect to the wiimote. Work was tested on an Android 12 device (Samsung A52) with supposedly HID capabilities.
Several methods were tried in vain:
 - l2cap connection directly without using the "HID capabilities" of android. I tried to create a socket with both `device.createL2capChannel(pcm)` and `device.createInsecureL2capChannel(pcm)` with `pcm=0x13` (Data Pipe channel cf [wiibrew](http://www.wiibrew.org/wiki/Wiimote/Protocol#HID_Interface)). But both socket couldn't be connected, `socket.connect()` always returned false and crashed with a timeout or error. I essentially have the same issue as the one in [this thread](https://stackoverflow.com/q/59996168)

 - if even low level l2cap connection isn't working, there is little chance for higher level protocoles like HID to work. But by reading the AOSP, I found this [BluetoothHidHost](https://android.googlesource.com/platform/frameworks/base.git/+/5e1c9fe7fc35fd9dfc0703379c380f3eec47cfd6/core/java/android/bluetooth/BluetoothHidHost.java) class. So I tried some java reflection to use it despite it not being in the public API. I could get the proxy by giving `HID_HOST=4` as a profile enum to `BluetoothAdapter.getProfileProxy` but the proxy couldn't connect devices, the same timeout as with the l2cap method occures.

## Hope
As the `BluetoothHidHost` is still not in the public API, there is hope for it to become available in the future, which might mean that wiimote controller could be used with newer phones in the future.

## Dependencies
 - Jetpack Compose

## Building
This project is configured with Android Studio, it builds with gradle

## License
wiidroid is delivered as it is under the well known MIT License.

**References that helped**
 - [wiibrew's site] : <https://wiibrew.org/wiki/Wiimote/Protocol>
 - [android doc's bluetooth guide] : <https://developer.android.com/guide/topics/connectivity/bluetooth/setup>
 - [motej's implementation] : <https://github.com/pkoenig10/motej/>
 - [android documentation] : <https://developer.android.com/>

[//]: # (These are reference links used in the body of this note and get stripped out when the markdown processor does its job. There is no need to format nicely because it shouldn't be seen. Thanks SO - http://stackoverflow.com/questions/4823468/store-comments-in-markdown-syntax)

   [wiibrew's site]: <https://wiibrew.org/wiki/Wiimote/Protocol>
   [android doc's bluetooth guide]: <https://developer.android.com/guide/topics/connectivity/bluetooth/setup>
   [motej's implementation]: <https://github.com/pkoenig10/motej/>
   [android documentation]: <https://developer.android.com/>
