# android-llm-app CMakeLists for JNI bridge to llama.cpp
# Pinned llama.cpp commit (see third_party/llama.cpp submodule)

cmake_minimum_required(VERSION 3.22.1)
project(llama_jni LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# ─── llama.cpp options (must come BEFORE add_subdirectory) ────────────
# These match the ADR-004 §5 decisions: arm64-only, no OpenMP/llamafile,
# minimal feature set for embedded use.
set(LLAMA_STATIC          OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)
set(GGML_OPENBLAS         OFF CACHE BOOL "" FORCE)
set(GGML_BLAS             OFF CACHE BOOL "" FORCE)

# ─── llama.cpp submodule ──────────────────────────────────────────────
# Repository path: third_party/llama.cpp/  (relative to android-llm-app/)
set(LLAMA_CPP_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/llama.cpp)

if(EXISTS ${LLAMA_CPP_DIR}/CMakeLists.txt)
    add_subdirectory(${LLAMA_CPP_DIR} llama_cpp_build EXCLUDE_FROM_ALL)
    set(LLAMA_CPP_AVAILABLE TRUE)
else()
    message(WARNING
        "llama.cpp submodule not found at ${LLAMA_CPP_DIR}.\n"
        "Run: git submodule update --init android-llm-app/third_party/llama.cpp\n"
        "Building llama_jni in STUB mode (loadModel returns 0).")
    set(LLAMA_CPP_AVAILABLE FALSE)
endif()

# ─── JNI bridge ───────────────────────────────────────────────────────
add_library(llama_jni SHARED
    llama_jni.cpp
)

target_include_directories(llama_jni PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
)

target_compile_definitions(llama_jni PRIVATE
    $<$<BOOL:${LLAMA_CPP_AVAILABLE}>:LLAMA_CPP_AVAILABLE=1>
)

if(LLAMA_CPP_AVAILABLE)
    target_link_libraries(llama_jni PRIVATE
        llama
        ggml
        log
        android
    )
else()
    target_link_libraries(llama_jni PRIVATE
        log
        android
    )
endif()

# Strip release builds
if(CMAKE_BUILD_TYPE STREQUAL "Release")
    target_link_options(llama_jni PRIVATE "-Wl,--gc-sections,--strip-all")
endif()
