# Dumb Browser

A browser with skills. The browser itself stays thin and generic; each skill
is a small instruction file (see `skills/`) that tells the browser which
sites to fetch, what details to pull, and how to compose one view from them.

- App: Android, Java, based on FOSS Browser (see LICENSE.md, AGPLv3).
- Package: `com.schoolofadults.dumbbrowser`
- Skills: `skills/*.md` — PNR first, more later. Skills and view templates
  are fetched from this repo and cached on the phone, so skill and design
  changes never need an app update. Only new native capabilities do.
- Privacy: everything stays on the phone. No accounts, no server. Skills are
  fetched only from this repo. See PRIVACY.md.
- Building: this repo has no Gradle wrapper jar (it cannot be stored here).
  Build with a system Gradle 9.x + JDK 17 + Android SDK (compileSdk 36), or
  use the CI workflow in `.github/workflows/` (added separately).
- Fork note: the upstream base targets SDK 37, but the Android 37 platform
  is not in Google's public SDK listing, so this fork builds against SDK 36
  (minSdk stays 29). Nothing user-facing changes.
