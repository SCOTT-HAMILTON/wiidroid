#include <jni.h>
#include <string>
#include <android/log.h>
#include <hidapi.h>
#include <libusb.h>

#define  LOG_TAG    "MainActivityJni"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jstring JNICALL
Java_org_scotthamilton_wiidroid_MainActivity_testInitHIDAPI(
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
