# Graph Report - BattWatch  (2026-11-02)

## Corpus Check
- 144 files · ~271,027 words
- Verdict: corpus is large enough that graph structure adds value.
- Unclassified: 76 file(s) not represented in the graph (top: .xml 69, (none) 3, .properties 3)

## Summary
- 2702 nodes · 9067 edges · 124 communities (52 shown, 72 thin omitted)
- Extraction: 93% EXTRACTED · 7% INFERRED · 0% AMBIGUOUS · INFERRED: 605 edges (avg confidence: 0.85)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `8285d511`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- TimePickerPreference
- ChargeSpeed
- android.content.SharedPreferences
- android.view.View
- Sample
- org.junit.runners.Parameterized
- BattWatch (Android battery monitor app)
- org.junit.Test
- BatteryDO
- .fold
- HorseshoeProgressBar
- BatteryRate
- Build & Test Job
- MonitoringRestartReceiverTest
- Alerts Preference Screen
- PreferenceDialogThemeTest.java
- .observeAndScale
- Battery Details Label-Value Table
- android.os.Bundle
- GaugeValueSmoother
- Battery Details Table (label : value rows)
- NotificationDetailsPickerTest
- android.content.SharedPreferences
- GenericPreferenceFragment
- com.google.android.material.slider.RangeSlider
- android.content.Context
- MainActivity
- NotificationDetailsRowWidthTest.java
- ContextBacked
- BatteryInsightsActivity
- NotificationLanguageTest
- .evaluate
- .applyChargeTargetWording
- Battery Insights Screen
- HorseshoeProgressBar.java
- .formatWhole
- OngoingStatusContentTest
- BattWatch Ongoing Status Notification
- BatteryHealthTracker
- .formatSocModern
- PreferenceCardDecoration
- Adaptive Icon Foreground Layer Asset Family (ic_launcher_foreground)
- NotificationConfigTest
- PowerConnectionReceiverTest.java
- Prefer Pure JUnit Tests on Android-Free Helpers
- android.content.Intent
- .ensureChannels
- LevelAlertState
- .setLevel
- .formatDuration
- RingtonePickSurvivesRecreationTest
- Arabic values-ar Parity
- org.junit.runner.RunWith
- NotificationDetailsPickerTest.java
- StringResourceDigitsTest
- AppPrefs
- LevelThresholds
- GaugeThemeToggleTest
- Override
- .handleTemperature
- .sendNotification
- BattWatch Agent Guide
- AlertSounds.java
- BatteryHealthGrade
- BatteryDetailsFragment.java
- RingtonePickSurvivesRecreationTest.java
- SurfacePaletteTest
- BatteryHealthTrackerStateTest
- BatteryDetailsRowSyncTest
- NotificationDetailsFragment.java
- Issue Tracker: GitHub Issues via the gh CLI
- NotificationDetailsFragment
- BattWatch Domain Glossary
- release-please-config.json
- .isolate
- Numbers Are Always Western Digits
- Allocation & Draw-Loop Performance Rules
- BattWatch Patterns & JavaDoc
- NotificationServiceTest.java
- .appVersionName
- .assertRendersAsWritten
- When to Extract Methods
- Minimal, Documented Permissions
- Main-Looper Handler Threading Rules
- HorseshoeProgressBarTest
- Avoid Always-Inverted Boolean Methods
- Accessibility Requirements (content descriptions, 48dp targets)
- Edge-to-Edge Display & Modern UI APIs
- Code Organization & Member Order
- Declare Variables Close to Usage
- No Fully Qualified Names
- .accruePartialCycles
- RingtonePreference
- BatteryDetailsScreenDrainRowsTest
- TalkBack accessibility support
- BattWatch
- GaugeIntroSweepTest
- AlertSoundBucketTest
- PowerSource
- Observation
- Robolectric @Config(sdk = 34) Constraint
- .getBatteryInfo
- Motion
- HeaderHolder
- 160-Character Line Width
- .scaledMicroAmps
- SystemService
- ReminderAlertsAgain
- Issue Template Config (Blank Issues Disabled, Email Contact)
- RingtonePickApplyTest
- Flow
- .labelFor
- ScreenDrainTestSupport.java

