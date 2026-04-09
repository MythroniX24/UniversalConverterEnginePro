cmake_minimum_required(VERSION 3.18.1)

project("universalconverter")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Compiler flags — no -pthread on Android (bionic has it built-in)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -ffast-math -Wall -Wno-unused-variable")
set(CMAKE_CXX_FLAGS_RELEASE "-O3 -DNDEBUG")

# ── Source files ──────────────────────────────────────────────────────────────
add_library(
    universalconverter
    SHARED
    converter_engine.cpp
    image_processor.cpp
    file_detector.cpp
    compression_engine.cpp
    thread_pool.cpp
)

# ── System libraries — use underscores in variable names ─────────────────────
find_library(log_lib     log)
find_library(android_lib android)
find_library(jnigfx_lib  jnigraphics)

# ── Link — do NOT link atomic explicitly (c++_shared provides it) ─────────────
target_link_libraries(
    universalconverter
    ${log_lib}
    ${android_lib}
    ${jnigfx_lib}
)

# ── Include source directory ──────────────────────────────────────────────────
target_include_directories(universalconverter PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)
