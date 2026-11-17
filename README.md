# FahrenCel

An Android weather app and home-screen widget that shows **°F and °C side by side**, with
optional Kelvin for the extra nerdy. Fully customizable widget looks: live sky, temperature
and photo backgrounds, gradients, shapes, borders, fonts and layouts.

## Try it

Download the latest APK from the [Releases](../../releases) page and open it on an Android
phone (Android 8 or newer). Android will ask you to allow installing from that app the first
time. Newer releases install over older ones.

## Build it yourself

Open the folder in Android Studio and press Run, or run `./gradlew assembleDebug`.
The APK lands in `app/build/outputs/apk/debug/`.

## Making a release

Push a tag starting with `v`, for example:

```
git tag v0.2
git push origin v0.2
```

GitHub builds the APK and attaches it to a new release automatically
(see `.github/workflows/build.yml`).

Weather data by [Open-Meteo.com](https://open-meteo.com/) (CC BY 4.0).
Icons from Google's Material Symbols.