## God Nodes (most connected - your core abstractions)
1. `BatteryDO` - 140 edges
2. `NotificationDetailsPickerTest` - 105 edges
3. `OngoingStatusContentTest` - 68 edges
4. `BatteryLevelReceiverDecisionTest` - 63 edges
5. `DetailFamily` - 55 edges
6. `AppPrefs` - 55 edges
7. `Streak` - 51 edges
8. `MainActivity` - 50 edges
9. `Sample` - 48 edges
10. `HorseshoeProgressBar` - 48 edges

## Surprising Connections (you probably didn't know these)
- `BatteryDO` --implements--> `Builder Pattern (method chaining, return this)`  [EXTRACTED]
  app/src/main/java/com/almothafar/simplebatterynotifier/model/BatteryDO.java → .claude/guidelines/patterns.md
- `Switch Expression Mapping Pattern` --references--> `BatteryHealthStatus`  [EXTRACTED]
  .claude/guidelines/patterns.md → app/src/main/java/com/almothafar/simplebatterynotifier/model/BatteryHealthStatus.java
- `BatteryHealthStatus` --implements--> `Enums Over Boolean Flags`  [EXTRACTED]
  app/src/main/java/com/almothafar/simplebatterynotifier/model/BatteryHealthStatus.java → .claude/guidelines/patterns.md
- `BatteryRateTracker` --implements--> `Charge Rate`  [INFERRED]
  app/src/main/java/com/almothafar/simplebatterynotifier/service/BatteryRateTracker.java → GLOSSARY.md
- `BatteryRateTracker` --implements--> `Drain Rate`  [INFERRED]
  app/src/main/java/com/almothafar/simplebatterynotifier/service/BatteryRateTracker.java → GLOSSARY.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Three-Tier Battery Level Alert Set** — play_screenshots_4_settings_alerts_notify_warning_level, play_screenshots_4_settings_alerts_notify_critical_every_tick, play_screenshots_4_settings_alerts_notify_full_level, play_screenshots_4_settings_alerts_threshold_slider [EXTRACTED 1.00]
