# Project-specific R8 rules go here (Room, OkHttp and kotlinx-serialization ship their own).

# EngQuest Chronicle: the web page calls these bridge methods by name (window.EngQuestApp.*).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
