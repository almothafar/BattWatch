# Project-specific R8 rules. These are appended to the default proguard-android-optimize.txt that
# app/build.gradle names in its proguardFiles directive, so only rules the defaults don't already
# cover belong here.
#
# See https://developer.android.com/build/shrink-code

# Preference screens name their fragment as a string in android:fragment (res/xml/pref_headers_root.xml),
# and SettingsActivity instantiates it reflectively via FragmentFactory.instantiate. R8 cannot see a class
# reference that only exists as XML attribute text, so without this rule it shrinks GenericPreferenceFragment
# away and every Settings sub-screen dies on Fragment.InstantiationException in a minified build.
# Keep the no-arg constructor the framework calls; the class body is still free to be optimized.
-keep class * extends androidx.fragment.app.Fragment {
    <init>();
}
