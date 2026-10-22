# Changelog

## 1.1.0
- Redact sensitive HTTP data by default, configurable with `addFieldsRedactor`, `addPatternsRedactor` and `setDefaultRedactionEnabled`
- Cap stored body size (32 KB by default, `setMaxBodySize`) and replace binary bodies with a note
- Bump devin write module dependency to 4.6.0

## 1.0.2
- Bump devin write module dependency to 4.3.1 (compileSdk/targetSdk 35, minSdk 24).

## 1.0.1
- Fix missing consumer ProGuard rules stripping HAR classes in consumer app release builds.

## 1.0.0
- Initialize and implement write-okhttp 
