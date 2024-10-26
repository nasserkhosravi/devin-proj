
# Devin project
[![Maven Central](https://img.shields.io/maven-central/v/io.github.nasserkhosravi.devin/write.svg)]([https://search.maven.org/artifact/io.github.libktx/ktx-module](https://search.maven.org/artifact/io.github.nasserkhosravi.devin/write))


Devin project is intended to ease creating a solid language for QA report sharing,
Also the project increase QA awareness from what happen in the application.

The project consist two main module, one for writing logs and other one for presenting logs.

Devin safety:
- Devin-op public functions will be removed in release variant.
- Devin-op is only available in debuggable variant.
- Devin-no-op has null implementation, no operation effect on release. 
- Devin-no-op has very tiny size (near to nothing).

## Sample:
You can see an example in [SampleActivity](https://github.com/nasserkhosravi/devin-proj/blob/main/sample-app/src/main/java/ir/khosravi/sample/devin/SampleActivity.kt)

## Dependency:
Devin write library published in mavenCentral.

```groovy

dependencies {
    debugImplementation "io.github.nasserkhosravi.devin:write:$VERSION"
    releaseImplementation "io.github.nasserkhosravi.devin:write-no-op:$VERSION"
}
```
