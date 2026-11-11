#!/usr/bin/env bash
# build_android.sh - builds the Android APK (packaging/android) on Linux; the twin of tools/build_android.ps1
# (agent L, 2026-11-11: same steps, same staged layout, Linux paths).
#
#   tools/build_android.sh [--release]
#
# Paths (environment, defaults for the maintainer's Arch machine; nothing of it is ever committed):
#   ORTA_ANDROID_DEPS  build-time tools, each with SOURCE.txt: jdk-21/unpacked/*, gradle-9.9.1/unpacked/*,
#                      sdk/ (platforms;android-36, build-tools;36.0.0, ndk;30.0.16248370, platform-tools),
#                      python-3.15.2-android/unpacked/prefix, capstone-5.0.9/unpacked/* (the Python binding),
#                      src/SDL3 (release-3.4.18), src/libadrenotools (8fae8ce + submodules)
#   ORTA_ANDROID_B     the toolkit's prebuilt Android parts (its tools/android scripts make them):
#                      device_toolchain/, sdk/, xr_glslang/, device_tests/
#   ORTA_ANDROID_SDK   the runtime SDK to ship (default $ORTA_ANDROID_B/sdk; a patched copy for a toolkit branch)
#   ORTA_CAPSTONE_SO   libcapstone.so for arm64 (tools/build_android_capstone.ps1 shows the CMake line)
#   ORTA_PRIVATE       the private output root (never the repo); the build goes to $ORTA_PRIVATE/android/build
#   XBOXRECOMP_DIR     the pinned toolkit checkout (option-c at the pin from docs/agent_l.md)
#   ORTA_RELEASE_NAME  the release stamp (tools/release.txt; a new one makes the device build the game again)
#   ORTA_RELEASE_KEY   --release only: the release keystore (alias "orta") and its password file beside it
#                      (<key>.pass); default $ORTA_ANDROID_DEPS/keys/orta-release.jks - never in a repo
# Gradle's caches and Android's debug key stay under ORTA_ANDROID_DEPS (GRADLE_USER_HOME, ANDROID_USER_HOME).
set -euo pipefail

task=assembleDebug
[ "${1:-}" = "--release" ] && task=assembleRelease

repo=$(cd "$(dirname "$0")/.." && pwd)
gaming=$(cd "$repo/../.." && pwd)
deps=${ORTA_ANDROID_DEPS:-$gaming/deps/android}
andb=${ORTA_ANDROID_B:-$gaming/agent_l/from_windows/android_b}
rsdk=${ORTA_ANDROID_SDK:-$andb/sdk}
csso=${ORTA_CAPSTONE_SO:-$gaming/agent_l/from_windows/capstone/libcapstone.so}
priv=${ORTA_PRIVATE:-$gaming/recomps/orta_private}
tk=${XBOXRECOMP_DIR:-$gaming/recomps/xboxrecomp-private}

