# CalcExpress

A fast, intuitive scientific calculator for Android, built to fix what stock calculators get wrong:
no nested menus, real maths layout, equations and systems solved straight from the keyboard.

## Install

Download the latest `CalcExpress-x.y.z.apk` from [Releases](https://github.com/ArturPelicho/calcexpress/releases/latest)
and open it on your phone (allow "install unknown apps" for your browser the first time).
After that, the app checks for updates itself: **Settings → About the app → Check for updates**,
or the banner that appears on the calculator when a new version is out.

## What it does (v0.1)

- **Calculation area** (≥ ⅓ of the screen) rendered like LaTeX (Computer Modern font): fractions,
  powers, roots, integrals and brackets drawn properly.
- **Cases**: every template (fraction, power, integral, formula...) shows boxes to fill. Tap a box or
  use ⇥ to jump to the next empty one.
- **^**: short press = square; long press = power mode (exponent outlined), long press again = back down.
- **Brackets by level**: ( ) → [ ] → { } chosen automatically from the nesting, and they grow.
- **Variables and equations**: any letter (Latin or Greek, toggle `abc/αβγ`; long press = capital)
  is a variable. `=□` writes an equality, `=` solves it (all real roots); `⊕=` adds another equation
  (`□ = □`) to solve systems.
- **Calculus**: `∫` definite integrals, nested for double/triple integrals, with **⇅∫** to change the
  order of integration (constant limits); `d/dx` derivatives (usable inside equations).
- **Graphs** (📈): functions of one variable, equations (roots marked), integral areas and double-integral regions.
  Pinch to zoom, drag to pan.
- **DEG / RAD / GRAD** toggle always visible.
- **Menu** (top left): Calculator (history, tips), Conversions (13 categories), Formulas
  (general maths, calculus & series, mechanical, computer, fluids, electrotechnics). Tap a formula and
  it opens with its boxes ready; leave the unknown empty and press `=`.
- **Settings** (top right): Appearance (Clear / Dark OLED true black / system; LaTeX or sans font),
  Preferences (metric/imperial, language: English UK, Português, Français, Deutsch, Italiano, Castellano),
  About (version, updates, privacy, licences).

## Project layout

| Module | What |
| --- | --- |
| `core/` | Pure Kotlin: expression tree and editor, parser, evaluator, equation/system solver, numeric integration, units, formula catalogue. Unit-tested (`./gradlew :core:test`). |
| `app/` | Android app (Kotlin, Jetpack Compose, single activity). |

Build flavours:

- `github` – sideloaded APK published to GitHub Releases, with the in-app updater
  (`INTERNET` + `REQUEST_INSTALL_PACKAGES`).
- `play` – Google Play build. **No self-updater and no internet permission**, because Play policy forbids
  apps updating themselves outside Play.

Android support: `minSdk 23` (Android 6.0, ~99% of active devices; current AndroidX/Compose need it),
`targetSdk 36` (Google Play's current requirement).

## Releases and signing

- `CI` workflow: tests + debug APKs on every pull request / branch push.
- `Release` workflow: every push to `main` builds a **signed** release APK and publishes it as
  `v<version.txt>.<commit count>` (e.g. `v0.1.42`). The last number is the `versionCode`, so each
  release installs over the previous one.
- Signing needs two repository secrets: `CALCEXPRESS_KEYSTORE_BASE64` (base64 of the PKCS12 keystore)
  and `CALCEXPRESS_SIGNING_PASSWORD` (store and key password; alias `calcexpress`). The keystore is never
  committed. Keep a backup: losing it means users must uninstall to update.
- Google Play later: enrol in Play App Signing and use this key as the *upload key*. Play then re-signs
  with its own key, so the Play build and the GitHub APK cannot update each other (users pick one).

## Privacy

See [PRIVACY.md](PRIVACY.md). No personal data, no ads, no analytics.

## Licences

Code © Artur Pelicho. Font: CMU Serif (Computer Modern Unicode), SIL Open Font License 1.1 – see
[licenses/CMU-Serif-OFL.txt](licenses/CMU-Serif-OFL.txt).
