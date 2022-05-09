#include <jni.h>
#include <string>
#include <android/log.h>
#include <hidapi.h>
#include <libusb.h>
#include <locale.h>
#include <iostream>
#include <iomanip>

#define  LOG_TAG    "MainActivityJni"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)


extern "C" JNIEXPORT jstring JNICALL
Java_org_scotthamilton_wiidroid_bluetooth_WiimoteManagerImpl_initHID(
        JNIEnv* env,
        jobject /* this */) {
    int r = libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
    if (r != LIBUSB_SUCCESS) {
        LOGD("libusb_set_option failed: %d\n", r);
    } else {
        r = hid_init();
        LOGD("hid_init()=%d\n", r);
    }
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}

extern "C" JNIEXPORT jint JNICALL
Java_org_scotthamilton_wiidroid_bluetooth_WiimoteManagerImpl_connectHID(
        JNIEnv* env,
        jobject /* this */) {
    {
        hid_device *handle = hid_open(0x57e, 0x306, NULL);
        if (!handle) {
            LOGD("error, device couldn't be open\n");
        } else {
            LOGD("device successfully opened");
        }
    }

    return 0;
}