- **In-Screen Alert Threshold Configuration** — play_screenshots_2_discharging_threshold_slider, play_screenshots_2_discharging_critical_threshold, play_screenshots_2_discharging_warning_threshold, play_screenshots_2_discharging_severity_color_coding [EXTRACTED 1.00]
- **Alert threshold control: dual-thumb slider with critical and warning labels** — play_screenshots_1_charging_threshold_slider, play_screenshots_1_charging_critical_threshold, play_screenshots_1_charging_warning_threshold [EXTRACTED 1.00]
- **Android CI Quality Gate Sequence** — _github_workflows_android_ci_unit_tests_step, _github_workflows_android_ci_lint_step, _github_workflows_android_ci_build_debug_apk_step, _github_workflows_android_ci_build_release_apk_step [EXTRACTED 1.00]
- **Battery alerting feature set** — readme_battery_alerts, readme_high_temperature_alert, readme_full_charge_notification, readme_persistent_and_repeated_alerts, readme_quiet_hours [EXTRACTED 1.00]
- **Battery Metric Vocabulary** — glossary_drain_rate, glossary_charge_rate, glossary_instantaneous_current, glossary_design_capacity, glossary_charge_target, glossary_unplug_reminder, glossary_temperature_range, glossary_stable_capacity [EXTRACTED 1.00]
- **Charging state shown by gauge, percent, status label and charge metrics** — play_screenshots_1_charging_horseshoe_gauge, play_screenshots_1_charging_percent_readout, play_screenshots_1_charging_status_label, play_screenshots_1_charging_metric_charge_rate, play_screenshots_1_charging_metric_time_to_full, play_screenshots_1_charging_metric_power_source [EXTRACTED 1.00]
- **Discharge-Specific Metric Set Shown Together** — play_screenshots_2_discharging_drain_rate, play_screenshots_2_discharging_time_remaining, play_screenshots_2_discharging_current, play_screenshots_2_discharging_average_current, play_screenshots_2_discharging_power_source, play_screenshots_2_discharging_state_label [EXTRACTED 1.00]
- **BattWatch Guideline Document Set** — claude_battwatch_agent_guide, _claude_guidelines_rulebook, _claude_guidelines_android_platform_rules, _claude_guidelines_patterns_doc, _claude_guidelines_testing_doc, code_review_guidelines_doc, glossary_battwatch_domain_glossary [EXTRACTED 1.00]
- **Headline-to-Explainer Reading Order** — play_screenshots_3_insights_health_hero_card, play_screenshots_3_insights_stat_card_grid, play_screenshots_3_insights_about_your_battery_card, play_screenshots_3_insights_how_it_works_card [EXTRACTED 1.00]
- **Four-Tile Battery Statistics Grid** — play_screenshots_3_insights_stat_card_grid, play_screenshots_3_insights_measured_capacity_metric, play_screenshots_3_insights_design_capacity_metric, play_screenshots_3_insights_charge_cycles_metric, play_screenshots_3_insights_days_in_use_metric [EXTRACTED 1.00]
- **Issue Intake Funnel** — _github_issue_template_bug_report_form, _github_issue_template_feature_request_form, _github_issue_template_feedback_form, _github_issue_template_config_contact_links [EXTRACTED 1.00]
- **Main Screen Top-to-Bottom Layout Pattern** — play_screenshots_2_discharging_app_bar, play_screenshots_2_discharging_gauge_header_panel, play_screenshots_2_discharging_threshold_slider, play_screenshots_2_discharging_details_table, play_screenshots_2_discharging_insights_button, play_screenshots_2_discharging_developer_credit [EXTRACTED 1.00]
- **Status Notification Composed of Title Plus Four Metric Lines** — play_screenshots_6_notification_title_level_and_state, play_screenshots_6_notification_live_current_line, play_screenshots_6_notification_average_current_line, play_screenshots_6_notification_time_remaining_line, play_screenshots_6_notification_temperature_line [EXTRACTED 1.00]
- **Play Store Listing Artwork Set** — play_store_icon_512_icon, play_feature_graphic_1024x500_graphic, play_feature_graphic_1024x500_messaging, play_feature_graphic_1024x500_wordmark [EXTRACTED 1.00]
- **Quiet Hours Suppression Flow** — play_screenshots_5_settings_behaviour_quiet_hours_toggle, play_screenshots_5_settings_behaviour_alerts_allowed_from, play_screenshots_5_settings_behaviour_alerts_allowed_until, play_screenshots_5_settings_behaviour_critical_ignores_quiet_hours, play_screenshots_5_settings_behaviour_mute_in_silent_mode [EXTRACTED 1.00]
- **Western-Digits Enforcement Chain** — _claude_guidelines_western_digits, _claude_guidelines_locale_root, _claude_guidelines_getstring_trap, _claude_guidelines_no_percent_d_in_resources, _claude_guidelines_batterypercentformatter, _claude_guidelines_stringresourcedigitstest, _claude_guidelines_locale_region_tag_tests, code_review_guidelines_western_digits [EXTRACTED 1.00]
- **Per-Level Alert Sound Configuration** — play_screenshots_4_settings_alerts_warning_level_sound, play_screenshots_4_settings_alerts_critical_level_sound, play_screenshots_4_settings_alerts_ringtone_picker, play_screenshots_5_settings_behaviour_vibrate [INFERRED 0.85]
- **Battery health metrics: capacity, design capacity, charge cycles, voltage** — play_screenshots_1_charging_metric_capacity, play_screenshots_1_charging_metric_design_capacity, play_screenshots_1_charging_metric_charge_cycles, play_screenshots_1_charging_metric_voltage [INFERRED 0.85]
- **BattWatch Visual Identity System** — play_store_icon_512_brand_mark, play_store_icon_512_design_language, play_feature_graphic_1024x500_wordmark, play_store_icon_512_icon, play_feature_graphic_1024x500_graphic, app_src_main_res_mipmap_xxxhdpi_ic_launcher_family, app_src_main_res_mipmap_xxxhdpi_ic_launcher_foreground_family [INFERRED 0.85]
- **Conventional-Commit Driven Release Train** — _github_workflows_pr_title_lint_allowed_types, _github_workflows_release_please_action, _github_workflows_release_please_manifest, _github_dependabot_commit_message_prefix [INFERRED 0.85]
- **Discharge Metrics Feeding the Remaining-Time Estimate** — play_screenshots_6_notification_discharging_state, play_screenshots_6_notification_average_current_line, play_screenshots_6_notification_drain_rate_percent_per_hour, play_screenshots_6_notification_time_remaining_line [INFERRED 0.85]
- **Health Percentage Derived from Measured vs Design Capacity** — play_screenshots_3_insights_health_percent_metric, play_screenshots_3_insights_measured_capacity_metric, play_screenshots_3_insights_design_capacity_metric, play_screenshots_3_insights_health_basis_ladder [INFERRED 0.95]

