# cordova-plugin-nfc-android

A plugin to handle read/write NDEF messages from NFC on Android.

## Installation:

For stable relases type:

```shell
cordova plugin add cordova-plugin-nfc-android
```


For latest releases type:

```shell
cordova plugin add https://github.com/bkamenov/cordova-plugin-nfc-android
```

## API & Usage:

```js
// NFC status checking.
cordova.plugins.NFC.status(
  (status) => {
    console.log('NFC status:', status); //"NFC_OK", "NFC_DISABLED" or "NO_NFC" (device does not have NFC hardware)

    if(status === "NFC_DISABLED") {
      // Open NFC settings
      cordova.plugins.NFC.showSettings();
    }
  });

// Permissions asking (normally permission is automatically granted but just in case check)
cordova.plugins.NFC.askForPermission(
  (granted) => {
    console.log('NFC permission granted:', granted); //1 - granted, 0 - not granted
  });

// Helper function
function arrayBufferToString(buffer) {
  const decoder = new TextDecoder('utf-8');
  const view = new Uint8Array(buffer);
  return decoder.decode(view);
}

// Listen for NDEF tags
document.addEventListener('ndef-tag',
  (event) => {
    const tag = event.detail;
    console.log("NDEF tag detected: " + tag.tagSerial);
    for(const record of tag.ndefRecords) {
      console.log("Record MIME: " + record.mimeType);
      console.log("Record data as text: " + arrayBufferToString(record.ndefData));
      // record.tnf
      // record.id
    }

    // Write NDEF message when a tag is detected (This step is optional,
    // because you are not obligated to write to tags.)
    const ndefRecord = {
      tnf: cordova.plugins.NFC.TNF_MEDIA,
      id: cordova.plugins.NFC.unset(),
      mimeType: "text/plain", // mimeType is optional
      ndefData: new TextEncoder().encode("My message").buffer
    };
    // The write command should be run in the context of read. 
    // Otherwise, the behavior is unknown.
    cordova.plugins.NFC.write([ndefRecord],
      () => {
        console.log('NDEF message written successfully');
      },
      (error) => {
        console.log('Failed to write NDEF message:', error);
      });
  });

// Monitor for NFC state changes
document.addEventListener('nfcstatechange',
  (event) => {
    console.log("NFC state changed to: " + event.detail); // "NFC_OK" or "NFC_DISABLED"
  });
```

It is recomended to use [cordova-plugin-nfc-launcher-android](https://github.com/bkamenov/cordova-plugin-nfc-launcher-android.git) if you want your app to launch automatically when a NDEF message with specific MIME type is detected. 



If you like my work and want more nice plugins, you can get me a [beer or stake](https://www.paypal.com/donate/?business=RXTV6JES35UQW&amount=5&no_recurring=0&item_name=Let+me+create+more+inspiring+Cordova+plugins.&currency_code=EUR).


