# wiidroid
Android library to use the Wiimote.

## Status
This library is not working yet, the pairing process is working but I still can't
connect to the wiimote. Work was tested on an Android 12 device (Samsung A52) with supposedly HID capabilities.
Several methods were tried in vain:
 - l2cap connection directly without using the "HID capabilities" of android. I tried to create a socket with both `device.createL2capChannel(pcm)` and `device.createInsecureL2capChannel(pcm)` with `pcm=0x13` (Data Pipe channel cf [wiibrew](http://www.wiibrew.org/wiki/Wiimote/Protocol#HID_Interface)). But both socket couldn't be connected, `socket.connect()` always returned false and crashed with a timeout or error. I essentially have the same issue as the one in [this thread](https://stackoverflow.com/q/59996168)

 - if even low level l2cap connection isn't working, there is little chance for higher level protocoles like HID to work.
