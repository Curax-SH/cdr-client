-printmapping build/release-mapping.txt
-target 17

-dontwarn org.springframework.boot.web.*
-dontwarn org.springframework.boot.webservices.*
-dontwarn org.springframework.web.servlet.*
-dontwarn org.yaml.*

# http://proguard.sourceforge.net/manual/examples.html#native
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# For enumeration classes, see http://proguard.sourceforge.net/manual/examples.html#enumerations
-keepclassmembers,allowoptimization enum * {
    public static **[] values();
    public static **[] entries();
    public static ** valueOf(java.lang.String);
}
