# RTSP-Server

[![Release](https://jitpack.io/v/pedroSG94/RTSP-Server.svg)](https://jitpack.io/#pedroSG94/RTSP-Server)
[![Documentation](https://img.shields.io/badge/library-documentation-orange)](https://pedroSG94.github.io/RTSP-Server)

Plugin of RootEncoder to stream directly to RTSP player.

## Compile

Require API 16+

To use this library in your project with gradle add this to your build.gradle:

```gradle
allprojects {
  repositories {
    maven { url 'https://jitpack.io' }
  }
}
dependencies {
  implementation 'com.github.pedroSG94:RTSP-Server:1.4.0'
  implementation 'com.github.pedroSG94.RootEncoder:library:2.7.1'
}

```

### NOTE:

The app example need min API 23+ but the library is compatible with API 16+