## Communities (124 total, 72 thin omitted)

### Community 0 - "TimePickerPreference"
Cohesion: 0.07
Nodes (5): TimePickerPreference, TimePickerPreferenceDialogFragmentCompat, GeneralHelper, BoundOrDefaultMinutes, FormatTime

### Community 1 - "ChargeSpeed"
Cohesion: 0.06
Nodes (14): ChargeSpeed, ChargeSpeedTier, FAST, NORMAL, SUPER_FAST, SUPER_FAST_PLUS, TRICKLE, UNKNOWN (+6 more)

### Community 2 - "android.content.SharedPreferences"
Cohesion: 0.08
Nodes (11): FastDrainDetector, SlowChargeDetector, Repeat, RepeatPolicy, Streak, StreakStore, SustainedConditionTracker, FastDrainDetectorTest (+3 more)

### Community 4 - "Sample"
Cohesion: 0.06
Nodes (8): BatteryRateTracker, Sample, AveragedCurrent, ComputeRate, CurrentAvailability, Serialization, TrimToWindow, Windowing

### Community 5 - "org.junit.runners.Parameterized"
Cohesion: 0.04
Nodes (42): BatteryDOTest, GetBatteryPercentage, GetBatteryPercentageInt, ChargeSpeedTest, Classify, PowerMilliwatts, PowerSourceTest, BatteryHealthTrackerTest (+34 more)

### Community 6 - "BattWatch (Android battery monitor app)"
Cohesion: 0.08
Nodes (28): Anti-tivoization / installation information for User Products, GNU General Public License v3.0, Explicit patent grant and non-assertion, Corresponding source availability requirement, Warranty disclaimer and limitation of liability, feature-graphic-1024x500.png (no alpha), In-app launcher icon (mipmap-* / adaptive mipmap-anydpi-v26), Phone screenshots (play/screenshots/) (+20 more)

### Community 8 - "BatteryDO"
Cohesion: 0.10
Nodes (7): BatteryDO, BatteryHealthStatus, CRITICAL, GOOD, UNKNOWN, WARNING, Behaviour

### Community 9 - ".fold"
Cohesion: 0.11
Nodes (5): BatteryTemperatureTracker, TemperatureRange, TemperatureStats, BatteryTemperatureTrackerTest, TargetDerivations

### Community 11 - "BatteryRate"
Cohesion: 0.09
Nodes (5): BatteryRate, IconBand, OngoingStatusContent, Fixed Expanded Notification View, Metric Families (Speed, Current, Time, Temperature, Power Source)

### Community 12 - "Build & Test Job"
Cohesion: 0.05
Nodes (29): AndroidX Update Group, Dependabot Configuration, GitHub Actions Ecosystem Updates, Gradle Ecosystem Updates, Test Libraries Update Group, Build & Test Job, Build Debug APK Step, Build Release APK Step (+21 more)

### Community 14 - "Alerts Preference Screen"
Cohesion: 0.08
Nodes (31): Alerts Settings Screen (screenshot), Alerts Preference Screen, Battery Levels Category, Critical Alert Category, Full Battery Category, Warning Alert Category, Critical Level Sound (Default Orion), Critical Level Threshold (13%) (+23 more)

