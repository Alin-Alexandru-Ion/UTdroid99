cmake_minimum_required(VERSION 3.22.1)
project(UT99GccProbe LANGUAGES C ASM)
set(runtime_dir "${ANDROID_NDK}/toolchains/llvm/prebuilt/windows-x86_64/lib64/clang/12.0.9/lib/linux")
set(renamed_builtins "${CMAKE_CURRENT_BINARY_DIR}/builtins-renamed.a")
set(renamed_unwind "${CMAKE_CURRENT_BINARY_DIR}/unwind-renamed.a")
add_custom_command(OUTPUT "${renamed_builtins}"
  COMMAND "${CMAKE_OBJCOPY}" "--redefine-syms=${CMAKE_CURRENT_SOURCE_DIR}/rename.txt" "${runtime_dir}/libclang_rt.builtins-aarch64-android.a" "${renamed_builtins}"
  DEPENDS "${runtime_dir}/libclang_rt.builtins-aarch64-android.a" "${CMAKE_CURRENT_SOURCE_DIR}/rename.txt" VERBATIM)
add_custom_command(OUTPUT "${renamed_unwind}"
  COMMAND "${CMAKE_OBJCOPY}" "--redefine-syms=${CMAKE_CURRENT_SOURCE_DIR}/rename.txt" "${runtime_dir}/aarch64/libunwind.a" "${renamed_unwind}"
  DEPENDS "${runtime_dir}/aarch64/libunwind.a" "${CMAKE_CURRENT_SOURCE_DIR}/rename.txt" VERBATIM)
add_custom_target(renamed_runtime DEPENDS "${renamed_builtins}" "${renamed_unwind}")
add_library(utgccbridge SHARED marker.c aliases.S)
add_dependencies(utgccbridge renamed_runtime)
set_target_properties(utgccbridge PROPERTIES OUTPUT_NAME "gcc_s" SUFFIX ".so.1")
target_link_options(utgccbridge PRIVATE "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/versions.map" "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
target_link_libraries(utgccbridge PRIVATE "-Wl,--whole-archive" "${renamed_builtins}" "${renamed_unwind}" "-Wl,--no-whole-archive" dl)
