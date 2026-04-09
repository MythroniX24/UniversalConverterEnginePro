cmake_minimum_required(VERSION 3.22.1)

project("universalconverter")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Optimization flags
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -ffast-math -Wall")
set(CMAKE_CXX_FLAGS_RELEASE "${CMAKE_CXX_FLAGS_RELEASE} -O3 -DNDEBUG")

# Source files
add_library(
    universalconverter
    SHARED
    converter_engine.cpp
    image_processor.cpp
    file_detector.cpp
    compression_engine.cpp
    thread_pool.cpp
)

# Find Android log library
find_library(log-lib log)
find_library(android-lib android)
find_library(jnigraphics-lib jnigraphics)

# Link libraries
target_link_libraries(
    universalconverter
    ${log-lib}
    ${android-lib}
    ${jnigraphics-lib}
    atomic
)

# Include directories
target_include_directories(universalconverter PRIVATE
    ${CMAKE_SOURCE_DIR}
)