### Community 17 - "Battery Details Label-Value Table"
Cohesion: 0.09
Nodes (34): Play Store Screenshot: Discharging State, BattWatch App Bar, Average Current Sub-Value (avg -750 mA), Battery Health from Capacity vs Design Capacity, Battery Level Percentage (41.93%), Android BatteryManager Data Source, Current Capacity Metric (4434 mAh), Charge Cycles Metric (23) (+26 more)

### Community 20 - "Battery Details Table (label : value rows)"
Cohesion: 0.09
Nodes (30): Play Store Screenshot: Charging State, App Bar (BattWatch title, settings, overflow), Android BatteryManager Data Source, Charging State Demonstration, Critical Threshold (13%), Battery Details Table (label : value rows), Developer Credit Footer, Teal Gauge Header Panel (+22 more)

### Community 21 - "NotificationDetailsPickerTest"
Cohesion: 0.05
Nodes (9): DetailFamily, CURRENT, POWER_SOURCE, SPEED, TEMPERATURE, TIME, Finger, NotificationDetailsPickerTest (+1 more)

### Community 22 - "android.content.SharedPreferences"
Cohesion: 0.05
Nodes (17): BatteryCapacityTracker, CapacityStats, CapacitySummary, Moment, ScreenDrainTracker, ScreenState, OFF, ON (+9 more)

### Community 24 - "com.google.android.material.slider.RangeSlider"
Cohesion: 0.21
Nodes (3): RangeSlider.OnSliderTouchListener, BatteryRangeSliderPreference, RangeSlider.OnSliderTouchListener

### Community 25 - "android.content.Context"
Cohesion: 0.12
Nodes (4): AlertSpec, AlertRouting, NotificationService, AppLanguage

### Community 33 - "Battery Insights Screen"
Cohesion: 0.13
Nodes (21): About Your Battery Advice Card, Battery Insights Screen, BatteryManager Capacity and Cycle-Count Data Source, Persisted Capacity Sample History, Charge Cycles (24), Days in Use (17), Design Capacity (4700 mAh), Health Basis Caption: Averaged Measured Capacity vs Design (+13 more)

### Community 35 - ".formatWhole"
Cohesion: 0.19
Nodes (3): AlertStyle, NotificationConfig, QuietHours

### Community 36 - "OngoingStatusContentTest"
Cohesion: 0.14
Nodes (3): ScreenDrain, ScreenSplit, OngoingStatusContentTest

### Community 37 - "BattWatch Ongoing Status Notification"
Cohesion: 0.14
Nodes (18): Notification App Name: BattWatch, Body Line: Average Current and Drain Rate (Average: -623 mA . 14%/h), Battery Change Broadcast Triggering the Status Update, BattWatch Ongoing Status Notification, Discharging Charging State, Drain Rate in Percent Per Hour (14%/h), Expandable Multi-Line Notification Body (BigTextStyle), Ongoing Monitoring Notification Channel (+10 more)

### Community 40 - "PreferenceCardDecoration"
Cohesion: 0.12
Nodes (3): CardPreferenceFragment, RowCardDecoration, PreferenceCardDecoration

### Community 41 - "Adaptive Icon Foreground Layer Asset Family (ic_launcher_foreground)"
Cohesion: 0.31
Nodes (12): xxxhdpi Density Bucket (192px legacy / 432px foreground), Legacy Launcher Icon Asset Family (ic_launcher), Adaptive Icon Foreground/Background Layer Split, Background Artwork Baked Into The Foreground Layer, Adaptive Icon Foreground Layer Asset Family (ic_launcher_foreground), ic_launcher_background Gradient, Play Store Feature Graphic (1024x500), Store Listing Value Proposition Copy (+4 more)

### Community 44 - "Prefer Pure JUnit Tests on Android-Free Helpers"
Cohesion: 0.20
Nodes (8): Gradle & ProGuard/R8 Build Rules, SystemService.getBatteryCapacity, Graceful Degradation for Non-Critical Features, SystemService.estimateFullCapacityMah, NotificationService.isWithinTimeRange, New Business Logic Ships With Regression Tests, Receiver Tests via Sticky ACTION_BATTERY_CHANGED, volatile for Mutable Static Fields

