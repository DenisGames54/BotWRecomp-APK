# Android (arm64-v8a) build of the BotWRecomp runtime -> libmain.so (+ libSDL3.so).
# Driven by scripts/build_native.sh (NDK toolchain). Mirrors engine/linux/CMakeLists.txt, with:
#   SDL3 / fmt / zstd / glslang built from source (scripts/fetch_deps.sh), OpenSSL replaced by a tiny
#   shim, no X11/Wayland, and the guest code taken from BOTW_GENERATED (produced from YOUR dump).
cmake_minimum_required(VERSION 3.24)
project(BotWRecompAndroid LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)
if(NOT CMAKE_BUILD_TYPE)
  set(CMAKE_BUILD_TYPE Release)
endif()

get_filename_component(APKROOT "${CMAKE_CURRENT_SOURCE_DIR}/.." ABSOLUTE)
set(ROOT "${APKROOT}/engine" CACHE PATH "BotWRecomp-Android engine checkout (submodule)")
set(BOTW_DEPS "${APKROOT}/deps" CACHE PATH "third-party sources (scripts/fetch_deps.sh)")
set(BOTW_GENERATED "${APKROOT}/work/generated" CACHE PATH "recompiled guest code (from YOUR dump)")
set(BOTW_JNILIBS "${APKROOT}/app/src/main/jniLibs/arm64-v8a" CACHE PATH "where libmain.so / libSDL3.so are copied")
set(BOTW_CPU "cortex-a710" CACHE STRING "-mcpu for generated code")
set(BOTW_PGO "" CACHE STRING "PGO mode: gen | use")
set(BOTW_PGO_DATA "" CACHE FILEPATH "merged .profdata (BOTW_PGO=use)")

set(PGO_COMPILE "")
set(PGO_LINK "")
if(BOTW_PGO STREQUAL "gen")
  set(PGO_COMPILE -fprofile-generate -DBOTW_PGO_GEN=1)
  set(PGO_LINK -fprofile-generate)
elseif(BOTW_PGO STREQUAL "use")
  set(PGO_COMPILE -fprofile-use=${BOTW_PGO_DATA} -ffunction-sections -Wno-profile-instr-unprofiled -Wno-profile-instr-out-of-date -Wno-backend-plugin)
endif()

# ---------------------------------------------------------------- third party
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_TEST_LIBRARY OFF CACHE BOOL "" FORCE)
set(SDL_TESTS OFF CACHE BOOL "" FORCE)
set(SDL_EXAMPLES OFF CACHE BOOL "" FORCE)
add_subdirectory(${BOTW_DEPS}/SDL3-3.4.18 SDL3)

set(FMT_TEST OFF CACHE BOOL "" FORCE)
set(FMT_DOC OFF CACHE BOOL "" FORCE)
set(FMT_INSTALL OFF CACHE BOOL "" FORCE)
add_subdirectory(${BOTW_DEPS}/fmt-12.2.0 fmt EXCLUDE_FROM_ALL)

