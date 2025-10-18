# Open Eyes on China Android App

A minimal Android wrapper app that displays your Hugo-generated website inside a WebView with pull-to-refresh.

## Features

- Loads your production website URL
- Swipe-to-refresh
- Back navigation within WebView history
- Basic Material 3 theme & edge-to-edge layout

## Prerequisites

Before building you need:
- JDK 17 (required by Android Gradle Plugin 8.5.2)
- Android SDK installed (including Platform Tools / adb)
- Android 34 (compile/target) platform + Build Tools (e.g. 34.0.x)

### Install JDK 17 (macOS / Homebrew)
```bash
brew install openjdk@17
# Add to shell (zsh):
echo 'export JAVA_HOME="$(/usr/libexec/java_home -v17)"' >> ~/.zshrc
echo 'export PATH="$JAVA_HOME/bin:$PATH"' >> ~/.zshrc
source ~/.zshrc
java -version  # verify 17
```
If Gradle still picks the wrong JDK you can uncomment `org.gradle.java.home` in `gradle.properties` and set it to the JDK 17 path.

### Install Android SDK (Option A: Android Studio)
1. Download Android Studio and open this project folder.
2. Use SDK Manager to install: "Android 14 (API 34)" + latest build tools, and Platform Tools.

### Install Android SDK (Option B: Command-line only)
```bash
mkdir -p "$HOME/Library/Android" && cd "$HOME/Library/Android"
curl -O https://dl.google.com/android/repository/commandlinetools-mac-11076708_latest.zip
unzip commandlinetools-mac-*_latest.zip -d cmdline-tools
mkdir -p cmdline-tools/latest
mv cmdline-tools/cmdline-tools/* cmdline-tools/latest/
# Add to PATH
echo 'export ANDROID_HOME="$HOME/Library/Android/sdk"' >> ~/.zshrc
echo 'export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"' >> ~/.zshrc
echo 'export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"' >> ~/.zshrc
source ~/.zshrc
# Accept licenses & install required packages
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### Point Project to SDK
The build error `SDK location not found` means Gradle couldn't find your SDK. Fix it by either:
1. Creating `local.properties` (NOT committed) with:
```
sdk.dir=/Users/<your-username>/Library/Android/sdk
```
2. Or exporting environment variables in `~/.zshrc`:
```
export ANDROID_HOME="$HOME/Library/Android/sdk"
export ANDROID_SDK_ROOT="$HOME/Library/Android/sdk"
```
Then restart the shell (or run `source ~/.zshrc`).

## Next Steps

1. Replace the placeholder URL in `app/src/main/res/values/strings.xml` (`default_url`).
2. Add app icons (mipmap) via Android Studio's Image Asset tool.
3. Configure a custom splash screen / offline handling.
4. Optionally implement in-app navigation enhancements (bottom bar, share, open external links in browser).
5. Sign the app for release.

## Building

Open the `android` folder in Android Studio or run Gradle:

```bash
./gradlew :app:assembleDebug
```

The resulting APK will be in `app/build/outputs/apk/debug/`.

### Build & Install to Connected Device Quickly
```bash
./gradlew :app:installDebug
adb shell monkey -p com.openeyesonchina.app -c android.intent.category.LAUNCHER 1
```
If multiple devices are connected, list them first:
```bash
adb devices
```

### Troubleshooting
- SDK location error: verify `local.properties` path matches actual SDK directory.
- JDK version error: ensure `java -version` shows 17.
- Missing platform 34: install with `sdkmanager "platforms;android-34"`.
- Build tools missing: `sdkmanager "build-tools;34.0.0"`.

## Customization Ideas

- Inject a JavaScript interface for native sharing.
- Add a simple offline cache strategy using `Service Worker` on the site side or `WebViewAssetLoader` for hybrid content.
- Implement dark mode detection bridging to your site's theme parameter.
