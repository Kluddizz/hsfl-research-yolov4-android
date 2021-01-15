LOCAL_PATH := $(call my-dir)

NCNN_INSTALL_PATH := ${LOCAL_PATH}/ncnn-android-vulkan/${TARGET_ARCH_ABI}

include $(CLEAR_VARS)
LOCAL_MODULE := ncnn
LOCAL_SRC_FILES := $(NCNN_INSTALL_PATH)/lib/libncnn.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := darknet
LOCAL_SRC_FILES := darknet_jni.cpp
LOCAL_C_INCLUDES := $(NCNN_INSTALL_PATH)/include
LOCAL_STATIC_LIBRARIES := ncnn
include $(BUILD_SHARED_LIBRARY)