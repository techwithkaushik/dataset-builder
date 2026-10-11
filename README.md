# Dataset Builder

Standalone Android app for manually creating and annotating image datasets for Photo and Signature detection.

## Features

- Create, edit, duplicate, and delete dataset projects.
- Persist project images and YOLO annotations between app sessions.
- Import multiple images, browse the image library, remove images, and edit annotations.
- Draw and save bounding boxes for **Photo** (class ID `0`) and **Signature** (class ID `1`).
- Export a YOLO-format ZIP with `data.yaml`, image/label splits, and dataset quality guidance.
- Group related forms so images from the same form stay in one dataset split.

## Build

Requirements: JDK 17, Android SDK, and Gradle 8.14.3.

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Package

`org.techwithkaushik.datasetbuilder`
