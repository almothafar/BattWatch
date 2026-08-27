# Graph Report - BattWatch  (2026-08-27)

## Corpus Check
- 111 files · ~233,271 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 1915 nodes · 5684 edges · 92 communities (67 shown, 25 thin omitted)
- Extraction: 94% EXTRACTED · 6% INFERRED · 0% AMBIGUOUS · INFERRED: 353 edges (avg confidence: 0.82)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `d32d189e`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- MainActivity.java
- ChargeSpeed
- android.content.SharedPreferences
- BatteryDetailsFragment
- Sample
- org.junit.runners.Parameterized
- BattWatch (Android battery monitor app)
- org.junit.Test
- BatteryDO
- .fold
- HorseshoeProgressBar
- android.app.NotificationManager
- Build & Test Job
- android.content.Intent
- Alerts Preference Screen
- org.junit.runner.RunWith
- LevelAlertState
- Battery Details Label-Value Table
- BaseActivity
- BatteryLevelReceiverTest
- Battery Details Table (label : value rows)
- .assertRendersAsWritten
- CapacityStats
- GenericPreferenceFragment
- LevelThresholds
- android.content.Context
- MainActivity
- Observation
- ContextBacked
- BatteryInsightsActivity
- ChargeConnectedWiring
- .getPrecisePercentage
- Battery Insights Screen
- TimePickerPreference
- .labelFor
- GaugeValueSmoother
- BattWatch Ongoing Status Notification
- BatteryRangeSliderPreference
- SystemService
- PreferenceCardDecoration
- Adaptive Icon Foreground Layer Asset Family (ic_launcher_foreground)
- NotificationConfigTest
- BatteryHealthStatus
- SystemService.getBatteryCapacity
- AlertType
- .ensureChannels
- RingtonePreference
- BatteryRateTrackerTest
- HorseshoeProgressBarTest
- Arabic values-ar Parity
- .celsiusToFahrenheit
- SnapshotCycleCount
- StringResourceDigitsTest
- BatteryDO
- BattWatch Domain Glossary
- Robolectric @Config(sdk = 34) Constraint
- android.util.AttributeSet
- QuietHoursRouting
- BattWatch Development Guidelines (machine-facing rulebook)
- Numbers Are Always Western Digits
- Conventional Commit PR Title Convention
- AlertTypeTest
- BatteryHealthTracker
- Motion
- .formatLive
- .appVersionName
- .formatSocModern
- Issue Template Config (Blank Issues Disabled, Email Contact)
- release-please-config.json
- 160-Character Line Width
- SettingsActivity
- Allocation & Draw-Loop Performance Rules
- Modern Java (JDK 25) Feature Use
- When to Extract Methods
- Minimal, Documented Permissions
- Main-Looper Handler Threading Rules
- Markdown Is Never Hard-Wrapped
- Never Swallow Exceptions Silently
- Avoid Always-Inverted Boolean Methods
- Accessibility Requirements (content descriptions, 48dp targets)
- Edge-to-Edge Display & Modern UI APIs
- Code Organization & Member Order
- Declare Variables Close to Usage
- No Fully Qualified Names
- Flow
- AGENTS.md

## God Nodes (most connected - your core abstractions)
1. `BatteryDO` - 113 edges
2. `BatteryLevelReceiverDecisionTest` - 63 edges
3. `Streak` - 51 edges
4. `HorseshoeProgressBar` - 45 edges
5. `Sample` - 43 edges
6. `NotificationService` - 39 edges
7. `BatteryHealthTracker` - 36 edges
8. `MainActivity` - 36 edges
9. `LevelAlertState` - 35 edges
10. `SystemService` - 34 edges

## Surprising Connections (you probably didn't know these)
- `Adaptive Icon Foreground Layer Asset Family (ic_launcher_foreground)` --semantically_similar_to--> `Play Store Listing Icon (512x512)`  [INFERRED] [semantically similar]
  app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png → play/store-icon-512.png
- `Build Configuration (Gradle 9.2.1, JDK 25)` --semantically_similar_to--> `SDK Level Targets (min 26 / target 36)`  [INFERRED] [semantically similar]
  CODE_REVIEW_GUIDELINES.md → .claude/guidelines/android.md
- `Arabic translation build gate (MissingTranslation)` --semantically_similar_to--> `TalkBack accessibility support`  [INFERRED] [semantically similar]
  .github/copilot-instructions.md → README.md
