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
    android_host/np2sysp.c
    android_host/platform.c
    android_host/core_probe.c
    android_host/font_overlay.c
    android_host/machine.c
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
target_compile_definitions(np21w_core PRIVATE SUPPORT_LARGE_HDD SUPPORT_KAI_IMAGES NP2_SDL2 CPUCORE_IA32 USE_TSC SUPPORT_YMFM KAIRO98_ANDROID_IRQ_FAST KAIRO98_ANDROID_FETCH_FAST)
target_compile_options(np21w_core PRIVATE -fsigned-char -fno-strict-aliasing
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
target_compile_options(kairo98 PRIVATE $<$<CONFIG:Debug>:-O2>)
target_link_libraries(kairo98 PRIVATE log android aaudio)
# Core calls stay inside this shared library. Bind them locally to avoid the
# measured PLT cost in the interpreter's instruction-fetch path.
target_link_options(kairo98 PRIVATE "-Wl,-Bsymbolic")
