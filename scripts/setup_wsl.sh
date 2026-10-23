#!/usr/bin/env bash
# Prepares JDK 17, Android SDK, NDK and Gradle on WSL2 (Ubuntu/Debian x86-64). Run once.
set -euo pipefail
SDK="$HOME/android-sdk"; GRADLE_V=8.9
sudo apt update
sudo apt install -y openjdk-17-jdk unzip wget curl git cmake ninja-build python3 build-essential

mkdir -p "$SDK/cmdline-tools"
if [ ! -d "$SDK/cmdline-tools/latest" ]; then
  wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip
  unzip -q /tmp/cmdtools.zip -d "$SDK/cmdline-tools" && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
export ANDROID_HOME="$SDK"
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" "platform-tools" "platforms;android-35" "build-tools;35.0.0" "ndk;27.2.12479018"

if [ ! -d "$HOME/gradle-$GRADLE_V" ]; then
  wget -q https://services.gradle.org/distributions/gradle-$GRADLE_V-bin.zip -O /tmp/gradle.zip
  unzip -q /tmp/gradle.zip -d "$HOME"
fi

cat > "$(dirname "$0")/../.botw-apk-env" <<ENV
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=$SDK
export ANDROID_NDK_HOME=$SDK/ndk/27.2.12479018
export PATH=\$HOME/gradle-$GRADLE_V/bin:\$ANDROID_HOME/platform-tools:\$PATH
ENV
echo "sdk.dir=$SDK" > "$(dirname "$0")/../local.properties"
echo "Done. Use: source .botw-apk-env"
