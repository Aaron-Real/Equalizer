cmake_minimum_required(VERSION 3.22.1)
project("equalizer")

# Set the target output to shared (required for JNI)
add_library(equalizer SHARED equalizer.cpp)

# Include Superpowered headers
include_directories(${CMAKE_SOURCE_DIR}/Superpowered)

# Choose the appropriate .a file for current ABI
set(SUPERPOWERED_LIBRARY "")
if(CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    set(SUPERPOWERED_LIBRARY ${CMAKE_SOURCE_DIR}/Superpowered/libSuperpoweredAndroidarm64-v8a.a)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a")
    set(SUPERPOWERED_LIBRARY ${CMAKE_SOURCE_DIR}/Superpowered/libSuperpoweredAndroidarmeabi-v7a.a)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    set(SUPERPOWERED_LIBRARY ${CMAKE_SOURCE_DIR}/Superpowered/libSuperpoweredAndroidX86.a)
elseif(CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    set(SUPERPOWERED_LIBRARY ${CMAKE_SOURCE_DIR}/Superpowered/libSuperpoweredAndroidX86_64.a)
endif()

# Link everything
target_link_libraries(equalizer
        ${SUPERPOWERED_LIBRARY}
        log
        atomic
        m
)
