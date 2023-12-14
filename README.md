[![](https://jitpack.io/v/nasserkhosravi/devin-proj.svg)](https://jitpack.io/#nasserkhosravi/devin-proj)
# Devin project

Devin project is intended to ease creating a solid language for QA report sharing,
Also the project increase QA awareness from what happen in the application.

The project consist two main module, one for writing logs and one application to present logs.

##  Logging sample:
You can see an example in [SampleActivity](https://github.com/nasserkhosravi/devin-proj/blob/main/sample-app/src/main/java/ir/khosravi/sample/devin/SampleActivity.kt)

## To use Devin:
Devin write library published in mavenCentral.

```groovy

dependencies {
    implementation 'io.github.nasserkhosravi.devin:write:1.0.1'
}
```

Add to your AndroidManifest.xml

```xml

<permission android:name="com.khosravi.devin.permission.READ" android:protectionLevel="normal" />

<permission android:name="com.khosravi.devin.permission.WRITE" android:protectionLevel="normal" />

<application>
<provider android:name="com.khosravi.devin.write.DevinContentProvider" android:authorities="com.khosravi.devin.provider"
    android:exported="true" android:readPermission="com.khosravi.devin.permission.READ"
    android:writePermission="com.khosravi.devin.permission.WRITE" />
</application>
```
