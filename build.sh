#!/bin/zsh
# Build APK Youder: pasang SDK yang kurang, buat keystore sekali, lalu assembleRelease.
set -e
cd "$(dirname "$0")"

export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH=$JAVA_HOME/bin:$PATH
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
echo "sdk.dir=$ANDROID_HOME" > local.properties

# Sisa kode dari nama lama
rm -rf app/src/main/java/com/adam/sedot

yes | sdkmanager --sdk_root=$ANDROID_HOME --licenses > /dev/null
sdkmanager --sdk_root=$ANDROID_HOME "platforms;android-35" "build-tools;35.0.0"

# Keystore wajib sama untuk setiap update APK; jangan dihapus
[ -f youder.jks ] || keytool -genkeypair -keystore youder.jks -alias youder -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass youder123 -keypass youder123 -dname "CN=Youder"

[ -f gradlew ] || gradle wrapper --gradle-version 8.11.1
./gradlew assembleRelease

cp app/build/outputs/apk/release/app-release.apk Youder.apk
ls -lh Youder.apk
