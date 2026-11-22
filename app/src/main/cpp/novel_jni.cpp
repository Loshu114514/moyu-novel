#include <android/log.h>
#include <jni.h>
#include <unistd.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <string>
#include <vector>

#include "chat.h"
#include "common.h"
#include "llama.h"
#include "ggml-backend.h"
#include "sampling.h"

#define LOG_TAG "MoyuLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr int   BATCH_SIZE        = 512;
constexpr int   N_THREADS_MIN     = 2;
constexpr int   N_THREADS_MAX     = 8;
constexpr int   CONTEXT_HEADROOM  = 8;

llama_model              * g_model    = nullptr;
llama_context            * g_ctx      = nullptr;
common_chat_templates_ptr  g_tmpl;
common_batch               g_batch;
common_sampler           * g_smpl     = nullptr;

std::vector<common_chat_msg> g_msgs;
llama_pos                    g_pos          = 0;
llama_pos                    g_stop_pos     = 0;
int                          g_ctx_size     = 4096;
int                          g_threads      = 4;
int                          g_n_predict    = 0;
std::string                  g_utf8_cache;
std::string                  g_generated;

std::string jstring_to_string(JNIEnv * env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char * chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

bool is_valid_utf8(const char * string) {
    if (string == nullptr) {
        return true;
    }
    const auto * bytes = reinterpret_cast<const unsigned char *>(string);
    while (*bytes != 0x00) {
        int num;
        if ((*bytes & 0x80) == 0x00) {
            num = 1;
        } else if ((*bytes & 0xE0) == 0xC0) {
            num = 2;
        } else if ((*bytes & 0xF0) == 0xE0) {
            num = 3;
        } else if ((*bytes & 0xF8) == 0xF0) {
            num = 4;
        } else {
            return false;
        }
        bytes += 1;
        for (int i = 1; i < num; ++i) {
            if ((*bytes & 0xC0) != 0x80) {
                return false;
            }
            bytes += 1;
        }
    }
    return true;
}

void llama_log_callback(enum ggml_log_level level, const char * text, void * /*user_data*/) {
    if (text == nullptr) {
        return;
    }
    if (level == GGML_LOG_LEVEL_ERROR) {
        LOGE("%s", text);
    } else if (level == GGML_LOG_LEVEL_WARN) {
        LOGW("%s", text);
    } else {
        LOGI("%s", text);
    }
}

void free_sampler() {
    if (g_smpl != nullptr) {
        common_sampler_free(g_smpl);
        g_smpl = nullptr;
    }
}

bool decode_tokens(const std::vector<llama_token> & tokens, llama_pos start_pos, bool logits_on_last) {
    if (g_ctx == nullptr) {
        return false;
    }
    for (size_t i = 0; i < tokens.size(); i += BATCH_SIZE) {
        const int n = static_cast<int>(std::min<size_t>(BATCH_SIZE, tokens.size() - i));
        if (start_pos + static_cast<llama_pos>(i) + n >= g_ctx_size - CONTEXT_HEADROOM) {
            LOGE("context overflow: %d + %d + %d >= %d", start_pos, (int) i, n, g_ctx_size);
            return false;
        }
        g_batch.clear();
        for (int j = 0; j < n; ++j) {
            const bool want_logits = logits_on_last && (i + j == tokens.size() - 1);
            g_batch.add(tokens[i + j], start_pos + static_cast<llama_pos>(i + j), 0, want_logits);
        }
        if (llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
            LOGE("llama_process failed");
            return false;
        }
    }
    return true;
}

bool decode_text(const std::string & text, bool add_special) {
    if (g_ctx == nullptr) {
        return false;
    }
    std::vector<llama_token> tokens = common_tokenize(g_ctx, text, add_special, true);
    if (tokens.empty()) {
        return true;
    }
    if (!decode_tokens(tokens, g_pos, true)) {
        return false;
    }
    g_pos += static_cast<llama_pos>(tokens.size());
    return true;
}

std::string format_single_message(const std::string & role, const std::string & content,
                                  const std::string & reasoning, bool add_assistant) {
    common_chat_msg msg;
    msg.role = role;
    msg.content = content;
    msg.reasoning_content = reasoning;

    const bool has_template = g_tmpl != nullptr && common_chat_templates_was_explicit(g_tmpl.get());
    std::string formatted = content;
    if (has_template) {
        formatted = common_chat_format_single(g_tmpl.get(), g_msgs, msg, add_assistant, false);
    }
    return formatted;
}

bool add_and_decode_message(const std::string & role, const std::string & content,
                            const std::string & reasoning, bool add_assistant) {
    if (g_ctx == nullptr) {
        return false;
    }
    const bool first = g_msgs.empty();
    std::string formatted = format_single_message(role, content, reasoning, add_assistant);
    common_chat_msg msg;
    msg.role = role;
    msg.content = content;
    msg.reasoning_content = reasoning;

    if (!decode_text(formatted, first)) {
        return false;
    }
    g_msgs.push_back(std::move(msg));
    return true;
}

void reset_state() {
    free_sampler();
    g_msgs.clear();
    g_pos = 0;
    g_stop_pos = 0;
    g_n_predict = 0;
    g_utf8_cache.clear();
    g_generated.clear();
    if (g_ctx != nullptr) {
        llama_memory_clear(llama_get_memory(g_ctx), false);
    }
}

} // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_moyu_novel_local_LocalEngine_nativeInit(JNIEnv * env, jobject /*thiz*/, jstring native_lib_dir) {
    llama_log_set(llama_log_callback, nullptr);
    const std::string dir = jstring_to_string(env, native_lib_dir);
    if (!dir.empty()) {
        ggml_backend_load_all_from_path(dir.c_str());
    }
    ggml_backend_load_all();
    llama_backend_init();
    LOGI("llama backend initialized");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moyu_novel_local_LocalEngine_loadModel(JNIEnv * env, jobject /*thiz*/, jstring model_path,
                                                jint n_ctx, jint n_threads) {
    const std::string path = jstring_to_string(env, model_path);
    if (path.empty()) {
        return JNI_FALSE;
    }

    if (g_ctx != nullptr) {
        reset_state();
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_tmpl.reset();
    g_batch = common_batch();

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(path.c_str(), model_params);
    if (g_model == nullptr) {
        LOGE("failed to load model: %s", path.c_str());
        return JNI_FALSE;
    }

    const int wanted_ctx = std::max(512, (int) n_ctx);
    g_threads = std::max(N_THREADS_MIN, std::min(N_THREADS_MAX, (int) n_threads));

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = wanted_ctx;
    ctx_params.n_batch = BATCH_SIZE;
    ctx_params.n_ubatch = 256;
    ctx_params.n_threads = g_threads;
    ctx_params.n_threads_batch = g_threads;
    ctx_params.no_perf = true;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        LOGE("failed to create context");
        llama_model_free(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    g_ctx_size = static_cast<int>(llama_n_ctx(g_ctx));
    g_batch = common_batch(g_ctx);
    g_tmpl = common_chat_templates_init(g_model, "");
    g_msgs.clear();
    g_pos = 0;
    LOGI("model loaded, n_ctx=%d threads=%d", g_ctx_size, g_threads);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moyu_novel_local_LocalEngine_isLoaded(JNIEnv * /*env*/, jobject /*thiz*/) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_moyu_novel_local_LocalEngine_modelDesc(JNIEnv * env, jobject /*thiz*/) {
    if (g_model == nullptr) {
        return env->NewStringUTF("");
    }
    char desc[256];
    llama_model_desc(g_model, desc, sizeof(desc));
    return env->NewStringUTF(desc);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_moyu_novel_local_LocalEngine_nativeContextSize(JNIEnv * /*env*/, jobject /*thiz*/) {
    return g_ctx_size;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_moyu_novel_local_LocalEngine_pastTokens(JNIEnv * /*env*/, jobject /*thiz*/) {
    return static_cast<jint>(g_pos);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_moyu_novel_local_LocalEngine_countTokens(JNIEnv * env, jobject /*thiz*/, jstring text) {
    if (g_ctx == nullptr) {
        return 0;
    }
    const std::string value = jstring_to_string(env, text);
    const auto tokens = common_tokenize(g_ctx, value, false, true);
    return static_cast<jint>(tokens.size());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moyu_novel_local_LocalEngine_newChat(JNIEnv * env, jobject /*thiz*/, jstring system_prompt) {
    if (g_ctx == nullptr) {
        return JNI_FALSE;
    }
    reset_state();
    const std::string system = jstring_to_string(env, system_prompt);
    if (system.empty()) {
        return JNI_TRUE;
    }
    const bool has_template = g_tmpl != nullptr && common_chat_templates_was_explicit(g_tmpl.get());
    std::string formatted = system;
    common_chat_msg msg;
    msg.role = "system";
    msg.content = system;
    if (has_template) {
        formatted = common_chat_format_single(g_tmpl.get(), g_msgs, msg, false, false);
    }
    if (!decode_text(formatted, true)) {
        return JNI_FALSE;
    }
    g_msgs.push_back(std::move(msg));
    LOGI("new chat, system tokens decoded, pos=%d", g_pos);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moyu_novel_local_LocalEngine_addUser(JNIEnv * env, jobject /*thiz*/, jstring text) {
    return add_and_decode_message("user", jstring_to_string(env, text), "", true) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moyu_novel_local_LocalEngine_addAssistantHistory(JNIEnv * env, jobject /*thiz*/, jstring text,
                                                          jstring reasoning) {
    return add_and_decode_message("assistant", jstring_to_string(env, text),
                                  jstring_to_string(env, reasoning), false) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_moyu_novel_local_LocalEngine_nativeFinishAssistant(JNIEnv * env, jobject /*thiz*/, jstring text,
                                                      jstring reasoning) {
    if (g_ctx == nullptr) {
        return;
    }
    const std::string content = jstring_to_string(env, text);
    const std::string reason = jstring_to_string(env, reasoning);
    // Format (and remember) the assistant turn without decoding it again: the generated
    // tokens are already in the KV cache. This keeps the chat template state in sync.
    format_single_message("assistant", content, reason, false);
    common_chat_msg msg;
    msg.role = "assistant";
    msg.content = content;
    msg.reasoning_content = reason;
    g_msgs.push_back(std::move(msg));
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_moyu_novel_local_LocalEngine_startGeneration(JNIEnv * /*env*/, jobject /*thiz*/,
                                                      jint max_tokens, jfloat temp, jint top_k,
                                                      jfloat top_p, jfloat min_p,
                                                      jfloat repeat_penalty) {
    if (g_ctx == nullptr || g_model == nullptr) {
        return JNI_FALSE;
    }
    free_sampler();

    common_params_sampling sparams;
    sparams.temp = temp;
    sparams.top_k = top_k;
    sparams.top_p = top_p;
    sparams.min_p = min_p;
    sparams.penalty_last_n = 64;
    sparams.penalty_repeat = repeat_penalty;
    sparams.seed = LLAMA_DEFAULT_SEED;
    g_smpl = common_sampler_init(g_model, sparams);
    if (g_smpl == nullptr) {
        LOGE("failed to create sampler");
        return JNI_FALSE;
    }

    g_n_predict = std::max(1, (int) max_tokens);
    g_stop_pos = g_pos + g_n_predict;
    g_utf8_cache.clear();
    g_generated.clear();
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_moyu_novel_local_LocalEngine_nextToken(JNIEnv * env, jobject /*thiz*/) {
    if (g_ctx == nullptr || g_smpl == nullptr) {
        return nullptr;
    }
    if (g_n_predict <= 0 || g_pos >= g_stop_pos) {
        return nullptr;
    }
    if (g_pos >= g_ctx_size - CONTEXT_HEADROOM) {
        LOGW("context full, stopping");
        return nullptr;
    }

    const llama_token token = common_sampler_sample(g_smpl, g_ctx, -1);
    common_sampler_accept(g_smpl, token, true);

    g_batch.clear();
    g_batch.add(token, g_pos, 0, true);
    if (llama_process(g_ctx, LLAMA_PROCESS_TYPE_DECODE, g_batch.get()) != 0) {
        LOGE("decode of generated token failed");
        return nullptr;
    }
    g_pos++;
    g_n_predict--;

    if (llama_vocab_is_eog(llama_model_get_vocab(g_model), token)) {
        LOGI("eog reached");
        return nullptr;
    }

    const std::string piece = common_token_to_piece(g_ctx, token);
    g_utf8_cache += piece;
    if (!is_valid_utf8(g_utf8_cache.c_str())) {
        return env->NewStringUTF("");
    }
    const std::string out = g_utf8_cache;
    g_utf8_cache.clear();
    g_generated += out;
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_moyu_novel_local_LocalEngine_freeModel(JNIEnv * /*env*/, jobject /*thiz*/) {
    reset_state();
    g_tmpl.reset();
    g_batch = common_batch();
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_ctx_size = 4096;
    LOGI("model freed");
}

extern "C" JNIEXPORT void JNICALL
Java_com_moyu_novel_local_LocalEngine_nativeShutdown(JNIEnv * /*env*/, jobject /*thiz*/) {
    free_sampler();
    g_tmpl.reset();
    if (g_ctx != nullptr) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
}