- `Dual guidelines editions (human-facing vs machine-facing)` --semantically_similar_to--> `Conventional Commit PR titles (Copilot rule)`  [INFERRED] [semantically similar]
  README.md → .github/copilot-instructions.md
- `Western Digits Rule (reviewer edition)` --semantically_similar_to--> `Numbers Are Always Western Digits`  [INFERRED] [semantically similar]
  CODE_REVIEW_GUIDELINES.md → .claude/guidelines.md

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Three-Tier Battery Level Alert Set** — play_screenshots_4_settings_alerts_notify_warning_level, play_screenshots_4_settings_alerts_notify_critical_every_tick, play_screenshots_4_settings_alerts_notify_full_level, play_screenshots_4_settings_alerts_threshold_slider [EXTRACTED 1.00]
- **In-Screen Alert Threshold Configuration** — play_screenshots_2_discharging_threshold_slider, play_screenshots_2_discharging_critical_threshold, play_screenshots_2_discharging_warning_threshold, play_screenshots_2_discharging_severity_color_coding [EXTRACTED 1.00]
- **Alert threshold control: dual-thumb slider with critical and warning labels** — play_screenshots_1_charging_threshold_slider, play_screenshots_1_charging_critical_threshold, play_screenshots_1_charging_warning_threshold [EXTRACTED 1.00]
- **Android CI Quality Gate Sequence** — _github_workflows_android_ci_unit_tests_step, _github_workflows_android_ci_lint_step, _github_workflows_android_ci_build_debug_apk_step, _github_workflows_android_ci_build_release_apk_step [EXTRACTED 1.00]
- **Battery alerting feature set** — readme_battery_alerts, readme_high_temperature_alert, readme_full_charge_notification, readme_persistent_and_repeated_alerts, readme_quiet_hours [EXTRACTED 1.00]
- **Battery Metric Vocabulary** — context_drain_rate, context_charge_rate, context_instantaneous_current, context_design_capacity, context_stable_capacity, context_charge_target, context_temperature_range, context_unplug_reminder [EXTRACTED 1.00]
- **Charging state shown by gauge, percent, status label and charge metrics** — play_screenshots_1_charging_horseshoe_gauge, play_screenshots_1_charging_percent_readout, play_screenshots_1_charging_status_label, play_screenshots_1_charging_metric_charge_rate, play_screenshots_1_charging_metric_time_to_full, play_screenshots_1_charging_metric_power_source [EXTRACTED 1.00]
- **Discharge-Specific Metric Set Shown Together** — play_screenshots_2_discharging_drain_rate, play_screenshots_2_discharging_time_remaining, play_screenshots_2_discharging_current, play_screenshots_2_discharging_average_current, play_screenshots_2_discharging_power_source, play_screenshots_2_discharging_state_label [EXTRACTED 1.00]
- **BattWatch Guideline Document Set** — claude_battwatch_agent_guide, _claude_guidelines_rulebook, _claude_guidelines_android_platform_rules, _claude_guidelines_patterns_doc, _claude_guidelines_testing_doc, code_review_guidelines_doc, context_glossary [EXTRACTED 1.00]
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

## Communities (92 total, 25 thin omitted)

### Community 0 - "MainActivity.java"
Cohesion: 0.16
Nodes (10): android.annotation.SuppressLint, android.os.Bundle, android.view.MenuItem, androidx.activity.result.ActivityResultLauncher, androidx.appcompat.widget.Toolbar, AppPrefs, BatteryPercentFormatter, BidiText (+2 more)

### Community 1 - "ChargeSpeed"
Cohesion: 0.07
Nodes (17): android.os.Handler, ChargeSpeed, ChargeSpeedTier, FAST, NORMAL, SUPER_FAST, SUPER_FAST_PLUS, TRICKLE (+9 more)

### Community 2 - "android.content.SharedPreferences"
Cohesion: 0.06
Nodes (16): android.content.SharedPreferences, FastDrainDetector, SlowChargeDetector, Editor, Outcome, Repeat, RepeatPolicy, Streak (+8 more)

### Community 3 - "BatteryDetailsFragment"
Cohesion: 0.06
Nodes (16): android.view.LayoutInflater, android.view.View, android.view.ViewGroup, android.widget.TableLayout, android.widget.TableRow, android.widget.TextView, androidx.fragment.app.Fragment, BatteryDetailsFragment (+8 more)

