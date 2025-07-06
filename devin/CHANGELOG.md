# Changelog

## 4.0.0
**Write module:**
- DevinTool now accept a boolean to config being enable.
- Add metaIndex table to presenter be able to query something in meta.
- Break-change, DevinTool getOrCreate function changed to init() and get().
- Add type id column to separate tags and types.
- Add connectPlugin function to able extend functionality of DevinTool, see write-okhttp module

## 3.1.1
- Fix devin content provider visibility access that may cause some problem in android 30 and above.   

## 3.1.0
- Make DevinTool as singleton instance.
- Disable devin-api module.

## 3.0.0
- Implement dedicate presenter and image logger. 
- Implement general exception logging.
- Create devin-api module to share codes between operation and no-operation module.

## 2.0.0
- Support multi app client (Content provider moved to presenter)

## 1.1.0
- Support different log level, payload and throwable when log.
- Add no-op version.
- Move devin content provider declaring to write module.
- Remove log function by proguard in release.
- Add snapshot repository.

## 1.0.1 
- Initialize devin write module project.