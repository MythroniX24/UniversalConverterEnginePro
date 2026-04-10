// thread_pool.cpp
// All JNI bridges are in converter_engine.cpp
// This file contains the NativeThreadPool implementation only (no JNI exports)
#include <thread>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <functional>
#include <vector>
#include <atomic>
#include <android/log.h>

#define LOG_TAG "UCEngine_Pool"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// NativeThreadPool — used internally by converter operations
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

private:
    std::vector<std::thread>          workers_;
    std::queue<std::function<void()>> tasks_;
    std::mutex                        mutex_;
    std::condition_variable           condition_;
    bool                              stopped_;
    std::atomic<size_t>               completedTasks_{0};
};

// No JNI exports — all moved to converter_engine.cpp
