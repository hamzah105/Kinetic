cmake_minimum_required(VERSION 3.30)
project(kinetic_local LANGUAGES C CXX)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_JOB_POOLS kinetic_compile=2)
set(CMAKE_JOB_POOL_COMPILE kinetic_compile)
set(CMAKE_JOB_POOL_LINK kinetic_compile)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
foreach(flag GGML_NATIVE GGML_OPENMP GGML_LLAMAFILE GGML_CPU_KLEIDIAI GGML_CPU_REPACK
    GGML_VULKAN GGML_OPENCL GGML_RPC GGML_BACKEND_DL LLAMA_OPENSSL LLAMA_SUBPROCESS
    LLAMA_LLGUIDANCE LLAMA_BUILD_COMMON LLAMA_BUILD_TESTS LLAMA_BUILD_TOOLS
    LLAMA_BUILD_EXAMPLES LLAMA_BUILD_SERVER LLAMA_BUILD_APP LLAMA_BUILD_MTMD)
    set(${flag} OFF CACHE BOOL "" FORCE)
endforeach()
# Build-time pinned source only. No runtime download or backend/plugin discovery.
include(FetchContent)
FetchContent_Declare(llama
    URL "${CMAKE_CURRENT_SOURCE_DIR}/../../../../../third_party/llama-5266f24.zip"
    URL_HASH SHA256=CF07FFF2E0F5859E633A137466BDEE72B06D75C00B72EE591FF922EAD864EC68
    DOWNLOAD_EXTRACT_TIMESTAMP TRUE)
FetchContent_MakeAvailable(llama)
add_library(kinetic_local SHARED kinetic_local.cpp)
target_link_libraries(kinetic_local PRIVATE llama)
target_link_options(kinetic_local PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--exclude-libs,ALL")