### Community 4 - "Sample"
Cohesion: 0.07
Nodes (8): BatteryRateTracker, Sample, TransientState, AveragedCurrent, ComputeRate, Serialization, TrimToWindow, Windowing

### Community 5 - "org.junit.runners.Parameterized"
Cohesion: 0.06
Nodes (36): BatteryDOTest, GetBatteryPercentage, GetBatteryPercentageInt, ChargeSpeedTest, Classify, PowerMilliwatts, StableCapacityMah, BatteryHealthTrackerTest (+28 more)

### Community 6 - "BattWatch (Android battery monitor app)"
Cohesion: 0.06
Nodes (37): Arabic translation build gate (MissingTranslation), CODE_REVIEW_GUIDELINES.md reference (no final on parameters), Conventional Commit PR titles (Copilot rule), Domain vocabulary from CONTEXT.md (drain rate, charge rate, design capacity), Versioning rule: never hand-edit the version, Anti-tivoization / installation information for User Products, Copyleft obligation, GNU General Public License v3.0 (+29 more)

### Community 8 - "BatteryDO"
Cohesion: 0.08
Nodes (3): BatteryDO, Behaviour, UsableLevel

### Community 9 - ".fold"
Cohesion: 0.10
Nodes (5): BatteryTemperatureTracker, TemperatureRange, TemperatureStats, BatteryTemperatureTrackerTest, TargetDerivations

### Community 10 - "HorseshoeProgressBar"
Cohesion: 0.11
Nodes (8): android.animation.ValueAnimator, android.graphics.Canvas, android.graphics.Matrix, android.graphics.Paint, android.graphics.SweepGradient, HorseshoeProgressBar, Override, SweepGradient

### Community 11 - "android.app.NotificationManager"
Cohesion: 0.13
Nodes (7): android.app.NotificationChannel, android.app.NotificationManager, android.media.AudioAttributes, DefinitionVersionMigration, NotificationChannel, NotificationChannelsTest, VersionedIds

### Community 12 - "Build & Test Job"
Cohesion: 0.07
Nodes (36): AGP 8 Version Holds (androidx.core, gradle-wrapper), AndroidX Update Group, Non-Bumping Commit Prefixes (build / ci), Dependabot Configuration, Release Cooldown Window, GitHub Actions Ecosystem Updates, Google Maven Resolution via dependencyResolutionManagement, Gradle Ecosystem Updates (+28 more)

### Community 13 - "android.content.Intent"
Cohesion: 0.17
Nodes (9): android.app.Service, android.content.BroadcastReceiver, android.content.Intent, android.os.IBinder, BootCompletedIntentReceiver, Intent, Override, Override (+1 more)

### Community 14 - "Alerts Preference Screen"
Cohesion: 0.08
Nodes (35): Alerts Settings Screen (screenshot), Alerts Preference Screen, Battery Levels Category, Critical Alert Category, Full Battery Category, Warning Alert Category, Critical Level Sound (Default Orion), Critical Level Threshold (13%) (+27 more)

### Community 15 - "org.junit.runner.RunWith"
Cohesion: 0.13
Nodes (18): ArabicLiteralQuantityTest, BatteryCapacityTrackerTest, ObserveAndAverage, PowerConnectionServiceForegroundStartTest, BoundOrDefaultMinutes, QuietHoursTest, SustainedConditionTrackerTest, CapacityRangeMessageTest (+10 more)

### Community 16 - "LevelAlertState"
Cohesion: 0.17
Nodes (8): BatteryLevelReceiver, ChargeState, Editor, Override, LevelAlertConfig, LevelAlertDecision, LevelAlertState, TemperatureDecision

### Community 17 - "Battery Details Label-Value Table"
Cohesion: 0.09
Nodes (34): Play Store Screenshot: Discharging State, BattWatch App Bar, Average Current Sub-Value (avg -750 mA), Battery Health from Capacity vs Design Capacity, Battery Level Percentage (41.93%), Android BatteryManager Data Source, Current Capacity Metric (4434 mAh), Charge Cycles Metric (23) (+26 more)

### Community 18 - "BaseActivity"
Cohesion: 0.15
Nodes (5): androidx.appcompat.app.AppCompatActivity, BaseActivity, Override, HeaderFragment, Override

