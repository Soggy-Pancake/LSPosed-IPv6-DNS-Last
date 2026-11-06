# Wi-Fi DNS Order Fix

LSPosed/Vector module (libxposed API 101/102, Android 16, tested target: LineageOS 23.2).
On Wi-Fi, moves IPv6 DNS servers (e.g. router RDNSS) behind IPv4 DNS servers
before they reach the resolver. Mobile data is untouched. Scope: System Framework.

## Build

Option A: Android Studio
1. Open this folder, accept the Gradle sync (let it install SDK 35 if asked).
2. Build > Generate App Bundles or APKs > Build APK(s).
3. APK: app/build/outputs/apk/release/app-release.apk

Option B: command line (Arch)
1. sudo pacman -S jdk17-openjdk && sudo archlinux-java set java-17-openjdk
2. Install Android SDK command-line tools, then:
   sdkmanager "platforms;android-35" "build-tools;35.0.0"
3. export ANDROID_HOME=<sdk path>
4. ./gradlew assembleRelease

## Install and enable
1. adb install app/build/outputs/apk/release/app-release.apk
2. LSPosed/Vector manager > Modules > enable "Wi-Fi DNS Order Fix"
3. Scope: tick "System Framework" if not already ticked
4. Reboot

## Verify
- adb logcat -d -s WifiDnsOrder
  Expect: "module loaded in system_server", "hooked SystemServiceManager.startService",
  "ConnectivityServiceInitializer is starting", "hooked DnsManager.noteDnsServersForNetwork",
  then "net N (wlan0) DNS [...] -> [...]" after Wi-Fi connects.
- adb shell su -c 'dumpsys dnsresolver' : Wi-Fi NetId should list IPv4 servers first.
- No lines at all: adb logcat -d | grep -iE "lsposed|vector|xposed|WifiDnsOrder"

## Bootloop recovery
Disable the module in the manager, or remove it from Magisk modules (the Vector module),
then reboot. Collect logs first: adb logcat -b all -d > boot.log
