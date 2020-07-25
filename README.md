---
title: Trezor Plugin
description: Communicate with Trezor on Android and Browser.
---

# cordova-plugin-trezor

This plugin allows your cordova app to communicate with a Trezor device using a single API on browser and Android
(Only for devices with OTG). The browser implementation uses the latest `trezor-connect` minified bundle and the Android
implementation uses `trezor-android`. Unfortunately there is no trezor API for iOS.

cordova-trezor-plugin registers itself as `cordovaTrezor` in window.
Although the object is in the global scope, it is not available until after the `deviceready` event.

## Installation

    cordova plugin add cordova-plugin-trezor

## Quick Example

```js
document.addEventListener('deviceready', onDeviceReady, false);
function onDeviceReady() {
    cordovaTrezor.manifest({
        email: 'your-email@host.com',
        appUrl: 'https://your-app-url.com'
    });

    cordovaTrezor.getPublicKeys({
        bundle: [{path: "m/49/0'/0'"}]
    }).then((result) => {
        if (result.success) {
            console.log(result.payload);
        }
    });
};
```

## Implemented Methods

- manifest
- getPublicKeys

PRs are welcome for more api implementations.

## manifest

Calling this method before any access to the device is required on browser by
[trezor-connect](https://github.com/trezor/connect/blob/develop/docs/index.md#trezor-connect-manifest);
This method has no effect on Android.

Refer to [types](types/index.d.ts) for inputs and outputs.

### Supported Platforms

- Browser

## getPublicKeys

This method accepts a bundle of paths and returns a `Promise` with a payload of public keys.

Refer to [types](types/index.d.ts) for inputs and outputs.

### Supported Platforms

- Browser
- Android

## Important Note

The returned `path` array in Android is based on signed integer values and in browser it's based on unsigned 32 bit integer.
So they are logically the same but they are different in representations. You can rely on serialized path for comparison purposes.