### Community 45 - "android.content.Intent"
Cohesion: 0.15
Nodes (3): MonitoringRestartReceiver, ScreenStateReceiver, PowerConnectionService

### Community 46 - ".ensureChannels"
Cohesion: 0.08
Nodes (7): AlertChannel, NotificationChannels, ChannelSounds, DefinitionVersionMigration, NotificationChannelsTest, SettingChangesReVersion, VersionedIds

### Community 47 - "LevelAlertState"
Cohesion: 0.11
Nodes (10): BatteryLevelReceiver, ChargeState, LevelAlertConfig, LevelAlertDecision, LevelAlertState, AlertType, CRITICAL, FULL (+2 more)

### Community 48 - ".setLevel"
Cohesion: 0.18
Nodes (3): PrecisePercentage, UsableLevel, Behaviour

### Community 51 - "Arabic values-ar Parity"
Cohesion: 0.33
Nodes (6): RTL Layout Support, Arabic values-ar Parity, Resource Naming & Untranslated Internal Keys, No Hardcoded User-Facing Strings, Arabic translation build gate (MissingTranslation), MissingTranslation Build Gate

### Community 52 - "org.junit.runner.RunWith"
Cohesion: 0.10
Nodes (18): ArabicLiteralQuantityTest, BattWatchApplicationTest, BidiVisualOrder, FormatAverageCurrentLine, ChargeConnectedDigits, PowerConnectionServiceForegroundStartTest, BatteryOptimizationExemption, PowerSourceLabel (+10 more)

### Community 53 - "NotificationDetailsPickerTest.java"
Cohesion: 0.05
Nodes (4): BatteryPercentFormatter, BidiText, TemperatureUtils, RateWindowTestSupport

### Community 56 - "LevelThresholds"
Cohesion: 0.24
Nodes (3): LevelThresholds, BatteryRangeSliderHelper, Invariants

### Community 58 - "Override"
Cohesion: 0.14
Nodes (4): DragCallback, PickerAdapter, RecyclerView.ViewHolder, RowHolder

### Community 61 - "BattWatch Agent Guide"
Cohesion: 0.25
Nodes (9): BattWatch Android Platform Rules, BattWatch Development Guidelines (machine-facing rulebook), BattWatch Testing Strategy, CODE_REVIEW_GUIDELINES.md reference (no final on parameters), BattWatch — agent guide, graphify, BattWatch Agent Guide, Code Review Guidelines (human-facing) (+1 more)

### Community 63 - "BatteryHealthGrade"
Cohesion: 0.22
Nodes (5): BatteryHealthGrade, EXCELLENT, FAIR, GOOD, POOR

### Community 66 - "SurfacePaletteTest"
Cohesion: 0.10
Nodes (9): ThemeAttributes, PreferenceDialogThemeTest, SnackbarActionContrastTest, Mode, DARK, LIGHT, NeutralRole, SurfacePaletteTest (+1 more)

### Community 69 - "BatteryDetailsRowSyncTest"
Cohesion: 0.15
Nodes (4): RowBinder, RowBinder, BatteryDetailsRowSyncTest, BatteryDetailsFragment.RowBinder

### Community 70 - "NotificationDetailsFragment.java"
Cohesion: 0.09
Nodes (3): AccessibilityDelegateCompat, ResetMenu, RowAccessibility

### Community 71 - "Issue Tracker: GitHub Issues via the gh CLI"
Cohesion: 0.21
Nodes (9): Agent Skills Configuration, Domain Docs: How Skills Read the Glossary and ADRs, Single-Context Layout (one root GLOSSARY.md plus docs/adr/), Issue Tracker: GitHub Issues via the gh CLI, Native Issue Dependencies for Blocking, Sub-Issues of a Parent Issue, Wayfinding Map and Child Tickets, Five Triage Roles (needs-triage, needs-info, ready-for-agent, ready-for-human, wontfix) (+1 more)

### Community 72 - "NotificationDetailsFragment"
Cohesion: 0.15
Nodes (3): LiveStatus, NotificationDetailsFragment, PickerRows

