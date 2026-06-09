// llama_jni — JNI bridge for FR-004 GGUF model loading.
//
// When LLAMA_CPP_AVAILABLE is defined (submodule present), bridges to
// llama.cpp directly. Otherwise compiles in STUB mode so the project
// still builds and CI can exercise the Kotlin layer.
//
// Threading: every entry point is blocking; Kotlin side ensures invocation
// happens on Dispatchers.IO (ADR-004 §3.2).

#include <jni.h>
#include <android/log.h>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <string>

#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
  #include "llama.h"
#endif

#define LOG_TAG "llama_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// GGUF magic bytes: 'G' 'G' 'U' 'F' (0x46554747 little-endian).
constexpr uint32_t kGgufMagic = 0x46554747u;

bool check_gguf_magic(const char* path) {
    FILE* f = std::fopen(path, "rb");
    if (!f) {
        return false;
    }
    uint32_t magic = 0;
    const size_t n = std::fread(&magic, sizeof(magic), 1, f);
    std::fclose(f);
    return n == 1 && magic == kGgufMagic;
}

std::string jstring_to_std(JNIEnv* env, jstring s) {
    if (!s) {
        return {};
    }
    const char* raw = env->GetStringUTFChars(s, nullptr);
    std::string out(raw);
    env->ReleaseStringUTFChars(s, raw);
    return out;
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_loadModel(
        JNIEnv* env, jobject /*this*/, jstring jpath, jboolean useMmap) {
    const std::string path = jstring_to_std(env, jpath);
    if (path.empty()) {
        LOGE("loadModel: empty path");
        return 0L;
    }
    if (!check_gguf_magic(path.c_str())) {
        LOGE("loadModel: not a GGUF file: %s", path.c_str());
        return 0L;
    }

#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    llama_backend_init();
    llama_model_params params = llama_model_default_params();
    params.use_mmap = useMmap;
    params.use_mlock = false;
    llama_model* model = llama_load_model_from_file(path.c_str(), params);
    if (!model) {
        LOGE("llama_load_model_from_file returned NULL for %s", path.c_str());
        return 0L;
    }
    LOGI("Loaded model: %s (mmap=%d)", path.c_str(), useMmap);
    return reinterpret_cast<jlong>(model);
#else
    // STUB mode: pretend to load. Returns a non-zero sentinel that
    // freeModel will safely accept. Used only when llama.cpp submodule
    // is not pulled (CI / first-time clone).
    LOGI("loadModel STUB: %s (mmap=%d)", path.c_str(), useMmap);
    return reinterpret_cast<jlong>(new int(0xC0FFEE));
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_freeModel(
        JNIEnv* /*env*/, jobject /*this*/, jlong nativePtr) {
    if (nativePtr == 0L) {
        return;
    }
#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    auto* model = reinterpret_cast<llama_model*>(nativePtr);
    llama_free_model(model);
    llama_backend_free();
    LOGI("Freed model ptr=%lld", static_cast<long long>(nativePtr));
#else
    delete reinterpret_cast<int*>(nativePtr);
    LOGI("Freed STUB ptr=%lld", static_cast<long long>(nativePtr));
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_isValidGguf(
        JNIEnv* env, jobject /*this*/, jstring jpath) {
    const std::string path = jstring_to_std(env, jpath);
    if (path.empty()) {
        return JNI_FALSE;
    }
    return check_gguf_magic(path.c_str()) ? JNI_TRUE : JNI_FALSE;
}
