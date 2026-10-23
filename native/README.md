# native/ (fase 2)
Aqui entra o CMake do NDK que gera `libmain.so` a partir de `engine/` (runtime + cemu_gpu + guest + ih264d),
usando SDL3 para Android. Os `generated/*.cpp` sao produzidos localmente pelo `scripts/build_apk.sh`
a partir do dump do proprio usuario e **nunca** sao versionados (veja .gitignore).