### Community 73 - "BattWatch Domain Glossary"
Cohesion: 0.31
Nodes (10): Domain vocabulary from GLOSSARY.md (drain rate, charge rate, design capacity), BattWatch Domain Glossary, Charge Rate, Charge Target, Design Capacity, Drain Rate, Instantaneous Current, Stable Capacity (+2 more)

### Community 74 - "release-please-config.json"
Cohesion: 0.33
Nodes (5): include-component-in-tag, packages, pull-request-title-pattern, release-type, $schema

### Community 77 - "Numbers Are Always Western Digits"
Cohesion: 0.36
Nodes (3): BatteryPercentFormatter, Locale.ROOT for All Numeric Formatting, StringResourceDigitsTest

### Community 78 - "Allocation & Draw-Loop Performance Rules"
Cohesion: 0.50
Nodes (3): Allocation & Draw-Loop Performance Rules, Cache Expensive Operations (bitmap decode), DRY Principle

### Community 79 - "BattWatch Patterns & JavaDoc"
Cohesion: 0.17
Nodes (9): Modern Java (JDK 25) Feature Use, isNull/nonNull Null-Safety Rule, Builder Pattern (method chaining, return this), BattWatch Patterns & JavaDoc, Null Safety Pattern (isNull guard + warn log), try-with-resources Resource Cleanup, Single Responsibility Principle, Switch Expression Mapping Pattern (+1 more)

### Community 80 - "NotificationServiceTest.java"
Cohesion: 0.18
Nodes (3): NotificationServiceTest, QuietHoursRouting, ResolveChargeStyle

### Community 103 - "PowerSource"
Cohesion: 0.29
Nodes (6): PowerSource, AC, BATTERY, OTHER, USB, WIRELESS

### Community 106 - "Robolectric @Config(sdk = 34) Constraint"
Cohesion: 0.29
Nodes (4): SDK Level Targets (min 26 / target 36), API-Level Branching with Fallback, SharedPreferences Data Storage, Build Configuration (Gradle 9.2.1, JDK 25)

### Community 108 - "Motion"
Cohesion: 0.29
Nodes (7): Motion, BREATH_CRITICAL, BREATH_FILLING, NONE, PULSE_IDLE, WAVE_FORWARD, WAVE_REVERSE

### Community 115 - "SystemService"
Cohesion: 0.13
Nodes (3): Immutable Internal Data Classes, BatteryExtras, SystemService

### Community 117 - "Issue Template Config (Blank Issues Disabled, Email Contact)"
Cohesion: 0.40
Nodes (6): Funding Configuration (GitHub Sponsors, PayPal), Required Environment Fields (Device, Android / Vendor Skin), Bug Report Issue Form, Issue Template Config (Blank Issues Disabled, Email Contact), Feature Request Issue Form, Feedback Issue Form

### Community 119 - "Flow"
Cohesion: 0.40
Nodes (4): Flow, DRAINING, FILLING, FULL

## Ambiguous Edges - Review These
- `Appearance Category` → `Sticky Notifications (cut off at fold)`  [AMBIGUOUS]
  play/screenshots/5_settings_behaviour.jpg · relation: references
- `Android BatteryManager Data Source` → `Chipset Metric (QTI SM8550)`  [AMBIGUOUS]
  play/screenshots/2_discharging.jpg · relation: conceptually_related_to
- `Horseshoe Progress Gauge` → `Info (i) Icon Overlay`  [AMBIGUOUS]
  play/screenshots/2_discharging.jpg · relation: references
- `Settings Gear Action` → `Dual-Thumb Alert Threshold Slider`  [AMBIGUOUS]
  play/screenshots/2_discharging.jpg · relation: shares_data_with
- `Horseshoe Progress Gauge` → `Info (i) Button on Gauge Panel`  [AMBIGUOUS]
  play/screenshots/1_charging.jpg · relation: references
- `View Battery Insights Button` → `Metric: Power Source (AC Charger)`  [AMBIGUOUS]
  play/screenshots/1_charging.jpg · relation: conceptually_related_to
