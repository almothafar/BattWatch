# Play Store assets

Graphics for the Google Play listing, versioned here so the source of truth lives in the repo.
These are **uploaded manually** in the Play Console — they are not built or published by CI.

| File | Play Console field | Spec |
|------|--------------------|------|
| `store-icon-512.png` | Main store listing → App icon | 512×512, 32-bit PNG |
| `feature-graphic-1024x500.png` | Main store listing → Feature graphic | 1024×500, 24-bit PNG/JPEG (no alpha) |
| `screenshots/` | Main store listing → Phone screenshots | added later |

The launcher icon that ships **inside** the app lives under `app/src/main/res/mipmap-*`
(legacy `ic_launcher.png` + `ic_launcher_foreground.png` + the adaptive `mipmap-anydpi-v26`),
and shares the same BattWatch artwork as `store-icon-512.png`.
