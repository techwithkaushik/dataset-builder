# Dataset Builder

A standalone Android app for creating, managing, and annotating image datasets for object-detection and computer-vision tasks.

## Features

- Create multiple projects with custom class names.
- Configure any number of annotation classes; YOLO class IDs follow the order entered in project setup, starting at `0`.
- Import multiple images, browse thumbnails, remove imported copies, and edit bounding boxes.
- Persist project images and annotations between app sessions.
- Export a YOLO-format ZIP with `data.yaml`, image/label splits, and generic annotation guidance.
- Group related images so they stay together in a dataset split.
- Supports positive and negative samples; images with no boxes export with an empty label file.

## Configure classes

When creating or editing a project, enter comma-separated class names, for example:

`Person, Vehicle, Animal`

The names and order are project-specific. The exported `data.yaml` contains the exact class names and matching IDs.

## Build

Requirements: JDK 17, Android SDK, and Gradle 8.14.3.

```bash
gradle :app:assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Package

`org.techwithkaushik.datasetbuilder`
