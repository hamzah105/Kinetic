#include <jni.h>
#include "llama.h"
#include <atomic>
#include <algorithm>
#include <chrono>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <vector>
#include <string>

namespace {
using Clock = std::chrono::steady_clock;
struct Session {
    std::atomic<bool> cancelled{false};
    Clock::time_point deadline = Clock::now() + std::chrono::seconds(120);
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    llama_sampler * sampler = nullptr;
    int generated = 0;
    bool completed = false;
    ~Session() {
        if (sampler) llama_sampler_free(sampler);
        if (context) llama_free(context);
        if (model) llama_model_free(model);
    }
    bool stopped() { return cancelled.load() || Clock::now() >= deadline; }
};
std::mutex registry_mutex;
std::unordered_map<jlong, std::shared_ptr<Session>> sessions;
jlong next_id = 1;
std::once_flag initialized;
std::shared_ptr<Session> find(jlong id) {
    std::lock_guard<std::mutex> guard(registry_mutex);
    auto entry = sessions.find(id);
    return entry == sessions.end() ? nullptr : entry->second;
}
void fail(JNIEnv * env, const char * code) {
    // Static codes only: never model text, file content, paths or credentials.
    auto type = env->FindClass("java/lang/IllegalStateException");
    if (type) env->ThrowNew(type, code);
}
bool abort_eval(void * data) { return static_cast<Session *>(data)->stopped(); }
bool progress(float, void * data) { return !static_cast<Session *>(data)->stopped(); }
void silent_log(enum ggml_log_level, const char *, void *) {}
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_kinetic_data_model_LlamaNative_create(JNIEnv * env, jobject) {
    try {
        std::call_once(initialized, [] { llama_log_set(silent_log, nullptr); llama_backend_init(); });
        std::lock_guard<std::mutex> guard(registry_mutex);
        auto id = next_id++;
        sessions.emplace(id, std::make_shared<Session>());
        return id;
    } catch (...) { fail(env, "LOCAL_UNAVAILABLE"); return 0; }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kinetic_data_model_LlamaNative_load(JNIEnv * env, jobject, jlong id, jstring path, jbyteArray input) {
    try {
        auto session = find(id);
        if (!session || session->model || session->stopped()) { fail(env, "LOCAL_UNAVAILABLE"); return; }
        const auto length = env->GetArrayLength(input);
        if (length <= 0 || length > 32768) { fail(env, "CONTEXT_LIMIT"); return; }
        std::string prompt(length, '\0');
        env->GetByteArrayRegion(input, 0, length, reinterpret_cast<jbyte *>(prompt.data()));
        if (env->ExceptionCheck()) return;
        const char * raw_path = env->GetStringUTFChars(path, nullptr);
        if (!raw_path) return;
        std::string model_path(raw_path);
        env->ReleaseStringUTFChars(path, raw_path);
        auto mp = llama_model_default_params();
        mp.n_gpu_layers = 0;
        mp.load_mode = LLAMA_LOAD_MODE_MMAP;
        mp.use_extra_bufts = false;
        mp.progress_callback = progress;
        mp.progress_callback_user_data = session.get();
        session->model = llama_model_load_from_file(model_path.c_str(), mp);
        if (!session->model || session->stopped()) { fail(env, "LOCAL_UNAVAILABLE"); return; }
        auto vocab = llama_model_get_vocab(session->model);
        int count = -llama_tokenize(vocab, prompt.data(), length, nullptr, 0, true, true);
        if (count <= 0 || count + 64 > 1024) { fail(env, "CONTEXT_LIMIT"); return; }
        std::vector<llama_token> tokens(count);
        if (llama_tokenize(vocab, prompt.data(), length, tokens.data(), count, true, true) != count) {
            fail(env, "CONTEXT_LIMIT"); return;
        }
        auto cp = llama_context_default_params();
        cp.n_ctx = 1024; cp.n_batch = 64; cp.n_ubatch = 64;
        cp.n_threads = 2; cp.n_threads_batch = 2;
        cp.abort_callback = abort_eval; cp.abort_callback_data = session.get();
        session->context = llama_init_from_model(session->model, cp);
        if (!session->context) { fail(env, "LOCAL_UNAVAILABLE"); return; }
        for (int offset = 0; offset < count; offset += 64) {
            auto batch = llama_batch_get_one(tokens.data() + offset, std::min(64, count - offset));
            if (session->stopped() || llama_decode(session->context, batch) != 0) {
                fail(env, "LOCAL_UNAVAILABLE"); return;
            }
        }
        session->sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
        llama_sampler_chain_add(session->sampler, llama_sampler_init_top_k(20));
        llama_sampler_chain_add(session->sampler, llama_sampler_init_top_p(0.8f, 1));
        llama_sampler_chain_add(session->sampler, llama_sampler_init_temp(0.7f));
        llama_sampler_chain_add(session->sampler, llama_sampler_init_dist(42));
    } catch (...) { fail(env, "LOCAL_UNAVAILABLE"); }
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_kinetic_data_model_LlamaNative_next(JNIEnv * env, jobject, jlong id) {
    try {
        auto session = find(id);
        if (!session || !session->sampler || session->stopped()) { fail(env, "LOCAL_UNAVAILABLE"); return nullptr; }
        if (session->generated >= 64) return nullptr;
        auto vocab = llama_model_get_vocab(session->model);
        auto token = llama_sampler_sample(session->sampler, session->context, -1);
        if (llama_vocab_is_eog(vocab, token)) { session->completed = true; return nullptr; }
        std::vector<char> piece(256);
        int size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        if (size < 0) {
            if (size < -32768) { fail(env, "LOCAL_UNAVAILABLE"); return nullptr; }
            piece.resize(-size);
            size = llama_token_to_piece(vocab, token, piece.data(), piece.size(), 0, false);
        }
        if (size < 0) { fail(env, "LOCAL_UNAVAILABLE"); return nullptr; }
        ++session->generated;
        if (session->generated < 64) {
            auto batch = llama_batch_get_one(&token, 1);
            if (session->stopped() || llama_decode(session->context, batch) != 0) {
                fail(env, "LOCAL_UNAVAILABLE"); return nullptr;
            }
        }
        auto bytes = env->NewByteArray(size);
        if (bytes) env->SetByteArrayRegion(bytes, 0, size, reinterpret_cast<jbyte *>(piece.data()));
        return bytes;
    } catch (...) { fail(env, "LOCAL_UNAVAILABLE"); return nullptr; }
}

extern "C" JNIEXPORT void JNICALL
Java_dev_kinetic_data_model_LlamaNative_cancel(JNIEnv *, jobject, jlong id) {
    if (auto session = find(id)) session->cancelled.store(true);
}
extern "C" JNIEXPORT void JNICALL
Java_dev_kinetic_data_model_LlamaNative_close(JNIEnv *, jobject, jlong id) {
    std::lock_guard<std::mutex> guard(registry_mutex);
    sessions.erase(id);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_kinetic_data_model_LlamaNative_completed(JNIEnv *, jobject, jlong id) {
    auto session = find(id);
    return session && session->completed ? JNI_TRUE : JNI_FALSE;
}
