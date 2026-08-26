# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in D:\Android\sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Preference screens name their fragment as a string in android:fragment (res/xml/pref_headers_root.xml),
# and SettingsActivity instantiates it reflectively via FragmentFactory.instantiate. R8 cannot see a class
# reference that only exists as XML attribute text, so without this rule it shrinks GenericPreferenceFragment
# away and every Settings sub-screen dies on Fragment.InstantiationException in a minified build.
# Keep the no-arg constructor the framework calls; the class body is still free to be optimized.
-keep class * extends androidx.fragment.app.Fragment {
    <init>();
}
