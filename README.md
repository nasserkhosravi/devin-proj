# Devin project

Devin project is intended to ease creating a solid language for QA report sharing,
Also the project increase QA awarness from what happen in the application.

The project consist two main module, one for writing logs and one application to present logs.

##  Logging sample:
You can see an example in [SampleActivity](https://github.com/nasserkhosravi/devin-proj/blob/main/sample-app/src/main/java/ir/khosravi/sample/devin/SampleActivity.kt)

## To use Devin:
 Add devin library to your gradle
```groovy

dependencies {
    
}
```

Add to your AndroidManifest.xml
```xml
    <permission android:name="ir.khosravi.devin.permission.READ" 
    android:protectionLevel="normal" />

    <permission android:name="ir.khosravi.devin.permission.WRITE"
    android:protectionLevel="normal" />

    <application>
        <provider
            android:name="ir.khosravi.devin.write.DevinContentProvider"
            android:authorities="ir.khosravi.devin.provider"
            android:exported="true"
            android:readPermission="ir.khosravi.devin.permission.READ"
            android:writePermission="ir.khosravi.devin.permission.WRITE" />
    </application>
```