### Community 20 - "Battery Details Table (label : value rows)"
Cohesion: 0.09
Nodes (31): Play Store Screenshot: Charging State, App Bar (BattWatch title, settings, overflow), Android BatteryManager Data Source, Charging State Demonstration, Critical Threshold (13%), Battery Details Table (label : value rows), Developer Credit Footer, Fractional Percent Display Decision (+23 more)

### Community 21 - ".assertRendersAsWritten"
Cohesion: 0.10
Nodes (8): BidiVisualOrder, Run, ArabicAlertRenderingTest, ChargeTargetSummaryTest, BatteryRangeSliderHelperTest, ClampPair, BidiTextTest, UnderAnRtlLocale

### Community 22 - "CapacityStats"
Cohesion: 0.16
Nodes (5): BatteryCapacityTracker, CapacityStats, CapacitySummary, Learn, Summarize

### Community 23 - "GenericPreferenceFragment"
Cohesion: 0.11
Nodes (8): androidx.preference.EditTextPreference, androidx.preference.ListPreference, androidx.preference.MultiSelectListPreference, androidx.preference.Preference, androidx.preference.SeekBarPreference, GenericPreferenceFragment, Override, OnSharedPreferenceChangeListener

### Community 24 - "LevelThresholds"
Cohesion: 0.24
Nodes (3): LevelThresholds, BatteryRangeSliderHelper, Invariants

### Community 25 - "android.content.Context"
Cohesion: 0.05
Nodes (15): android.app.PendingIntent, android.content.Context, android.net.Uri, AlertSounds, AlertSpec, BatteryRate, AlertStyle, NotificationConfig (+7 more)

### Community 26 - "MainActivity"
Cohesion: 0.17
Nodes (5): android.view.Menu, Intent, Override, MainActivity, com.google.android.material.button.MaterialButton

### Community 27 - "Observation"
Cohesion: 0.14
Nodes (5): CurrentUnitCalibrator, Observation, Observe, ObserveAndScale, ScaledMicroAmps

### Community 28 - "ContextBacked"
Cohesion: 0.16
Nodes (4): android.content.res.XmlResourceParser, AttrReader, ContextBacked, SeekBarPreference

### Community 29 - "BatteryInsightsActivity"
Cohesion: 0.09
Nodes (9): android.widget.ImageView, androidx.annotation.StringRes, BatteryHealthGrade, EXCELLENT, FAIR, GOOD, POOR, BatteryInsightsActivity (+1 more)

### Community 30 - "ChargeConnectedWiring"
Cohesion: 0.20
Nodes (6): android.app.Notification, ChargeConnectedDigits, ChargeConnectedWiring, NotificationServiceTest, ReminderAlertsAgain, ResolveChargeStyle

### Community 33 - "Battery Insights Screen"
Cohesion: 0.13
Nodes (22): About Your Battery Advice Card, Battery Insights Screen, BatteryManager Capacity and Cycle-Count Data Source, Persisted Capacity Sample History, Charge Cycles (24), Days in Use (17), Design Capacity (4700 mAh), Health Basis Caption: Averaged Measured Capacity vs Design (+14 more)

### Community 34 - "TimePickerPreference"
Cohesion: 0.06
Nodes (12): android.content.res.TypedArray, android.widget.TimePicker, androidx.preference.DialogPreference, androidx.preference.PreferenceDialogFragmentCompat, QuietHours, Override, TimePickerPreference, Override (+4 more)

### Community 37 - "BattWatch Ongoing Status Notification"
Cohesion: 0.14
Nodes (18): Notification App Name: BattWatch, Body Line: Average Current and Drain Rate (Average: -623 mA . 14%/h), Battery Change Broadcast Triggering the Status Update, BattWatch Ongoing Status Notification, Discharging Charging State, Drain Rate in Percent Per Hour (14%/h), Expandable Multi-Line Notification Body (BigTextStyle), Ongoing Monitoring Notification Channel (+10 more)

### Community 38 - "BatteryRangeSliderPreference"
Cohesion: 0.25
Nodes (3): androidx.preference.PreferenceViewHolder, BatteryRangeSliderPreference, Override

### Community 39 - "SystemService"
Cohesion: 0.15
Nodes (3): android.content.res.Resources, BatteryExtras, SystemService

