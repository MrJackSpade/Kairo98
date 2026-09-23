cmake_minimum_required(VERSION 3.22.1)
project(kairo98 LANGUAGES C CXX)

get_filename_component(NP21W_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/np21w" ABSOLUTE)
get_filename_component(SPLEEN_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/spleen" ABSOLUTE)
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
    ${NP21W_ROOT}/i286c
    ${NP21W_ROOT}/io
    ${NP21W_ROOT}/mem
    ${NP21W_ROOT}/network
    ${NP21W_ROOT}/sound
    ${NP21W_ROOT}/vram
    ${SPLEEN_ROOT}
)
target_compile_definitions(np21w_core PRIVATE SUPPORT_LARGE_HDD NP2_SDL2)
target_compile_options(np21w_core PRIVATE -fsigned-char -fno-strict-aliasing)

add_library(kairo98 SHARED native_bridge.cpp $<TARGET_OBJECTS:np21w_core>)
target_compile_features(kairo98 PRIVATE cxx_std_17)
target_link_libraries(kairo98 PRIVATE log android aaudio)
