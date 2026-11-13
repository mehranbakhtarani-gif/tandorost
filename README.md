# MFit

A fresh Flutter rebuild of MFit. The first milestone is a responsive web preview built from the same Flutter/Dart UI that will later be packaged for Android.

## Current preview scope

- Five navigation areas: Home, Activity, Nutrition, Map, Profile
- Responsive Persian RTL interface
- Independent activity stopwatch
- Interactive demo food logging and water counter
- Map illustration for layout review (not live GPS)

## Web preview

Pushing to `preview/mfit-flutter-first-look` triggers the GitHub Actions workflow in `.github/workflows/deploy-preview.yml`, which builds Flutter Web and deploys it to GitHub Pages.

## Important

The first web build is a UI/interaction prototype. Real GPS tracking, background activity, health integrations, and production food database connections are not enabled in this preview. Those will be implemented and tested in the Android build.
