# Changelog

## [3.3.0](https://github.com/almothafar/BattWatch/compare/v3.2.0...v3.3.0) (2026-09-02)


### Features

* add a theme setting (System / Light / Dark) ([#334](https://github.com/almothafar/BattWatch/issues/334)) ([c07e114](https://github.com/almothafar/BattWatch/commit/c07e114d657f9dbc6321843e7b4db71bfa0289a3))
* add a theme toggle to the gauge corner ([#337](https://github.com/almothafar/BattWatch/issues/337)) ([a1bfca3](https://github.com/almothafar/BattWatch/commit/a1bfca3429a7fc7b45ce10772e8da3b465e653eb))
* follow the system dark theme ([#331](https://github.com/almothafar/BattWatch/issues/331)) ([9b53a88](https://github.com/almothafar/BattWatch/commit/9b53a8885b33d1c2819038dc5f02758fbd337268))
* publish the graphify graph viewer to GitHub Pages ([#325](https://github.com/almothafar/BattWatch/issues/325)) ([95bd7d4](https://github.com/almothafar/BattWatch/commit/95bd7d4b74117df743b2bf17a975dcd083a94e71))
* tell the user when the system stopped background monitoring ([#314](https://github.com/almothafar/BattWatch/issues/314)) ([986625b](https://github.com/almothafar/BattWatch/commit/986625b7402c914da61c59eecc486cb9a2a48201))


### Bug Fixes

* give the monitoring-stopped hint time to be read ([#329](https://github.com/almothafar/BattWatch/issues/329)) ([3553029](https://github.com/almothafar/BattWatch/commit/3553029efd1901c73d752b762bfd51069cd037dd))
* give the preference dialogs the app's own Material 3 panel ([#336](https://github.com/almothafar/BattWatch/issues/336)) ([c95c6a7](https://github.com/almothafar/BattWatch/commit/c95c6a78d5ce561f06149b25c5ff7d41c7c06587))
* make the adaptive-icon background track the artwork it sits under ([#330](https://github.com/almothafar/BattWatch/issues/330)) ([3cfdf98](https://github.com/almothafar/BattWatch/commit/3cfdf98eaa59b57b1956c17ed9e36f2bd0a70e3d))
* stop the Graph Viewer workflow claiming it can enable Pages ([#327](https://github.com/almothafar/BattWatch/issues/327)) ([e66da7a](https://github.com/almothafar/BattWatch/commit/e66da7a644eed282ad04f693f6470da2a7be4b15))
* stop the pre-commit hook recording an empty commit ([#320](https://github.com/almothafar/BattWatch/issues/320)) ([ab3c163](https://github.com/almothafar/BattWatch/commit/ab3c16353e7305732a07038864194b919466415f))

## [3.2.0](https://github.com/almothafar/BattWatch/compare/v3.1.0...v3.2.0) (2026-08-27)


### Features

* add a monochrome layer to the launcher icon ([#252](https://github.com/almothafar/BattWatch/issues/252)) ([6948cbe](https://github.com/almothafar/BattWatch/commit/6948cbe02fa71dfd2db9eff4cbc6ad12ef247acc))
* adopt the BattWatch launcher icon and add Play listing assets ([#236](https://github.com/almothafar/BattWatch/issues/236)) ([1ecdfa0](https://github.com/almothafar/BattWatch/commit/1ecdfa00aba1c40a9d1caf1d58d6658befda4120))
* let the user set the charge target the full-battery alert fires at ([#272](https://github.com/almothafar/BattWatch/issues/272)) ([700e8f6](https://github.com/almothafar/BattWatch/commit/700e8f68b4da98ce499ab04ac6d17529381dff4f))
* repeat the full-battery alert until the charger comes out ([#277](https://github.com/almothafar/BattWatch/issues/277)) ([10fa9cd](https://github.com/almothafar/BattWatch/commit/10fa9cd3ac2f5b9dd867618b1d883bce4645ac75))
* restore a complete landscape layout for the main screen ([#253](https://github.com/almothafar/BattWatch/issues/253)) ([07f902c](https://github.com/almothafar/BattWatch/commit/07f902ccc5ec8d077e927b0c2da2a707eb91bdfc))
* semantic alert icons and a level-mirroring ongoing icon ([#240](https://github.com/almothafar/BattWatch/issues/240)) ([e9b8e4e](https://github.com/almothafar/BattWatch/commit/e9b8e4e8a025af6922b883d9e2a27518d9a2f858))
* show the battery temperature range since the last full charge ([#262](https://github.com/almothafar/BattWatch/issues/262)) ([ebabce0](https://github.com/almothafar/BattWatch/commit/ebabce083ceef7bf6b9a9023a120e19afe81ea28))


### Bug Fixes

* bidi-isolate the Latin numbers embedded in Arabic notification copy ([#276](https://github.com/almothafar/BattWatch/issues/276)) ([ea0f2f9](https://github.com/almothafar/BattWatch/commit/ea0f2f9b1213d1fad5bd8096dc2191a018e96c8c))
* clear the RestrictedApi lint errors and run lint in CI ([#250](https://github.com/almothafar/BattWatch/issues/250)) ([0798a80](https://github.com/almothafar/BattWatch/commit/0798a80648ce780890f7a053f96e4a9c3b54c5c5))
* close the whole charge session when the unplug broadcast is missed ([#269](https://github.com/almothafar/BattWatch/issues/269)) ([6ee0df1](https://github.com/almothafar/BattWatch/commit/6ee0df13b257db46435a27c4cb3bbda4421ad4af))
* dismiss the full-battery alert when the charger comes out ([#266](https://github.com/almothafar/BattWatch/issues/266)) ([17cc1c3](https://github.com/almothafar/BattWatch/commit/17cc1c3ad45bf09f249113b356f0aa978652da21))
* dismiss the overheat notification once the battery cools ([#261](https://github.com/almothafar/BattWatch/issues/261)) ([379cd54](https://github.com/almothafar/BattWatch/commit/379cd540fefb8951b4a64cb5feb379823559421b))
* format quiet-hours time with the shared locale-safe helper ([#247](https://github.com/almothafar/BattWatch/issues/247)) ([db64c6b](https://github.com/almothafar/BattWatch/commit/db64c6b391b9006a76a4e4bcdf1ecb24d1c3d0c8))
* instantiate the preference fragment directly so R8 cannot strip it ([#297](https://github.com/almothafar/BattWatch/issues/297)) ([00d3b2e](https://github.com/almothafar/BattWatch/commit/00d3b2e16323114229b3c66547d67aade843fb85))
* keep every user-facing number in Western digits on Arabic locales ([#274](https://github.com/almothafar/BattWatch/issues/274)) ([9301b11](https://github.com/almothafar/BattWatch/commit/9301b119fcb21ed3f71fa749267b62d46514c986))
* keep the pending sound picker across a screen recreation ([#306](https://github.com/almothafar/BattWatch/issues/306)) ([f44c1a2](https://github.com/almothafar/BattWatch/commit/f44c1a2eba01321fa08f35e7f35eb12a11b307e1))
* make the "alert is showing" flag survive a process kill ([#270](https://github.com/almothafar/BattWatch/issues/270)) ([159885f](https://github.com/almothafar/BattWatch/commit/159885fc64606ba101d46d397980a9c7185ee470))
* play the picked notification sound on the alert channels ([#287](https://github.com/almothafar/BattWatch/issues/287)) ([8033584](https://github.com/almothafar/BattWatch/commit/8033584bb0510230cbf5208b6989e70b7247f0cf))
* refresh the alert channels when a sound is picked ([#304](https://github.com/almothafar/BattWatch/issues/304)) ([2d7a794](https://github.com/almothafar/BattWatch/commit/2d7a794a798dce0bc06c258e6414db7e444b9248))
* restore the notification large icon lost to the adaptive launcher icon ([#299](https://github.com/almothafar/BattWatch/issues/299)) ([15b4662](https://github.com/almothafar/BattWatch/commit/15b4662c8ad01d13aaa53b5815b058094179c6a3))
* stop shipping hardcoded English placeholders on Battery Insights ([#251](https://github.com/almothafar/BattWatch/issues/251)) ([27b148e](https://github.com/almothafar/BattWatch/commit/27b148e19c10ef7bdd04f6bfead07dc09bafca7f))
* stop the Insights cards being clipped, and clear the layout lint nits ([#255](https://github.com/almothafar/BattWatch/issues/255)) ([41dfed4](https://github.com/almothafar/BattWatch/commit/41dfed4121307dc6943693e85c56d5bc6095e4b8))
* stop the service instead of crashing when foreground promotion is refused ([#301](https://github.com/almothafar/BattWatch/issues/301)) ([952959a](https://github.com/almothafar/BattWatch/commit/952959ab7422a1a575bcffcf9d5e5044da4fd7b6))

## [3.1.0](https://github.com/almothafar/BattWatch/compare/v3.0.1...v3.1.0) (2026-07-24)


### Features

* group settings screens into rounded cards (Material You style) ([#226](https://github.com/almothafar/BattWatch/issues/226)) ([f2f8c20](https://github.com/almothafar/BattWatch/commit/f2f8c2030e6a86990e5071f90ad34f1bdedd88ac))
* rebrand app display name to BattWatch ([#233](https://github.com/almothafar/BattWatch/issues/233)) ([33ccde9](https://github.com/almothafar/BattWatch/commit/33ccde96c1b31bf6d2ff3008f87422006de2d37b))
* show averaged measured capacity with min/max in Insights ([#229](https://github.com/almothafar/BattWatch/issues/229)) ([06a385f](https://github.com/almothafar/BattWatch/commit/06a385f6255e99b5ca3bdabc9c73c56346ecc4fb))
* show calculating and tap-to-set hints instead of the dash in the details table ([#224](https://github.com/almothafar/BattWatch/issues/224)) ([eefcd5d](https://github.com/almothafar/BattWatch/commit/eefcd5d3ed761bc65d5fffbccdb34996139ada6f))
* smoothly animate the live battery percentage between counter updates ([#218](https://github.com/almothafar/BattWatch/issues/218)) ([8ae2500](https://github.com/almothafar/BattWatch/commit/8ae25000cbfbd89be798375cb032b3012630291f))


### Bug Fixes

* keep sampling through the charge handshake so fast chargers aren't labelled "Slow charging" ([#228](https://github.com/almothafar/BattWatch/issues/228)) ([05e47b7](https://github.com/almothafar/BattWatch/commit/05e47b7fe806967ae36cdbfea9cddefe645b7e98))
* live sub-percent decimals stuck at .00 on trusted-counter devices ([#215](https://github.com/almothafar/BattWatch/issues/215)) ([2b1ab4f](https://github.com/almothafar/BattWatch/commit/2b1ab4f08ca59bea3cff6cf4265b4f7dd01cb7b0))
* lock app to portrait to stop the main-screen landscape crash ([#232](https://github.com/almothafar/BattWatch/issues/232)) ([4c8fdf1](https://github.com/almothafar/BattWatch/commit/4c8fdf10fd5aa6c08aae2c3cdbd27e45af76f7b0))

## [3.0.1](https://github.com/almothafar/SimpleBatteryNotifier/compare/v3.0.0...v3.0.1) (2026-07-22)


### Bug Fixes

* auto-clean stale fast-drain alert and retry the charge-speed sample ([#205](https://github.com/almothafar/SimpleBatteryNotifier/issues/205)) ([7de9931](https://github.com/almothafar/SimpleBatteryNotifier/commit/7de9931ae5c3de7b37fd1b7fc57deca95170c8c8))
* label the bare current value in the collapsed notification  ([#208](https://github.com/almothafar/SimpleBatteryNotifier/issues/208)) ([1cd1430](https://github.com/almothafar/SimpleBatteryNotifier/commit/1cd1430e265eb9619524319a09e8f029672497ad))
