# Ambient Home

An Android tablet launcher that turns the home screen into an ambient smart display: a big customisable clock, a daily briefing (forecast, agenda, today's special, headlines), weather, a photo frame and a calm "flow" screen. The look is a gradient-based glass style. It does not use real liquid glass; that can be added later.

## Build it on GitHub (no Android Studio needed)

1. Create a new repository on GitHub.
2. Upload everything in this folder to it, keeping the structure. The file `.github/workflows/build.yml` must stay at that exact path.
   - Hidden folders: on Windows/macOS make sure `.github` is included when you drag files in, or use `git push`.
3. Open the repository's **Actions** tab. The **Build APK** workflow runs on every push to `main`. You can also start it with **Run workflow**.
4. When it finishes, open the run and download the artifact:
   - `AmbientHome-debug` for everyday testing.
   - `AmbientHome-release` for a smaller build (signed with the debug key so it installs without a keystore).
5. Unzip the artifact and copy the `.apk` to your tablet. Allow "install unknown apps" for your file manager when asked.

To publish an APK on the repository's Releases page, push a tag that starts with `v`, for example `v0.4.0`.

## First run on the tablet

1. Open **Ambient Home**.
2. Go to **Settings, Set as default home app** and choose Ambient Home. The Home button now brings you back here.
3. Tap **Allow calendar** if you want today's events in the briefing.

## Using it

- Swipe left and right between Clock, Briefing, Weather, Photos and Flow.
- Touch and hold the clock to customise it: swipe between styles, pick a colour, switch 24-hour or the date.
- On the Briefing screen, tap **Edit widgets** to turn widgets on or off.
- **Settings** lets you choose a wallpaper (gradients or your own photo), dim it, pick an accent colour, change city and units, add photos for the slideshow, and keep the screen on.
- **Apps** opens the app list with search. Touch and hold an app for its info page.

## Data sources

- Weather: Open-Meteo (no key needed).
- Headlines: BBC World RSS.
- Agenda: the tablet's own calendar, read locally.

## Project layout

- `app/src/main/java/com/ambient/launcher/` holds the Kotlin and Jetpack Compose code.
- `.github/workflows/build.yml` is the build workflow.
- Versions: Kotlin 1.9.24, Android Gradle Plugin 8.5.2, Gradle 8.9, compile and target SDK 34, minimum SDK 26.

## If the build fails

Open the failed run, expand the failing step and copy the first few lines of red error text. That is enough to find and fix the problem.
