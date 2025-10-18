# Open Eyes on China Android App

A minimal Android wrapper app that displays your Hugo-generated website inside a WebView with pull-to-refresh.

## Features

- Loads your production website URL
- Swipe-to-refresh
- Back navigation within WebView history
- Basic Material 3 theme & edge-to-edge layout

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

## Customization Ideas

- Inject a JavaScript interface for native sharing.
- Add a simple offline cache strategy using `Service Worker` on the site side or `WebViewAssetLoader` for hybrid content.
- Implement dark mode detection bridging to your site's theme parameter.