- `Metric: Charge Cycles (23)` → `Metric: Chipset (QTI SM8550)`  [AMBIGUOUS]
  play/screenshots/1_charging.jpg · relation: conceptually_related_to
- `Charge Cycles (24)` → `Days in Use (17)`  [AMBIGUOUS]
  play/screenshots/3_insights.jpg · relation: conceptually_related_to
- `BattWatch Ongoing Status Notification` → `System Notification: Do Not Disturb Turned On by Modes and Routines`  [AMBIGUOUS]
  play/screenshots/6_notification.jpg · relation: conceptually_related_to
- `Fractional Battery Percentage Display (85.82%)` → `System Status Bar Level (86) Versus App Fractional Level (85.82%)`  [AMBIGUOUS]
  play/screenshots/6_notification.jpg · relation: conceptually_related_to
- `Adaptive Icon Foreground/Background Layer Split` → `Background Artwork Baked Into The Foreground Layer`  [AMBIGUOUS]
  app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png · relation: conceptually_related_to
- `Background Artwork Baked Into The Foreground Layer` → `Adaptive Icon Foreground Layer Asset Family (ic_launcher_foreground)`  [AMBIGUOUS]
  app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png · relation: references
- `Issue Template Config (Blank Issues Disabled, Email Contact)` → `Funding Configuration (GitHub Sponsors, PayPal)`  [AMBIGUOUS]
  .github/FUNDING.yml · relation: conceptually_related_to

## Knowledge Gaps
- **116 isolated node(s):** `EXCELLENT`, `GOOD`, `FAIR`, `POOR`, `GOOD` (+111 more)
  These have ≤1 connection - possible missing edges or undocumented components. (Counts symbols only; 326 node(s) total have ≤1 connection when file, concept and rationale nodes are included.)
- **72 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `BatteryDO` connect `BatteryDO` to `ChargeSpeed`, `android.content.SharedPreferences`, `android.view.View`, `Sample`, `org.junit.runners.Parameterized`, `BatteryRate`, `android.content.SharedPreferences`, `android.content.Context`, `MainActivity`, `OngoingStatusContentTest`, `PowerConnectionReceiverTest.java`, `Prefer Pure JUnit Tests on Android-Free Helpers`, `android.content.Intent`, `.setLevel`, `org.junit.runner.RunWith`, `NotificationDetailsPickerTest.java`, `BatteryDetailsFragment.java`, `BatteryDetailsRowSyncTest`, `BattWatch Patterns & JavaDoc`, `BatteryDetailsScreenDrainRowsTest`, `.getBatteryInfo`, `.iconAt`, `ScreenDrainTestSupport.java`?**
  _High betweenness centrality (0.064) - this node is a cross-community bridge._
- **Are the 24 inferred relationships involving `BatteryDO` (e.g. with `.onReceive()` and `.getBatteryPercentage_oneThird_maintainsPrecision()`) actually correct?**
  _`BatteryDO` has 24 INFERRED edges - model-reasoned connections that need verification._
- **What connects `EXCELLENT`, `GOOD`, `FAIR` to the rest of the system?**
  _116 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `TimePickerPreference` be split into smaller, more focused modules?**
  _Cohesion score 0.06887755102040816 - nodes in this community are weakly interconnected._
- **Why does `HorseshoeProgressBar` connect `HorseshoeProgressBar` to `HorseshoeProgressBar.java`, `android.view.View`, `GaugeIntroSweepTest`, `PreferenceCardDecoration`, `Motion`, `NotificationDetailsPickerTest.java`, `Flow`, `HorseshoeProgressBarTest`, `.initializeFirstValues`, `MainActivity`, `NotificationDetailsRowWidthTest.java`?**
  _High betweenness centrality (0.043) - this node is a cross-community bridge._

### Low-confidence Hints
_AMBIGUOUS edges — the extractor was unsure. Verify before acting on these._

- **What is the exact relationship between `Appearance Category` and `Sticky Notifications (cut off at fold)`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **What is the exact relationship between `Android BatteryManager Data Source` and `Chipset Metric (QTI SM8550)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._