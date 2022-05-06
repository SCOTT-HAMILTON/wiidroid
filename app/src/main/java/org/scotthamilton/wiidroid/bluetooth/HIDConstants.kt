package org.scotthamilton.wiidroid.bluetooth

import org.scotthamilton.wiidroid.bluetooth.utils.toGoodByteArray

const val GAMEPAD_REPORT_ID: Int = 0x01

const val SUBCLASS_NONE: Byte = 0x00

// https://android.googlesource.com/platform/frameworks/base.git/+/1f5ea66a39506a9fac15fae2adca688548d5ccc4/core/java/android/bluetooth/BluetoothProfile.java
// hidden API, waiting to get mainstream
const val HID_HOST = 4

val WiimoteHIDDescriptors = listOf(
    0x05, 0x01,                    // USAGE_PAGE (Generic Desktop)
    0x09, 0x05,                    // USAGE (Game Pad)
    0xa1, 0x01,                    // COLLECTION (Application)
    0x85, GAMEPAD_REPORT_ID,	   //   REPORT_ID (GAMEPAD_REPORT_ID)
    0x05, 0x01,                    //   USAGE_PAGE (Generic Desktop)
    0x09, 0x30,                    //   USAGE (X)
    0x09, 0x31,                    //   USAGE (Y)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x26, 0xFF, 0x00,			   //   LOGICAL_MAXIMUM (255)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x02,                    //   REPORT_COUNT (2)
    0x81, 0x02,                    //   INPUT (Data,Var,Abs)
    0x05, 0x09,                    //   USAGE_PAGE (Button)
    0x19, 0x01,                    //   USAGE_MINIMUM (Button 1)
    0x29, 0x0D,                    //   USAGE_MAXIMUM (Button 13)
    0x15, 0x00,                    //   LOGICAL_MINIMUM (0)
    0x25, 0x01,                    //   LOGICAL_MAXIMUM (1)
    0x95, 0x0D,                    //   REPORT_COUNT (13)
    0x75, 0x01,                    //   REPORT_SIZE (1)
    0x81, 0x02,                    //   INPUT (Data,Var,Abs)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x75, 0x03,                    //   REPORT_SIZE (3)
    0x81, 0x03,                    //   INPUT (Cnst,Var,Abs)
    0x05, 0x01,                    //   USAGE_PAGE (Generic Desktop)
    0x09, 0x32,                    //   USAGE (Z)
    0x09, 0x33,                    //   USAGE (RX)
    0x09, 0x34,                    //   USAGE (RY)
    0x09, 0x35,                    //   USAGE (RZ)
    0x15, 0x00,					   //   LOGICAL_MINIMUM (0)
    0x26, 0xFF, 0x00,			   //   LOGICAL_MAXIMUM (255)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x04,                    //   REPORT_COUNT (4)
    0x81, 0x02,                    //   INPUT (Data,Var,Abs)
    0x05, 0x01,                    //   USAGE_PAGE (Generic Desktop)
    0x09, 0x39,                    //   USAGE (Hat Swtich)
    0x15, 0x01,                    //   LOGICAL_MINIMUM (1)
    0x25, 0x08,                    //   LOGICAL_MAXIMUM (8)
    0x35, 0x00,                    //   PHYSICAL_MINIMUM (0)
    0x46, 0x3b, 0x01,              //   PHYSICAL_MAXIMUM (315)
    0x55, 0x00,                    //   UNIT_EXPONENT (0)
    0x65, 0x14,                    //   UNIT (English Rotation: Angular Position)
    0x75, 0x08,                    //   REPORT_SIZE (8)
    0x95, 0x01,                    //   REPORT_COUNT (1)
    0x81, 0x4a,                    //   INPUT (Data,Var,Abs,Wrap,Null)
    0xc0
).toGoodByteArray()