# CalcExpress

**A fast, complete scientific calculator for Android. Free, with no ads, no accounts and no paid tiers, and it will stay that way.**

CalcExpress is built to fix what stock calculators get wrong. There are no nested menus, the maths is laid out the way you would write it on paper, and equations, systems, integrals and differential equations are solved straight from the keyboard.

> **Free, complete, forever.** Every feature is available to everyone at no cost. There are no ads, in-app purchases, subscriptions, "pro" versions, trackers or data collection, and none will be added later.

---

## Install

1. Download the latest `CalcExpress-x.y.z.apk` from **[Releases](https://github.com/ArturPelicho/CalcExpress/releases/latest)**.
2. Open it on your phone. The first time, Android asks you to allow "install unknown apps" for your browser or file manager.
3. From then on the app updates itself. Use **Settings → About the app → Check for updates**, or tap the banner that appears on the calculator when a new version is out.

Requires Android 6.0 or newer.

## Features

### Calculator
- **Real maths layout** in a LaTeX-style font (Computer Modern). Fractions, powers, roots, integrals, matrices and brackets are drawn properly, and the calculation area always takes at least ⅓ of the screen.
- **Boxes ("cases") to fill.** Every template (fraction, power, integral, formula…) shows empty boxes. Tap one, or use ⇥ to jump to the next empty box.
- **`^` key.** A short press squares; a long press enters the exponent (outlined), and another long press leaves it.
- **Automatic bracket levels.** Brackets change ( ) → [ ] → { } with nesting and grow with their content.
- **Latin and Greek letters** are always one tap away (`abc/αβγ`). A long press gives the capital.
- **DEG / RAD / GRAD** toggle, always visible.
- **Exact results** where they exist (√2/2, π/3, 3/4…), next to the decimal value.
- **Persistent history.** Past calculations stay after closing the app; tap one to reuse it.

### Equations and systems
- `=□` writes an equation and `=` solves it. You get all real roots, plus complex roots for polynomials (`x² + 1 = 0 → x = ±i`).
- Equal roots are shown on one line (`x₁ = x₂ = 3`).
- `⊕=` adds another equation for systems, linear or non-linear.
- **Systems with too few equations** are solved for as many variables as possible, in the order x, y, z, α, β, γ, σ, τ; the rest stay as parameters.

### Calculus
- **Definite integrals,** nested for double and triple integrals.
- **Indefinite integrals** (antiderivatives `+ C`). Leave the limits empty.
- **Reordering integration** (⇅∫), including inner limits that depend on the outer variable. The region is split automatically when needed.
- **Derivatives** `d/dx`, numeric or symbolic.
- **Differential equations:** first order (separable, linear) and second order with constant coefficients, solved symbolically. Initial conditions are lines like `x = 0`, `y = 1`, `d/dx(y) = 0`. When no closed form exists, a numeric solution is computed.
- **Matrices:** determinants, inverses, products and linear systems.

### Graphs
- Functions of one variable, equation roots, integral areas and double-integral regions.
- Several functions on the same axes (hold ⊕= for a plain new line).
- Pinch to zoom, drag to pan, tap to read the value of every curve.

### Conversions
- 13 categories: length, area, volume, mass, temperature, speed, time, pressure, energy, power, force, angle and data. Metric and imperial units, with translated names.

### Formulas
Grouped by area. Tap an area to see its formulas, then tap a formula to open it in the calculator with its boxes ready. Leave the unknown empty and press `=`.
- **Mathematics:** general, calculus & series, matrices.
- **Engineering:** mechanics, stress & strain, beams/torsion/buckling, stress states & failure criteria, fluid mechanics, electrotechnics, computing.

### Mohr's circle (interactive)
- Stress or strain, in **2D or 3D**.
- **2D:** enter σx, σy, τxy, or two points A and B of any element. Choose the plane by its angle θ or its normal, or drag around the circle. The 2θ arc and a rotated element follow your finger.
- **3D:** symmetric tensor matrix, three circles with the admissible region shaded, and the point of a plane given by its normal.
- **Stress/strain fields:** entries can be functions of x, y, z, evaluated at a point.
- **Results:** principal values and directions, maximum shear, invariants I₁, I₂, I₃, and the components on the chosen plane.

### Settings
- **Appearance:** Clear (whites, creams and blues), Dark (true OLED black) or follow the system; LaTeX or sans-serif font.
- **Preferences:** metric or imperial units, language, angle unit.
- **About:** version, updates, privacy policy, licences.

## Languages

The app is fully translated into **English (UK), Português (Portugal), Français, Deutsch, Italiano and Castellano**. This covers every screen, tip, formula name and unit name. On first launch it uses your phone's language if it is one of these, otherwise English. You can change it any time in Settings → Preferences.

## Privacy

No personal data is collected. There are no ads, no analytics and no tracking, and your calculations stay on your device. See [PRIVACY.md](PRIVACY.md).

## Roadmap

Ideas being considered (suggestions welcome in [Issues](https://github.com/ArturPelicho/CalcExpress/issues)):
- Step-by-step solutions for equations, integrals and derivatives.
- More formula areas: thermodynamics, heat transfer, structures, statistics and probability, finance.
- Statistics mode (data lists, mean/σ, regression with plots).
- Complex-number arithmetic and polar/exponential forms everywhere.
- Unit-aware calculation (type `3 m + 20 cm`).
- Saving and naming your own formulas and constants, plus a physical-constants table.
- 3D surface plots and parametric/polar curves.
- Copy results as LaTeX or image, and share.
- Home-screen widget and landscape/tablet layouts.
- Google Play release (a Play build without the self-updater is already prepared).

## For developers

| Module | What |
| --- | --- |
| `core/` | Pure Kotlin: expression tree and editor, parser, evaluator, symbolic algebra (derivatives, antiderivatives, exact values), equation/system/ODE solvers, numeric integration, Mohr's circle, units, formula catalogue. Unit-tested (`./gradlew :core:test`). |
| `app/` | Android app (Kotlin, Jetpack Compose, single activity). |

Build flavours:
- `github`: the sideloaded APK published to GitHub Releases, with the in-app updater (`INTERNET` + `REQUEST_INSTALL_PACKAGES`).
- `play`: the Google Play build, with **no self-updater and no internet permission**, because Play policy forbids apps updating themselves outside Play.

Android support: `minSdk 23` (Android 6.0), `targetSdk 36`.

### Releases and signing
- The **CI** workflow runs tests and builds debug APKs on every pull request and branch push.
- The **Release** workflow runs on every push to `main`. It builds a **signed** release APK and publishes it as `v<version.txt>.<commit count>` (e.g. `v0.1.42`). The last number is the `versionCode`, so each release installs over the previous one.
- Signing needs two repository secrets:
  - `CALCEXPRESS_KEYSTORE_BASE64`: base64 of the PKCS12 keystore.
  - `CALCEXPRESS_SIGNING_PASSWORD`: the store and key password (alias `calcexpress`).

  The keystore is never committed. Keep a backup, because losing it means users must uninstall to update.
- Google Play later: enrol in Play App Signing and use this key as the *upload key*. Play then re-signs with its own key, so the Play build and the GitHub APK cannot update each other (users pick one).

## Licences

Code © Artur Pelicho. Font: CMU Serif (Computer Modern Unicode), SIL Open Font License 1.1. See [licenses/CMU-Serif-OFL.txt](licenses/CMU-Serif-OFL.txt).
