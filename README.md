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

// Monitor for NFC state changes
document.addEventListener('nfcstatechange',
  (event) => {
    console.log("NFC state changed to: " + event.detail); // "NFC_OK" or "NFC_DISABLED"
  });
```

```js
// Read only example
cordova.plugins.NFC.beginScanSession(
  (tag) => {
    console.log("TAG SERIAL: " + tag.tagSerial);

    for(const record of tag.ndefRecords) {
      if(record.mimeType)
        console.log("MIME: " + record.mimeType);
    
      console.log("TNF: " + record.tnf.toString());

      console.log("DATA SIZE: " + record.ndefData.length.toString());
    }

    // Explicit call of endSession to end the session
    cordova.plugins.NFC.endSession(() => { console.log("Session ended."); });
  },
  (error) => {
    console.log("Session closed or error reading tag: " + error);
  },
  "Hold your phone near an NFC tag.");
```

```js
// Read and write example
cordova.plugins.NFC.beginScanSession(
  (tag) => {
    console.log("TAG SERIAL: " + tag.tagSerial);

    for(const record of tag.ndefRecords) {
      console.log("MIME: " + record.mimeType);
      console.log("TNF: " + record.tnf.toString());
      console.log("DATA SIZE: " + record.ndefData.length.toString());
      //record.id
    }

    const text = "Hello world";
    const encoder = new TextEncoder();

    // The call to write will use the existing session to write the data
    // and then close the session automatically
    cordova.plugins.NFC.write(
      [  
        {
          id: cordova.plugins.NFC.unset(),
          tnf: cordova.plugins.NFC.TNF_MEDIA,
          mimeType: "text/plain",
          ndefData: encoder.encode(text).buffer
        }, 
      ]
      () => {
        // Called on write success
        console.log("Write successful.");
      },
      (error) => {
        console.log("Session closed or error writing tag: " + error);
      },
      "Hold your phone near an NFC tag.",
      "Data has been successfully written.");
  },
  (error) => {
    console.log("Session closed or error reading tag: " + error);
  },
  "Hold your phone near an NFC tag.");
```

```js
// Write only example
const text = "Hello world";
const encoder = new TextEncoder();

// The call to write will create own session and when tag
// is detected will overwrite it.
cordova.plugins.NFC.write(
  [
    {
      id: cordova.plugins.NFC.unset(),
      tnf: cordova.plugins.NFC.TNF_MEDIA,
      mimeType: "text/plain",
      ndefData: encoder.encode(text).buffer
    }
  ], 
  () => {
    // Called on write success
    console.log("Write successful.");
  },
  (error) => {
    console.log("Error writing tag: " + error);
  });
```

If you like my work and want more nice plugins, you can get me a [beer or stake](https://www.paypal.com/donate/?business=RXTV6JES35UQW&amount=5&no_recurring=0&item_name=Let+me+create+more+inspiring+Cordova+plugins.&currency_code=EUR).