set(ZSTD_BUILD_PROGRAMS OFF CACHE BOOL "" FORCE)
set(ZSTD_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(ZSTD_BUILD_SHARED OFF CACHE BOOL "" FORCE)
set(ZSTD_BUILD_STATIC ON CACHE BOOL "" FORCE)
add_subdirectory(${BOTW_DEPS}/zstd-1.5.7/build/cmake zstd EXCLUDE_FROM_ALL)

# glslang without SPIRV-Tools (the Cemu code only uses glslang + GlslangToSpv)
set(ENABLE_OPT OFF CACHE BOOL "" FORCE)
set(ENABLE_HLSL OFF CACHE BOOL "" FORCE)
set(ENABLE_GLSLANG_BINARIES OFF CACHE BOOL "" FORCE)
set(ENABLE_CTEST OFF CACHE BOOL "" FORCE)
set(BUILD_EXTERNAL OFF CACHE BOOL "" FORCE)
set(GLSLANG_TESTS OFF CACHE BOOL "" FORCE)
add_subdirectory(${BOTW_DEPS}/glslang glslang EXCLUDE_FROM_ALL)
# Cemu includes <glslang/SPIRV/GlslangToSpv.h> (installed layout); in the source tree it is <SPIRV/...>
set(GLSLANG_INC ${CMAKE_BINARY_DIR}/glslang_inc)
file(MAKE_DIRECTORY ${GLSLANG_INC}/glslang)
file(CREATE_LINK ${BOTW_DEPS}/glslang/SPIRV ${GLSLANG_INC}/glslang/SPIRV SYMBOLIC)

# ---------------------------------------------------------------- guest code (your dump)
file(GLOB GUEST_SOURCES CONFIGURE_DEPENDS ${BOTW_GENERATED}/fn_*.cpp ${BOTW_GENERATED}/big_*.cpp
  ${BOTW_GENERATED}/huge_*.cpp ${BOTW_GENERATED}/table.cpp ${BOTW_GENERATED}/hle_stubs.cpp)
file(GLOB BIG_SOURCES CONFIGURE_DEPENDS ${BOTW_GENERATED}/big_*.cpp)
file(GLOB HUGE_SOURCES CONFIGURE_DEPENDS ${BOTW_GENERATED}/huge_*.cpp)
if(NOT GUEST_SOURCES)
  message(FATAL_ERROR "No generated guest code in ${BOTW_GENERATED}. Run scripts/recompile_dump.sh first.")
endif()
add_library(guest OBJECT ${GUEST_SOURCES})
target_include_directories(guest PRIVATE ${ROOT}/runtime/include ${BOTW_GENERATED})
target_compile_options(guest PRIVATE -O2 -w -fwrapv -fno-strict-aliasing -ffp-contract=off -mcpu=${BOTW_CPU} ${PGO_COMPILE})
set_source_files_properties(${BIG_SOURCES} PROPERTIES COMPILE_OPTIONS "-O1")
set_source_files_properties(${HUGE_SOURCES} PROPERTIES COMPILE_OPTIONS "-O0")
set_source_files_properties(${BOTW_GENERATED}/table.cpp PROPERTIES COMPILE_OPTIONS "-O0")

# ---------------------------------------------------------------- vendored Cemu Latte layer (Vulkan)
file(GLOB_RECURSE CEMU_SOURCES CONFIGURE_DEPENDS
  ${ROOT}/runtime/cemu/Cafe/*.cpp ${ROOT}/runtime/cemu/Common/*.cpp ${ROOT}/runtime/cemu/util/*.cpp
  ${ROOT}/runtime/cemu/Cemu/FileCache/*.cpp ${ROOT}/runtime/cemu/imgui/*.cpp
  ${ROOT}/runtime/cemu_shims/*.cpp)
list(FILTER CEMU_SOURCES EXCLUDE REGEX "LatteOverlay\\.cpp$|/dependencies/|SystemInfo(Win|Mac|BSD)\\.cpp$|/Renderer/Metal/|MetalCppImpl|imgui_impl_metal|LatteDecompilerEmitMSL")
foreach(f imgui imgui_draw imgui_tables imgui_widgets)
  list(APPEND CEMU_SOURCES ${ROOT}/runtime/cemu/dependencies/imgui/${f}.cpp)
endforeach()
list(APPEND CEMU_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/compat/openssl_shim.cpp)
add_library(cemu_gpu OBJECT ${CEMU_SOURCES})
target_include_directories(cemu_gpu PRIVATE
  ${CMAKE_CURRENT_SOURCE_DIR}/compat ${ROOT}/linux/compat   # our headers first (vk_platform_compat.h, execinfo.h, openssl/)
  ${ROOT}/runtime/cemu_shims ${ROOT}/runtime/cemu ${ROOT}/runtime/cemu/Cafe
  ${ROOT}/runtime/cemu/dependencies/imgui ${ROOT}/runtime/cemu/dependencies
  ${GLSLANG_INC} ${BOTW_DEPS}/glslang ${BOTW_DEPS}/boost_1_83_0 ${BOTW_DEPS}/glm)
target_compile_definitions(cemu_gpu PRIVATE BOOST_NOWIDE_NO_LIB ENABLE_VULKAN=1 VK_NO_PROTOTYPES BOTWRECOMP
  VK_USE_PLATFORM_ANDROID_KHR
  EMULATOR_VERSION_MAJOR=2 EMULATOR_VERSION_MINOR=6 EMULATOR_VERSION_PATCH=0 EMULATOR_HASH=a53cf0e)
target_compile_options(cemu_gpu PRIVATE -w -O2 -flax-vector-conversions -mcpu=${BOTW_CPU}
  "SHELL:-include vk_platform_compat.h" "SHELL:-include Common/precompiled.h")
target_link_libraries(cemu_gpu PRIVATE SDL3::SDL3 fmt::fmt)

# ---------------------------------------------------------------- runtime
file(GLOB_RECURSE RUNTIME_SOURCES CONFIGURE_DEPENDS ${ROOT}/runtime/src/*.cpp)
list(FILTER RUNTIME_SOURCES EXCLUDE REGEX "/gpu_native/")
list(APPEND RUNTIME_SOURCES ${CMAKE_CURRENT_SOURCE_DIR}/src/android_main.cpp)
set_source_files_properties(${ROOT}/runtime/src/main.cpp PROPERTIES COMPILE_DEFINITIONS "main=botw_main")

add_subdirectory(${ROOT}/runtime/third_party/ih264d ${CMAKE_BINARY_DIR}/ih264d EXCLUDE_FROM_ALL)
add_library(runtime_obj OBJECT ${RUNTIME_SOURCES})
target_include_directories(runtime_obj PRIVATE ${CMAKE_CURRENT_SOURCE_DIR}/compat ${ROOT}/linux/compat
  ${ROOT}/runtime/include ${ROOT}/runtime/src ${BOTW_GENERATED}
  ${ROOT}/runtime/third_party/ih264d ${ROOT}/runtime/src/h264)
target_compile_options(runtime_obj PRIVATE -Wall -Wno-unused-function -Wno-unused-variable -fwrapv -flax-vector-conversions
  -mcpu=${BOTW_CPU} ${PGO_COMPILE})
target_link_libraries(runtime_obj PRIVATE SDL3::SDL3 fmt::fmt)

# ---------------------------------------------------------------- libmain.so
add_library(main SHARED $<TARGET_OBJECTS:runtime_obj> $<TARGET_OBJECTS:guest> $<TARGET_OBJECTS:cemu_gpu>)
set(GLSLANG_LIBS glslang SPIRV)
if(TARGET glslang-default-resource-limits)
  list(APPEND GLSLANG_LIBS glslang-default-resource-limits)
endif()
target_link_libraries(main PRIVATE SDL3::SDL3 fmt::fmt libzstd_static ${GLSLANG_LIBS} ih264d android log z dl)
target_link_options(main PRIVATE -Wl,-z,max-page-size=16384 -Wl,--gc-sections ${PGO_LINK})

add_custom_command(TARGET main POST_BUILD
  COMMAND ${CMAKE_COMMAND} -E make_directory ${BOTW_JNILIBS}
  COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:main> ${BOTW_JNILIBS}/libmain.so
  COMMAND ${CMAKE_COMMAND} -E copy $<TARGET_FILE:SDL3::SDL3> ${BOTW_JNILIBS}/libSDL3.so)
