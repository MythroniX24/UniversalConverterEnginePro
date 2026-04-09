#include <jni.h>
#include <android/log.h>
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <functional>
#include <vector>
#include <atomic>
#include <future>

#define LOG_TAG "UCEngine_ThreadPool"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// ─── Thread Pool ──────────────────────────────────────────────────────────────
class NativeThreadPool {
public:
    explicit NativeThreadPool(size_t numThreads) : stopped_(false) {
        for (size_t i = 0; i < numThreads; ++i) {
            workers_.emplace_back([this] {
                while (true) {
                    std::function<void()> task;
                    {
                        std::unique_lock<std::mutex> lock(mutex_);
                        condition_.wait(lock, [this] {
                            return stopped_ || !tasks_.empty();
                        });
                        if (stopped_ && tasks_.empty()) return;
                        task = std::move(tasks_.front());
                        tasks_.pop();
                    }
                    task();
                    ++completedTasks_;
                }
            });
        }
        LOGI("ThreadPool initialized with %zu threads", numThreads);
    }

    template<class F>
    auto enqueue(F&& f) -> std::future<decltype(f())> {
        using ReturnType = decltype(f());
        auto task = std::make_shared<std::packaged_task<ReturnType()>>(
            std::forward<F>(f));
        std::future<ReturnType> result = task->get_future();
        {
            std::unique_lock<std::mutex> lock(mutex_);
            if (stopped_) throw std::runtime_error("ThreadPool is stopped");
            tasks_.emplace([task] { (*task)(); });
            ++pendingTasks_;
        }
        condition_.notify_one();
        return result;
    }

    void waitAll() {
        while (pendingTasks_.load() > completedTasks_.load()) {
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
    }

    ~NativeThreadPool() {
        {
            std::unique_lock<std::mutex> lock(mutex_);
            stopped_ = true;
        }
        condition_.notify_all();
        for (auto& w : workers_) {
            if (w.joinable()) w.join();
        }
    }

    size_t pendingCount()   const { return pendingTasks_.load(); }
    size_t completedCount() const { return completedTasks_.load(); }

private:
    std::vector<std::thread>          workers_;
    std::queue<std::function<void()>> tasks_;
    std::mutex                        mutex_;
    std::condition_variable           condition_;
    bool                              stopped_;
    std::atomic<size_t>               pendingTasks_{0};
    std::atomic<size_t>               completedTasks_{0};
};

// Singleton pool using hardware concurrency
static NativeThreadPool& getPool() {
    static NativeThreadPool pool(
        std::max(2u, std::thread::hardware_concurrency()));
    return pool;
}

// ─── Compression Engine ───────────────────────────────────────────────────────
// Native RLE compression (for demonstration / text/binary data)
static std::vector<uint8_t> rleCompress(const std::vector<uint8_t>& input) {
    std::vector<uint8_t> output;
    output.reserve(input.size());
    size_t i = 0;
    while (i < input.size()) {
        uint8_t val = input[i];
        size_t count = 1;
        while (i + count < input.size() &&
               input[i + count] == val && count < 255) {
            ++count;
        }
        output.push_back(static_cast<uint8_t>(count));
        output.push_back(val);
        i += count;
    }
    return output;
}

static std::vector<uint8_t> rleDecompress(const std::vector<uint8_t>& input) {
    std::vector<uint8_t> output;
    for (size_t i = 0; i + 1 < input.size(); i += 2) {
        uint8_t count = input[i];
        uint8_t val   = input[i + 1];
        for (uint8_t j = 0; j < count; ++j) {
            output.push_back(val);
        }
    }
    return output;
}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_getAvailableThreads(
    JNIEnv* /* env */, jobject /* this */)
{
    return static_cast<jint>(std::thread::hardware_concurrency());
}

JNIEXPORT jbyteArray JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_compressDataNative(
    JNIEnv* env, jobject /* this */, jbyteArray inputData)
{
    jsize len = env->GetArrayLength(inputData);
    std::vector<uint8_t> input(len);
    env->GetByteArrayRegion(inputData, 0, len,
        reinterpret_cast<jbyte*>(input.data()));

    auto compressed = rleCompress(input);

    jbyteArray result = env->NewByteArray(static_cast<jsize>(compressed.size()));
    env->SetByteArrayRegion(result, 0,
        static_cast<jsize>(compressed.size()),
        reinterpret_cast<const jbyte*>(compressed.data()));
    return result;
}

JNIEXPORT jbyteArray JNICALL
Java_com_universalconverter_pro_engine_NativeEngine_decompressDataNative(
    JNIEnv* env, jobject /* this */, jbyteArray inputData)
{
    jsize len = env->GetArrayLength(inputData);
    std::vector<uint8_t> input(len);
    env->GetByteArrayRegion(inputData, 0, len,
        reinterpret_cast<jbyte*>(input.data()));

    auto decompressed = rleDecompress(input);

    jbyteArray result = env->NewByteArray(
        static_cast<jsize>(decompressed.size()));
    env->SetByteArrayRegion(result, 0,
        static_cast<jsize>(decompressed.size()),
        reinterpret_cast<const jbyte*>(decompressed.data()));
    return result;
}

} // extern "C"
