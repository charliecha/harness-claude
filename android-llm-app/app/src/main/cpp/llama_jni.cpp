// llama_jni — JNI bridge for FR-004 GGUF model loading and FR-006 inference.
//
// When LLAMA_CPP_AVAILABLE is defined (submodule present), bridges to
// llama.cpp directly. Otherwise compiles in STUB mode so the project
// still builds and CI can exercise the Kotlin layer.
//
// Threading: every entry point is blocking; Kotlin side ensures invocation
// happens on a background dispatcher (ADR-004 §3.2, ADR-006 §3.4).

#include <jni.h>
#include <android/log.h>
#include <cstdio>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

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
    llama_model* model = llama_model_load_from_file(path.c_str(), params);
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
    llama_model_free(model);
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

// ─── FR-006: Context lifecycle ────────────────────────────────────────────────

extern "C" JNIEXPORT jlong JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_createContext(
        JNIEnv* /*env*/, jobject /*this*/, jlong modelPtr, jint nCtx, jint nBatch) {
#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    auto* model = reinterpret_cast<llama_model*>(modelPtr);
    llama_context_params params = llama_context_default_params();
    params.n_ctx   = static_cast<uint32_t>(nCtx);
    params.n_batch = static_cast<uint32_t>(nBatch);
    llama_context* ctx = llama_init_from_model(model, params);
    if (!ctx) {
        LOGE("createContext: llama_init_from_model returned NULL");
        return 0L;
    }
    LOGI("createContext: ctx=%p n_ctx=%d n_batch=%d", ctx, nCtx, nBatch);
    return reinterpret_cast<jlong>(ctx);
#else
    LOGI("createContext STUB: modelPtr=%lld", static_cast<long long>(modelPtr));
    return reinterpret_cast<jlong>(new int(0xC0FFEE));
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_freeContext(
        JNIEnv* /*env*/, jobject /*this*/, jlong ctxPtr) {
    if (ctxPtr == 0L) return;
#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    auto* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    llama_free(ctx);
    LOGI("freeContext: ptr=%lld", static_cast<long long>(ctxPtr));
#else
    delete reinterpret_cast<int*>(ctxPtr);
    LOGI("freeContext STUB: ptr=%lld", static_cast<long long>(ctxPtr));
#endif
}

extern "C" JNIEXPORT void JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_clearKvCache(
        JNIEnv* /*env*/, jobject /*this*/, jlong ctxPtr) {
    if (ctxPtr == 0L) return;
#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    auto* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    llama_kv_self_clear(ctx);
    LOGI("clearKvCache: ptr=%lld", static_cast<long long>(ctxPtr));
#else
    LOGI("clearKvCache STUB");
#endif
}

// ─── FR-006: Token operations ─────────────────────────────────────────────────

extern "C" JNIEXPORT jintArray JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_tokenize(
        JNIEnv* env, jobject /*this*/, jlong ctxPtr, jstring jtext, jboolean addBos) {
#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    auto* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    const llama_vocab* vocab = llama_model_get_vocab(llama_get_model(ctx));
    const std::string text = jstring_to_std(env, jtext);

    // First call with n_tokens_max=0 returns negative count of needed tokens.
    const int needed = -llama_tokenize(vocab, text.c_str(),
                                       static_cast<int32_t>(text.size()),
                                       nullptr, 0, addBos, false);
    if (needed <= 0) {
        return env->NewIntArray(0);
    }
    std::vector<llama_token> tokens(needed);
    llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()),
                   tokens.data(), needed, addBos, false);

    jintArray result = env->NewIntArray(needed);
    env->SetIntArrayRegion(result, 0, needed,
                           reinterpret_cast<const jint*>(tokens.data()));
    return result;
#else
    (void)ctxPtr; (void)jtext; (void)addBos;
    return env->NewIntArray(0);
#endif
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_tokenToText(
        JNIEnv* env, jobject /*this*/, jlong ctxPtr, jint tokenId) {
#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    auto* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    const llama_vocab* vocab = llama_model_get_vocab(llama_get_model(ctx));
    char buf[256];
    const int len = llama_token_to_piece(vocab, static_cast<llama_token>(tokenId),
                                         buf, sizeof(buf), 0, false);
    if (len <= 0) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(std::string(buf, len).c_str());
#else
    (void)ctxPtr; (void)tokenId;
    return env->NewStringUTF("");
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_isEog(
        JNIEnv* /*env*/, jobject /*this*/, jlong ctxPtr, jint tokenId) {
#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    auto* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    const llama_vocab* vocab = llama_model_get_vocab(llama_get_model(ctx));
    return llama_vocab_is_eog(vocab, static_cast<llama_token>(tokenId))
           ? JNI_TRUE : JNI_FALSE;
#else
    (void)ctxPtr; (void)tokenId;
    return JNI_FALSE;
#endif
}

// ─── FR-006: Inference ────────────────────────────────────────────────────────

extern "C" JNIEXPORT jint JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_decode(
        JNIEnv* env, jobject /*this*/, jlong ctxPtr, jintArray jtokenIds) {
#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    auto* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    const jsize n = env->GetArrayLength(jtokenIds);
    if (n <= 0) return 0;

    jint* ids = env->GetIntArrayElements(jtokenIds, nullptr);
    llama_batch batch = llama_batch_get_one(
        reinterpret_cast<llama_token*>(ids), static_cast<int32_t>(n));
    const int ret = llama_decode(ctx, batch);
    env->ReleaseIntArrayElements(jtokenIds, ids, JNI_ABORT);
    if (ret != 0) {
        LOGE("decode: llama_decode returned %d", ret);
    }
    return static_cast<jint>(ret);
#else
    (void)ctxPtr; (void)jtokenIds;
    return 0;
#endif
}

extern "C" JNIEXPORT jint JNICALL
Java_com_harnessclaude_llm_nativebridge_LlamaJni_sampleNext(
        JNIEnv* /*env*/, jobject /*this*/, jlong ctxPtr,
        jfloat temperature, jfloat topP, jint seed) {
#if defined(LLAMA_CPP_AVAILABLE) && LLAMA_CPP_AVAILABLE
    auto* ctx = reinterpret_cast<llama_context*>(ctxPtr);
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    llama_sampler* chain = llama_sampler_chain_init(sparams);
    llama_sampler_chain_add(chain, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    const uint32_t useed = (seed < 0)
        ? static_cast<uint32_t>(std::rand())
        : static_cast<uint32_t>(seed);
    llama_sampler_chain_add(chain, llama_sampler_init_dist(useed));

    const llama_token token = llama_sampler_sample(chain, ctx, -1);
    llama_sampler_free(chain);
    return static_cast<jint>(token);
#else
    (void)ctxPtr; (void)temperature; (void)topP; (void)seed;
    return 2; // EOS token id in most models — causes immediate EOG
#endif
}
