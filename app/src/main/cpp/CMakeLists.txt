cmake_minimum_required(VERSION 3.18.1)

project("wiidroid")

add_library(
        native-lib
        SHARED
        native-lib.cpp)

find_library(
        log-lib
        log)

# Thanks to https://stackoverflow.com/a/60073596
function(add_external_library)
    set(options)
    set(oneValueArgs TARGET WORKING_DIRECTORY OUTPUT COMMENT)
    set(multiValueArgs COMMAND INCLUDE_DIRS)
    cmake_parse_arguments(ARGS "${options}" "${oneValueArgs}" "${multiValueArgs}" "${ARGN}")

    # Specify how to build the result of the library
    add_custom_command(OUTPUT "${ARGS_OUTPUT}"
        COMMAND ${ARGS_COMMAND}
        WORKING_DIRECTORY "${ARGS_WORKING_DIRECTORY}"
        COMMENT "${ARGS_COMMENT}")

    # Create a target out of the library compilation result
    add_custom_target(${ARGS_TARGET}_target DEPENDS ${ARGS_OUTPUT})

    # Create an library target out of the library compilation result
    add_library(${ARGS_TARGET} SHARED IMPORTED GLOBAL)
    add_dependencies(${ARGS_TARGET} ${ARGS_TARGET}_target)

    # Specify where the library is and where to find the headers
    set_target_properties(${ARGS_TARGET}
            PROPERTIES
            OUTPUT_LIBRARY "${ARGS_OUTPUT}"
            INCLUDE_DIR "${ARGS_INCLUDE_DIRS}")
endfunction()

set(HIDAPI_LIBS_DIR ${PROJECT_DIR}/build/intermediates/jniLibs)
add_external_library(
        TARGET hidapi-lib
        COMMAND ${PROJECT_DIR}/../submodules/scripts/build_hidapi.sh
        ${NDK_BUILD}
        ${PROJECT_DIR}
        ${HIDAPI_LIBS_DIR}
        ${ANDROID_ABI}
        WORKING_DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}"
        OUTPUT "${HIDAPI_LIBS_DIR}/${ANDROID_ABI}/libhidapi.so"
        INCLUDE_DIRS "${PROJECT_DIR}/../submodules/hidapi/hidapi"
        COMMENT "Building libhidapi"
)

get_target_property(HIDAPI_LIB_PATH hidapi-lib OUTPUT_LIBRARY)
get_target_property(HIDAPI_INC_DIR hidapi-lib INCLUDE_DIR)

message(STATUS, " HIDAPI_INC_DIR=${HIDAPI_INC_DIR}")
message(STATUS, " HIDAPI_LIB_PATH=${HIDAPI_LIB_PATH}")

target_include_directories(
        native-lib
        PUBLIC
        ${HIDAPI_INC_DIR}
        ${PROJECT_DIR}/../submodules/libusb/libusb
)

target_link_libraries(
        native-lib
        ${log-lib} ${HIDAPI_LIB_PATH} "${HIDAPI_LIBS_DIR}/${ANDROID_ABI}/libusb1.0.so")