### Community 40 - "PreferenceCardDecoration"
Cohesion: 0.21
Nodes (10): android.graphics.Rect, android.graphics.RectF, androidx.preference.PreferenceFragmentCompat, androidx.recyclerview.widget.RecyclerView, CardPreferenceFragment, Override, Override, PreferenceCardDecoration (+2 more)

### Community 41 - "Adaptive Icon Foreground Layer Asset Family (ic_launcher_foreground)"
Cohesion: 0.36
Nodes (11): xxxhdpi Density Bucket (192px legacy / 432px foreground), Legacy Launcher Icon Asset Family (ic_launcher), Adaptive Icon Foreground/Background Layer Split, Background Artwork Baked Into The Foreground Layer, Adaptive Icon Foreground Layer Asset Family (ic_launcher_foreground), Play Store Feature Graphic (1024x500), Store Listing Value Proposition Copy, BattWatch Wordmark (+3 more)

### Community 43 - "BatteryHealthStatus"
Cohesion: 0.25
Nodes (5): BatteryHealthStatus, CRITICAL, GOOD, UNKNOWN, WARNING

### Community 44 - "SystemService.getBatteryCapacity"
Cohesion: 0.20
Nodes (10): Gradle & ProGuard/R8 Build Rules, Reflection Ban (non-SDK restrictions), SystemService.getBatteryCapacity, Graceful Degradation for Non-Critical Features, SystemService.estimateFullCapacityMah, NotificationService.isWithinTimeRange, Prefer Pure JUnit Tests on Android-Free Helpers, New Business Logic Ships With Regression Tests (+2 more)

### Community 45 - "AlertType"
Cohesion: 0.32
Nodes (6): AlertType, CRITICAL, FULL, WARNING, fromPersistedId(), persistedId()

### Community 46 - ".ensureChannels"
Cohesion: 0.11
Nodes (5): AlertChannel, NotificationChannel, NotificationChannels, ChannelSounds, SettingChangesReVersion

### Community 47 - "RingtonePreference"
Cohesion: 0.18
Nodes (3): Intent, Override, RingtonePreference

### Community 49 - "BatteryRateTrackerTest"
Cohesion: 0.12
Nodes (8): AmberThreshold, BatteryRateTrackerTest, CurrentPlausibility, CurrentSign, Direction, EstimateMinutesToEmpty, EstimateMinutesToFull, FormatAverageCurrentLine

### Community 51 - "Arabic values-ar Parity"
Cohesion: 0.17
Nodes (12): RTL Layout Support, Arabic values-ar Parity, BatteryHealthStatus Enum, determineHealthStatus, Enums Over Boolean Flags, getHealthString, Method Separation Decision (2025), Single Responsibility Principle (+4 more)

### Community 54 - "StringResourceDigitsTest"
Cohesion: 0.30
Nodes (5): StringResourceDigitsTest, java.util.regex.Pattern, javax.xml.parsers.DocumentBuilder, javax.xml.parsers.DocumentBuilderFactory, org.w3c.dom.Element

### Community 55 - "BatteryDO"
Cohesion: 0.33
Nodes (6): isNull/nonNull Null-Safety Rule, BatteryDO, BatteryExtras (immutable internal data class), Builder Pattern (method chaining, return this), Null Safety Pattern (isNull guard + warn log), Always Null-Check System Services

### Community 56 - "BattWatch Domain Glossary"
Cohesion: 0.31
Nodes (10): SharedPreferences Data Storage, Charge Rate, Charge Target, Design Capacity, Drain Rate, BattWatch Domain Glossary, Instantaneous Current, Stable Capacity (+2 more)

### Community 57 - "Robolectric @Config(sdk = 34) Constraint"
Cohesion: 0.33
Nodes (6): SDK Level Targets (min 26 / target 36), API-Level Branching with Fallback, Deprecation Handling Policy, Locale Tests Must Use a Region Tag (ar-rEG), Robolectric @Config(sdk = 34) Constraint, Build Configuration (Gradle 9.2.1, JDK 25)

### Community 58 - "android.util.AttributeSet"
Cohesion: 0.36
Nodes (3): android.util.AttributeSet, android.widget.LinearLayout, MinMaxRangeView

### Community 61 - "BattWatch Development Guidelines (machine-facing rulebook)"
Cohesion: 0.39
Nodes (8): BattWatch Android Platform Rules, Frozen applicationId / Java Package, BattWatch Patterns, JavaDoc & Decision Log, BattWatch Development Guidelines (machine-facing rulebook), BattWatch Testing Strategy, BattWatch Agent Guide, Code Review Guidelines (human-facing), Reviewer Checklist

