cmake_minimum_required(VERSION 3.18.1)
project("universalconverter")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Suppress ALL warnings that could become errors
# -Wno-unused-function, -Wno-unused-variable: suppress unused code warnings  
# -Wno-unused-private-field: suppress unused class member warnings
add_compile_options(
    -Wno-unused-function
    -Wno-unused-variable
    -Wno-unused-private-field
    -Wno-unused-parameter
)

add_library(
    universalconverter
    SHARED
    converter_engine.cpp
    image_processor.cpp
    file_detector.cpp
    compression_engine.cpp
    thread_pool.cpp
)

find_library(log_lib     log)
find_library(android_lib android)
find_library(jnigfx_lib  jnigraphics)

target_link_libraries(
    universalconverter
    ${log_lib}
    ${android_lib}
    ${jnigfx_lib}
)

target_include_directories(universalconverter PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)
