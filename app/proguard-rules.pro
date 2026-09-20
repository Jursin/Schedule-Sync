-keep class com.schedule.vela.MainActivity { *; }
-keep class com.schedule.vela.MainViewModel { *; }
-keep class androidx.activity.ComponentActivity { *; }
-keep class androidx.lifecycle.ViewModel { *; }

-dontwarn java.beans.**
-dontwarn org.yaml.snakeyaml.**

-keep class org.yaml.snakeyaml.** { *; }
-keepnames class org.yaml.snakeyaml.**
-keeppackagenames org.yaml.snakeyaml
