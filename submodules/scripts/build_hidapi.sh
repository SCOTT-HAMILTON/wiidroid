#!/bin/sh
# ./build_hidapi.sh <path/to/ndk-build> <path/to/project_dir> <path/to/output/libs_dir> <abi>
mkdir -p "$3/$4"
"$1" \
	NDK_PROJECT_PATH=null \
	APP_BUILD_SCRIPT=$2/../submodules/hidapi/android/jni/Android.mk \
	APP_ABI=$4 \
	NDK_ALL_ABIS=$4 \
	NDK_DEBUG=1 \
	APP_PLATFORM=android-21 \
	NDK_OUT=$2/build/intermediates/cxx/Debug/hidapi/obj \
	NDK_LIBS_OUT=$3 \
	APP_SHORT_COMMANDS=false \
	LOCAL_SHORT_COMMANDS=false
