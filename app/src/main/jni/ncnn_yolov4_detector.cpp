#include <jni.h>
#include <string>
#include <ncnn/gpu.h>
#include <ncnn/net.h>
#include <android/asset_manager_jni.h>
#include <android/log.h>

ncnn::Net yolov4;
ncnn::PoolAllocator pool_allocator;

typedef struct {
    float x1;
    float y1;
    float x2;
    float y2;
    float score;
    int label;
} BoxInfo;

ncnn::Option create_options(ncnn::Allocator& allocator) {
    ncnn::Option opt;

    if (ncnn::get_gpu_count() > 0)
        opt.use_vulkan_compute = true;

    opt.lightmode = true;
    opt.num_threads = 4;
    opt.blob_allocator = &allocator;

    return opt;
}

std::vector<BoxInfo> decode_infer(const ncnn::Mat& data, uint16_t width, uint16_t height) {
    std::vector<BoxInfo> boxes;

    for (int i = 0; i < data.h; i++) {
        BoxInfo box;

        const float* values = data.row(i);
        box.x1 = values[2] * (float)width;
        box.y1 = values[3] * (float)height;
        box.x2 = values[4] * (float)width;
        box.y2 = values[5] * (float)height;
        box.score = values[1];
        box.label = (int)values[0] - 1;

        boxes.push_back(box);
    }

    return boxes;
}

JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    ncnn::create_gpu_instance();
    return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM* vm, void* reserved) {
    ncnn::destroy_gpu_instance();
}

extern "C"
JNIEXPORT jboolean JNICALL Java_de_hsfl_research_movementdetection_detection_YoloDetector_init(JNIEnv* env, jobject, jobject assetManager, jstring binFile, jstring paramFile) {
    yolov4.opt = create_options(pool_allocator);
    AAssetManager* mgr = AAssetManager_fromJava(env, assetManager);

    const char* paramFileStr = env->GetStringUTFChars(paramFile, nullptr);
    if (yolov4.load_param(mgr, paramFileStr)) {
        __android_log_print(ANDROID_LOG_ERROR, "YOLOv4", "load_param_bin failed");
        return JNI_FALSE;
    }

    const char* binFileStr = env->GetStringUTFChars(binFile, nullptr);
    if (yolov4.load_model(mgr, binFileStr)) {
        __android_log_print(ANDROID_LOG_ERROR, "YOLOv4", "load_model failed");
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

extern "C"
JNIEXPORT jobjectArray JNICALL Java_de_hsfl_research_movementdetection_detection_YoloDetector_detect(JNIEnv* env, jobject, jobject image) {
    AndroidBitmapInfo img_size;
    AndroidBitmap_getInfo(env, image, &img_size);

    int input_size = 416;
    ncnn::Mat input = ncnn::Mat::from_android_bitmap_resize(env, image, ncnn::Mat::PIXEL_RGBA2RGB, input_size, input_size);

    float norm[3] = { 1 / 255.0f, 1 / 255.0f, 1 / 255.0f };
    float mean[3] = { 0.0f, 0.0f, 0.0f };
    input.substract_mean_normalize(mean, norm);

    ncnn::Extractor extractor = yolov4.create_extractor();
    extractor.set_light_mode(true);
    extractor.set_num_threads(4);
    extractor.input(0, input);

    ncnn::Mat blob;
    extractor.extract("output", blob);
    std::vector<BoxInfo> boxes = decode_infer(blob, img_size.width, img_size.height);

    jclass box_class = env->FindClass("de/hsfl/research/movementdetection/detection/Box");
    jmethodID constructor = env->GetMethodID(box_class, "<init>", "(FFFFFI)V");
    jobjectArray result = env->NewObjectArray(boxes.size(), box_class, nullptr);

    for (unsigned int i = 0; i < boxes.size(); i++) {
        BoxInfo box = boxes[i];

        env->PushLocalFrame(1);
        jobject obj = env->NewObject(box_class, constructor, box.x1, box.y1, box.x2, box.y2, box.score, box.label);
        obj = env->PopLocalFrame(obj);

        env->SetObjectArrayElement(result, i, obj);
    }

    return result;
}