die() { echo "build_android: $*" >&2; exit 1; }
first_dir() { local d; for d in "$1"/*/; do [ -d "$d" ] && { echo "${d%/}"; return; }; done; }
jdk=$(first_dir "$deps/jdk-21/unpacked")
gradle=$(first_dir "$deps/gradle-9.9.1/unpacked")
sdk=$deps/sdk
ndk=$sdk/ndk/30.0.16248370
py=$deps/python-3.15.2-android/unpacked/prefix
csbind=$(first_dir "$deps/capstone-5.0.9/unpacked")/bindings/python/capstone
sdl3=$deps/src/SDL3
adreno=$deps/src/libadrenotools
for p in "$jdk" "$gradle" "$ndk" "$sdk/platforms/android-36" "$py/lib/libpython3.15.so" "$csbind/__init__.py" \
         "$sdl3/android-project" "$adreno/CMakeLists.txt" "$tk/src/launcher/launcher.c"; do
    [ -n "$p" ] && [ -e "$p" ] || die "missing: ${p:-a tool folder under $deps}"
done
for f in compile_flags.txt link_line.txt sdk.txt; do
    [ -f "$rsdk/$f" ] || die "runtime SDK incomplete ($rsdk/$f)"
done
[ -f "$csso" ] || die "libcapstone.so (arm64) missing: $csso"

out=$priv/android/build
mkdir -p "$out"
export JAVA_HOME=$jdk ANDROID_HOME=$sdk ANDROID_SDK_ROOT=$sdk
export GRADLE_USER_HOME=$deps/gradle-home ANDROID_USER_HOME=$deps/user-home
echo "build_android: $task (NDK $(basename "$ndk"), JDK $(basename "$jdk"), Gradle $(basename "$gradle"))"

# prebuilt runtime libs (agent B): libxr_glslang.so + its device test, shipped as lib*.so -> nativeLibraryDir
pre=$out/prebuilt/arm64-v8a
mkdir -p "$pre"
if [ -f "$andb/xr_glslang/libxr_glslang.so" ]; then
    cp -f "$andb/xr_glslang/libxr_glslang.so" "$pre/libxr_glslang.so"
    [ -f "$andb/xr_glslang/xr_glslang_link_test" ] && cp -f "$andb/xr_glslang/xr_glslang_link_test" "$pre/libxr_glslang_test.so"
fi

# the embedded CPython (python.org's Android package): libpython as a jniLib, the stdlib + lifter as assets
cp -f "$py/lib/libpython3.15.so" "$pre/libpython3.15.so"
assets=$out/assets
pyA=$assets/python
rm -rf "$pyA"
std=$pyA/lib/python3.15
mkdir -p "$std"
skip=" test idlelib tkinter turtledemo ensurepip pydoc_data __phello__ lib2to3 venv sqlite3 curses dbm wsgiref xmlrpc unittest site-packages config-3.15-aarch64-linux-android "
for e in "$py/lib/python3.15"/*; do
    n=$(basename "$e")
    case "$skip" in *" $n "*) continue ;; esac
    cp -a "$e" "$std/"
done
find "$std" -type d -name __pycache__ -prune -exec rm -rf {} +
rm -f "$std"/lib-dynload/_test* "$std"/lib-dynload/xx*
sp=$std/site-packages
mkdir -p "$sp/tools"
for f in __init__.py gameconfig.py variant_prefix.py; do cp "$tk/tools/$f" "$sp/tools/$f"; done
for m in xbe_parser disasm func_id abi_analysis recomp ghidra_naming; do cp -a "$tk/tools/$m" "$sp/tools/$m"; done
find "$sp/tools" -type d \( -name __pycache__ -o -name tests \) -prune -exec rm -rf {} +
find "$sp/tools" -name 'test_*.py' -delete
cp -a "$tk/templates" "$sp/templates"
rm -rf "$sp/templates/game"          # the new-port template (README, CLAUDE.md, paths.ps1): not used by the lift
# capstone 5.0.9: the pure-Python binding from the same source as libcapstone.so
cp -a "$csbind" "$sp/capstone"
find "$sp/capstone" -type d -name __pycache__ -prune -exec rm -rf {} +
rm -rf "$sp/capstone/lib"; mkdir -p "$sp/capstone/lib"
cp "$csso" "$sp/capstone/lib/libcapstone.so"
# no debug info in what is shipped (it carries the build machine's paths); the dynamic symbols stay
"$ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-strip" --strip-debug "$sp/capstone/lib/libcapstone.so"
mkdir -p "$sp/orta_config"
cp -a "$repo/config/." "$sp/orta_config/"
echo "build_android: python assets $(du -sm "$pyA" | cut -f1) MB in $(find "$pyA" -type f | wc -l) files"

# the on-device compiler (agent B make_device_toolchain): clang + ld.lld as lib*_exe.so, sysroot + resource dir
dtc=$andb/device_toolchain
rm -rf "$assets/toolchain" "$assets/tools"
toolsA=$assets/tools
mkdir -p "$toolsA"
if [ -f "$dtc/bin/clang" ]; then
    cp -f "$dtc/bin/clang" "$pre/libclang_exe.so"
    cp -f "$dtc/bin/ld.lld" "$pre/liblld_exe.so"
    mkdir -p "$toolsA/toolchain"
    cp -a "$dtc/sysroot" "$dtc/lib" "$dtc/LICENSES" "$toolsA/toolchain/"
    probe=$priv/android/toolchain_probe
    rm -rf "$assets/probe"; mkdir -p "$assets/probe"
    [ -d "$probe" ] && find "$probe" -maxdepth 1 \( -name '*.c' -o -name '*.args' \) -exec cp {} "$assets/probe/" \;
    echo "build_android: toolchain assets $(du -sm "$toolsA/toolchain" | cut -f1) MB"
fi

# the launcher's read-only tools folder (assets/tools -> <files>/tools; XR_LAUNCHER_TOOLS, set by loader.c)
sdkB=$rsdk
echo "build_android: runtime SDK $sdkB ($(sed -n 's/^toolkit: //p' "$sdkB/sdk.txt"))"
lifA=$toolsA/lifter
mkdir -p "$lifA/tools"
mv "$sp/tools/"* "$lifA/tools/"; rmdir "$sp/tools"
mv "$sp/templates" "$lifA/templates"
printf '../../../../tools/lifter\n' > "$sp/xr_lifter.pth"
cp -a "$repo/config" "$toolsA/config"
portSrc="main.c recomp_manual.c input.c settings.c vk_check.c platform/host_linux.c"
mkdir -p "$toolsA/src/platform"
for f in $portSrc input.h settings.h vk_check.h platform/host.h; do cp "$repo/src/$f" "$toolsA/src/$f"; done
sdkA=$toolsA/sdk
mkdir -p "$sdkA/build" "$sdkA/extra/xbox" "$sdkA/lib"
cp -a "$sdkB/include" "$sdkA/include"
cp -a "$sdkB/lib/." "$sdkA/lib/"
cp "$sdkB/sdk.txt" "$sdkA/sdk.txt"
cp "$tk/include/xbox/xboxrecomp.h" "$sdkA/extra/xbox/xboxrecomp.h"
{ printf '/* generated from assets/icon_64.bmp (build_android.sh) */\nstatic const unsigned char g_port_icon_bmp[] = {'
  od -An -v -tx1 "$repo/assets/icon_64.bmp" | tr -s ' \n' ' ' | sed 's/^ //; s/ $//; s/\([0-9a-f][0-9a-f]\)/0x\1,/g; s/ //g'
  printf '};\n'; } > "$sdkA/build/port_icon.h"

# descriptors: build.txt (compile + link from B's SDK lines), lift.txt, toolchain.txt, release.txt
sysr='{TOOLS}/toolchain/sysroot'
drv=(--driver-mode=gcc --target=aarch64-linux-android29 "--sysroot=$sysr" '-resource-dir={TOOLS}/toolchain/lib/clang/23')
subst() { sed -e 's|\$SDK|{TOOLS}/sdk|g' -e "s|\\\$SYSROOT|$sysr|g" "$1" | tr -s ' \t\r\n' '\n' | sed '/^$/d'; }
mapfile -t sdkFlags < <(subst "$sdkB/compile_flags.txt" | grep -v -e '^--target=' -e '^--sysroot=')
# the boot animation (media/boot.mpg) when the SDK has the toolkit's src/bootvideo (libxbox_bootvideo.a)
boot=()
grep -q 'libxbox_bootvideo.a' "$sdkB/link_line.txt" && boot=(-DORTA_BOOTVIDEO=1 '-I{TOOLS}/sdk/include/toolkit/bootvideo')
cc=("${drv[@]}" -O2 -std=gnu11 "${sdkFlags[@]}" '-I{TOOLS}/src' '-I{GEN}' '-I{TOOLS}/sdk/build' '-I{TOOLS}/sdk/extra'
    -DORTA_VULKAN=1 -D_GNU_SOURCE "${boot[@]}" -w)
# the game's Vulkan = libvulkan_orta.so (system driver or Turnip, packaging/android/app/jni/vkshim), never -lvulkan
mapfile -t lline < <(subst "$sdkB/link_line.txt" | sed 's|^-lvulkan$|{NATIVE}/libvulkan_orta.so|')
link=("${drv[@]}" -shared '--ld-path={NATIVE}/libld_exe.so' -Wl,--no-undefined -Wl,-z,max-page-size=16384
      -Wl,--export-dynamic -Wl,-soname,libORTA_game.so "${lline[@]}" -llog)
join_tab() { local IFS=$'\t'; echo "$*"; }
{
    echo "# compile + link (build_android.sh, from agent B's runtime SDK lines): cc = the common arguments, src / gen = the units, link"
    join_tab cc "${cc[@]}"
    for f in $portSrc; do printf 'src\t{TOOLS}/src/%s\n' "$f"; done
    printf 'gen\t*.c\n'
    join_tab link "${link[@]}"
} > "$toolsA/build.txt"
cfgT='{TOOLS}/config/game.toml'
{
    echo "# the lift (the same steps as tools/lift.ps1): name, then python's arguments; {XBE} {PIPE} {GEN} {TOOLS}"
    join_tab mkdir '{PIPE}/disasm' '{PIPE}/func_id' '{PIPE}/abi' '{PIPE}/recomp' '{GEN}'
    join_tab xbe_parser -m tools.xbe_parser '{XBE}' --json '{PIPE}/xbe.json' --quiet
    join_tab disasm -m tools.disasm '{XBE}' -o '{PIPE}/disasm' --analysis-json '{PIPE}/xbe.json' --force --game-config "$cfgT"
    join_tab func_id -m tools.func_id '{XBE}' --functions '{PIPE}/disasm/functions.json' --strings '{PIPE}/disasm/strings.json' --xrefs '{PIPE}/disasm/xrefs.json' -o '{PIPE}/func_id'
    join_tab abi_analysis -m tools.abi_analysis '{XBE}' --disasm-dir '{PIPE}/disasm' --func-id-dir '{PIPE}/func_id' --output-dir '{PIPE}/abi'
    join_tab recomp -m tools.recomp '{XBE}' --all --game-config "$cfgT" --game-name ORTA --gen-dir '{GEN}' --disasm-dir '{PIPE}/disasm' --func-id-dir '{PIPE}/func_id' --abi-dir '{PIPE}/abi' -o '{PIPE}/recomp'
} > "$toolsA/lift.txt"
printf 'format\t2\nclang\t{NATIVE}/libclang_exe.so\npython\t{NATIVE}/libpython_exe.so\n' > "$toolsA/toolchain.txt"
rel=${ORTA_RELEASE_NAME:-android-dev-$(git -C "$repo" rev-parse --short HEAD)}   # a public APK: ORTA_RELEASE_NAME=<version>
echo "$rel" > "$toolsA/release.txt"
# agent B's device tests (MMIO decode / trap, bionic compat, stack dump)
for t in libmmio_decode_a64_exe.so libmmio_trap_exe.so libcompat_bionic_exe.so libhost_stack_dump_exe.so; do
    [ -f "$andb/device_tests/$t" ] && cp -f "$andb/device_tests/$t" "$pre/$t"
done
# licence texts of everything the APK ships (assets/licenses, shown by BACK menu > About and licences; maintainer
# 2026-11-11: needed before any public APK). A Turnip driver is never shipped, so Mesa is not in the list.
licA=$assets/licenses
rm -rf "$licA"; mkdir -p "$licA"
lic() { [ -f "$2" ] || die "licence text missing: $2"; cp "$2" "$licA/$1"; }
lic Orta-Recompiled_GPL-3.0.txt "$repo/LICENSE"
lic xboxrecomp_MIT.txt "$tk/LICENSE"
lic xboxrecomp_NOTICE.txt "$tk/NOTICE"
lic xemu-parts_LGPL-2.1.txt "$tk/LICENSES/LGPL-2.1.txt"
lic SMAA_MIT.txt "$tk/src/nv2a_vk/third_party/smaa/LICENSE.txt"
lic miniz_MIT.txt "$tk/src/shaderpack/third_party/miniz/LICENSE"
lic pl_mpeg_MIT.txt "$tk/src/bootvideo/third_party/pl_mpeg/LICENSE"
lic SDL3_zlib.txt "$sdl3/LICENSE.txt"
lic CPython_PSF.txt "$py/lib/python3.15/LICENSE.txt"
lic capstone_BSD-3.txt "$(dirname "$csbind")/../../LICENSE.TXT"
lic capstone_LLVM-parts.txt "$(dirname "$csbind")/../../LICENSE_LLVM.TXT"
lic libadrenotools_BSD-2.txt "$adreno/LICENSE"
lic linkernsbypass.txt "$adreno/lib/linkernsbypass/LICENSE"
lic glslang.txt "$deps/src/glslang-LICENSE.txt"
for f in "$dtc"/LICENSES/*; do [ -f "$f" ] && lic "toolchain_$(basename "$f")" "$f"; done
cat > "$licA/README.txt" <<'LICEOF'
Orta Recompiled - Android: the licences of everything inside this app

Orta Recompiled (this app's own code)   GPL-3.0-or-later   github.com/almost-drowned-rat/Orta-Recompiled-Android
xboxrecomp toolkit (option-c)           MIT, parts from xemu under LGPL-2.1-or-later (NOTICE lists them)
                                        github.com/almost-drowned-rat/xboxrecomp-option-c
  in it: SMAA (MIT), miniz (MIT), pl_mpeg (MIT, the boot animation)
SDL3                                    zlib               github.com/libsdl-org/SDL
CPython 3.15 (embedded, for the lift)   PSF License        python.org
capstone 5.0.9                          BSD-3-Clause (+ LLVM-derived parts)   github.com/capstone-engine/capstone
libadrenotools + linkernsbypass         BSD-2-Clause       github.com/bylaws/libadrenotools
glslang                                 BSD / MIT / Apache-2.0 (see glslang.txt)   github.com/KhronosGroup/glslang
LLVM clang + lld (the on-device compiler)  Apache-2.0 WITH LLVM-exception   github.com/llvm/llvm-project
Android NDK sysroot pieces              see toolchain_NDK-NOTICE.txt

No game data, no game code and no keys are part of this app: the game is built on the device from the player's own
disc. A Turnip driver (Mesa, MIT) is never shipped - the player chooses one.
Panzer Dragoon Orta is a trademark of SEGA; this project is not affiliated with SEGA or Microsoft.
LICEOF

# Turnip is never shipped (maintainer 2026-11-11): the player picks a driver package when the device needs one
rm -rf "$assets/turnip"
echo "build_android: tools assets $(du -sm "$toolsA" | cut -f1) MB in $(find "$toolsA" -type f | wc -l) files ($rel)"

sign=()
if [ "$task" = assembleRelease ]; then
    key=${ORTA_RELEASE_KEY:-$deps/keys/orta-release.jks}
    [ -f "$key" ] && [ -f "${key%.jks}.pass" ] || die "release key or its password file missing: $key"
    sign=("-PreleaseStore=$key" "-PreleasePassFile=${key%.jks}.pass")
fi
"$gradle/bin/gradle" -p "$repo/packaging/android" --no-daemon --console=plain --project-cache-dir "$out/gradle-cache" \
    "-Psdl3Src=$sdl3" "-PndkPath=$ndk" "-PoutDir=$out" "-PprebuiltLibs=$out/prebuilt" "-PassetsDir=$assets" \
    "-PpythonPrefix=$py" "-Ptoolkit=$tk" "-PportIconDir=$sdkA/build" "-PadrenotoolsSrc=$adreno" "${sign[@]}" "$task"
apk=$(find "$out" -name '*.apk' -printf '%T@ %p\n' | sort -n | tail -1 | cut -d' ' -f2-)
[ -n "$apk" ] || die "no APK found"
echo "APK: $apk ($(du -m "$apk" | cut -f1) MB)"
