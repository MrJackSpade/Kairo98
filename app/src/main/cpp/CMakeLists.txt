cmake_minimum_required(VERSION 3.22.1)
project(kairo98 LANGUAGES C CXX)

get_filename_component(NP21W_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/np21w" ABSOLUTE)
get_filename_component(SPLEEN_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/spleen" ABSOLUTE)
get_filename_component(YMFM_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/ymfm" ABSOLUTE)
include(np21w-sources.cmake)

add_library(np21w_core OBJECT
    ${NP21W_CORE_SOURCES}
    ${NP21W_ROOT}/sdl2/dosio.c
    ${NP21W_ROOT}/sdl2/timemng.c
    ${NP21W_ROOT}/sdl2/fontmng.c
    ${NP21W_ROOT}/cbus/boardmo.c
    ${NP21W_ROOT}/lio/gpaint.c
    ${NP21W_ROOT}/lio/groll.c
    ${NP21W_ROOT}/statsave.c
    android_host/np2sysp.c
    android_host/platform.c
    android_host/core_probe.c
    android_host/font_overlay.c
    android_host/machine.c
    android_host/gpudraw.c
    android_host/input_telemetry.c
    android_host/mousemng.c
    android_host/joymng.c
)
target_include_directories(np21w_core PRIVATE
    android_host
    ${NP21W_ROOT}
    ${NP21W_ROOT}/sdl2
    ${NP21W_ROOT}/common
    ${NP21W_ROOT}/cbus
    ${NP21W_ROOT}/generic
    ${NP21W_ROOT}/font
    ${NP21W_ROOT}/i386c
    ${NP21W_ROOT}/io
    ${NP21W_ROOT}/mem
    ${NP21W_ROOT}/network
    ${NP21W_ROOT}/sound
    ${NP21W_ROOT}/vram
    ${SPLEEN_ROOT}
)
target_compile_definitions(np21w_core PRIVATE SUPPORT_LARGE_HDD SUPPORT_KAI_IMAGES NP2_SDL2 CPUCORE_IA32 USE_TSC SUPPORT_YMFM KAIRO98_ANDROID_IRQ_FAST KAIRO98_ANDROID_FETCH_FAST
    USE_CPU_EIPMASK USE_CPU_DIRECTREG)
# Thin LTO lets the interpreter's memory, paging, and dispatch helpers inline
# across files; without it every guest memory access is a chain of calls.
target_compile_options(np21w_core PRIVATE -fsigned-char -fno-strict-aliasing -flto=thin
    $<$<CONFIG:Debug>:-O2>)

add_library(kairo98 SHARED
    native_bridge.cpp
    android_host/ymfm_bridge.cpp
    ${YMFM_ROOT}/src/ymfm_opn.cpp
    ${YMFM_ROOT}/src/ymfm_adpcm.cpp
    ${YMFM_ROOT}/src/ymfm_ssg.cpp
    $<TARGET_OBJECTS:np21w_core>)
target_include_directories(kairo98 PRIVATE ${YMFM_ROOT}/src)
target_compile_features(kairo98 PRIVATE cxx_std_17)
target_compile_options(kairo98 PRIVATE -flto=thin $<$<CONFIG:Debug>:-O2>)
target_link_libraries(kairo98 PRIVATE log android aaudio EGL GLESv3)
# Core calls stay inside this shared library. Bind them locally to avoid the
# measured PLT cost in the interpreter's instruction-fetch path.
target_link_options(kairo98 PRIVATE "-Wl,-Bsymbolic" -flto=thin)

# Profile-guided optimization. "generate" builds an instrumented library that
# writes an LLVM profile into the app's firmware directory while a game runs;
# "use" consumes a merged .profdata to lay out the interpreter's hot paths.
# Both modes change host code layout only, never guest behavior or timing.
set(KAIRO98_PGO_MODE "" CACHE STRING "Empty, generate, or use")
set(KAIRO98_PGO_PROFILE "" CACHE FILEPATH "Merged .profdata for KAIRO98_PGO_MODE=use")
if(KAIRO98_PGO_MODE STREQUAL "generate")
    set(KAIRO98_PGO_FLAGS -fprofile-generate)
    target_compile_definitions(kairo98 PRIVATE KAIRO98_PGO_GENERATE)
elseif(KAIRO98_PGO_MODE STREQUAL "use")
    if(NOT EXISTS "${KAIRO98_PGO_PROFILE}")
        message(FATAL_ERROR "KAIRO98_PGO_PROFILE must name an existing .profdata file")
    endif()
    set(KAIRO98_PGO_FLAGS "-fprofile-use=${KAIRO98_PGO_PROFILE}"
        -Wno-profile-instr-out-of-date -Wno-profile-instr-unprofiled
        -Wno-backend-plugin)
endif()
# Diagnostic build: run the EGC read fast path and the original shifter side by
# side and count any state divergence on the debug status line.
option(KAIRO98_EGC_VERIFY "Cross-check the EGC read fast path against the original" OFF)
if(KAIRO98_EGC_VERIFY)
    target_compile_definitions(np21w_core PRIVATE KAIRO98_EGC_VERIFY)
endif()
# Diagnostic build: draw on the CPU as well and compare the GPU composition
# against it pixel by pixel, logging the count.
option(KAIRO98_GPU_VERIFY "Cross-check the GPU screen path against the CPU conversion" OFF)
if(KAIRO98_GPU_VERIFY)
    target_compile_definitions(np21w_core PRIVATE KAIRO98_GPU_VERIFY)
    target_compile_definitions(kairo98 PRIVATE KAIRO98_GPU_VERIFY)
endif()
# Diagnostic build: run a second FM synthesizer synchronously on the emulation
# thread and compare the worker thread's output with it sample by sample.
option(KAIRO98_SYNTH_VERIFY "Cross-check the threaded FM synthesizer against a synchronous one" OFF)
if(KAIRO98_SYNTH_VERIFY)
    target_compile_definitions(kairo98 PRIVATE KAIRO98_SYNTH_VERIFY)
endif()
if(KAIRO98_PGO_FLAGS)
    target_compile_options(np21w_core PRIVATE ${KAIRO98_PGO_FLAGS})
    target_compile_options(kairo98 PRIVATE ${KAIRO98_PGO_FLAGS})
    target_link_options(kairo98 PRIVATE ${KAIRO98_PGO_FLAGS})
endif()
