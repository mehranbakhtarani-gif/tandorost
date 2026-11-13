# MFit

MFit is a Flutter-based sport-performance and health-tracking app. The project keeps a shared Flutter UI for the web preview and Android app.

## Implemented in this preview branch

- Five areas: Home, Activity, Nutrition, Map, Profile/Progress
- Central workout stopwatch persisted across tab changes and app suspension
- Android GPS tracker using Geolocator position updates
- Android foreground location service with an ongoing workout notification
- Route-point and distance persistence in local preferences
- OpenStreetMap tiles and recorded GPS polyline on the map screen
- Offline nutrition catalogue for common foods and Iranian meals
- Online packaged-food lookup through Open Food Facts
- Food diary saves a nutrient snapshot, so online items remain readable offline
- Progress measurements and trend chart for weight, waist, steps, active calories, distance and sleep

## Android build

Pushing to `preview/mfit-flutter-first-look` triggers `.github/workflows/android-apk.yml`. It generates the standard Flutter Android Gradle scaffold, builds a release APK and uploads it as the `mfit-android-release` workflow artifact.

The committed Android manifest declares foreground location service and location permissions. On first use, Android must grant location access, including **Allow all the time** for tracking after the app is backgrounded, and notification permission for the ongoing notification.

## GPS and privacy notes

- GPS tracking starts only after the user taps Start and grants permissions.
- The route is stored locally on the device; this branch does not upload location history to a server.
- Android may still restrict background execution under battery-saver or manufacturer-specific battery management. Users may need to allow unrestricted/background battery use for reliable long sessions.
- The web preview cannot provide native background GPS or an Android ongoing notification.
- OpenStreetMap map tiles require an internet connection and attribution.

## Nutrition data notes

- The offline catalogue includes common foods and typical Iranian dishes; values are estimates per 100 g edible portion and vary with recipe, cooking method and brand.
- Open Food Facts search is used for packaged products when online. Product labels can be incomplete or user-submitted; values should be checked against the package.
- This is a food logging aid, not a medical nutrition database or clinical advice.

## Workflows

- `.github/workflows/deploy-preview.yml`: Flutter web preview
- `.github/workflows/android-apk.yml`: Android release APK