### Community 62 - "Numbers Are Always Western Digits"
Cohesion: 0.36
Nodes (8): BatteryPercentFormatter, getString(id, int) Configuration-Locale Trap, Locale.ROOT for All Numeric Formatting, Log Messages Use + Concatenation, No %d in Any String Resource, StringResourceDigitsTest, Numbers Are Always Western Digits, Western Digits Rule (reviewer edition)

### Community 63 - "Conventional Commit PR Title Convention"
Cohesion: 0.25
Nodes (8): Short-Lived Branch Strategy, Conventional Commit PR Title Convention, Manual Upload APK Build, PR Title CI Check, release-please Version Ownership, Squash-Merge Workflow, versionCode Derivation Formula, Single-Source Version Manifest

### Community 65 - "BatteryHealthTracker"
Cohesion: 0.08
Nodes (4): BatteryHealthTracker, CycleAccrual, BatteryHealthTrackerStateTest, AccruePartialCycles

### Community 66 - "Motion"
Cohesion: 0.29
Nodes (7): Motion, BREATH_CRITICAL, BREATH_FILLING, NONE, PULSE_IDLE, WAVE_FORWARD, WAVE_REVERSE

### Community 71 - "Issue Template Config (Blank Issues Disabled, Email Contact)"
Cohesion: 0.40
Nodes (6): Funding Configuration (GitHub Sponsors, PayPal), Required Environment Fields (Device, Android / Vendor Skin), Bug Report Issue Form, Issue Template Config (Blank Issues Disabled, Email Contact), Feature Request Issue Form, Feedback Issue Form

### Community 74 - "release-please-config.json"
Cohesion: 0.33
Nodes (5): include-component-in-tag, packages, pull-request-title-pattern, release-type, $schema

### Community 75 - "160-Character Line Width"
Cohesion: 0.40
Nodes (5): Chained Call Wrapping Rule, More Than 4 Parameters, One Per Line, 160-Character Line Width, JavaDoc & Comment Format Standard, Always Use Curly Brackets

### Community 77 - "SettingsActivity"
Cohesion: 0.15
Nodes (8): androidx.preference.PreferenceCategory, SettingsActivity, AlertSoundBucketTest, RingtonePickSurvivesRecreationTest, SettingsScreen, OnPreferenceStartFragmentCallback, org.junit.After, org.robolectric.android.controller.ActivityController

### Community 78 - "Allocation & Draw-Loop Performance Rules"
Cohesion: 0.50
Nodes (4): Allocation & Draw-Loop Performance Rules, Cache Expensive Operations (bitmap decode), DRY Principle, Extract Repeated Method Calls (getResources)

### Community 79 - "Modern Java (JDK 25) Feature Use"
Cohesion: 0.50
Nodes (4): Modern Java (JDK 25) Feature Use, No final on Method Parameters, try-with-resources Resource Cleanup, final on Locals, Never on Parameters

### Community 83 - "When to Extract Methods"
Cohesion: 0.67
Nodes (3): Avoid Over-Engineering, When to Extract Methods, Method Size Limit (~50 lines)

### Community 97 - "Flow"
Cohesion: 0.50
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
- **114 isolated node(s):** `EXCELLENT`, `GOOD`, `FAIR`, `POOR`, `GOOD` (+109 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **25 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `Appearance Category` and `Sticky Notifications (cut off at fold)`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **What is the exact relationship between `Android BatteryManager Data Source` and `Chipset Metric (QTI SM8550)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `Horseshoe Progress Gauge` and `Info (i) Icon Overlay`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **What is the exact relationship between `Settings Gear Action` and `Dual-Thumb Alert Threshold Slider`?**
  _Edge tagged AMBIGUOUS (relation: shares_data_with) - confidence is low._
- **What is the exact relationship between `Horseshoe Progress Gauge` and `Info (i) Button on Gauge Panel`?**
  _Edge tagged AMBIGUOUS (relation: references) - confidence is low._
- **What is the exact relationship between `View Battery Insights Button` and `Metric: Power Source (AC Charger)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._
- **What is the exact relationship between `Metric: Charge Cycles (23)` and `Metric: Chipset (QTI SM8550)`?**
  _Edge tagged AMBIGUOUS (relation: conceptually_related_to) - confidence is low._