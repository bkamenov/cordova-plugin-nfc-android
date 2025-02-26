const exec = require('cordova/exec');

const NFC = {
  /**
   * TNF constants
   */
  TNF_EMPTY: 0x00, // Empty record
  TNF_WELL_KNOWN: 0x01, // NFC Forum well-known type [NFC RTD]
  TNF_MEDIA: 0x02, // Media-type [RFC 2046]
  TNF_URI: 0x03, // Absolute URI [RFC 3986]
  TNF_EXTERNAL_TYPE: 0x04, // NFC Forum external type [NFC RTD]
  TNF_UNKNOWN: 0x05, // Unknown data
  TNF_UNCHANGED: 0x06, // Unchanged
  TNF_RESERVED: 0x07, // Reserved (do not use)

  /**
   * Use this method to set empty data for id or ndefData
   * in a NDEF record. 
   */
  unset: function () { 
    return new ArrayBuffer(0); 
  },

  /**
   * Checks the current NFC status.
   * 
   * Parameters:
   * 
   * - success - called on success with single string argument 'result':
   *   
   *   "NO_NFC" - device does not have NFC hardware.
   *   "NFC_OK" - NFC turned on.
   *   "NFC_DISABLED" - NFC disabled.
   * 
   * - error - called on error.
   */
  status: function (success, error) {
    exec(success, error, 'NfcPlugin', 'status', []);
  },

  /**
   * Asks the user for permission to use NFC.
   * 
   * Parameters:
   * 
   * - sucess - called on succes with single boolean argument 'granted':
   *   true - permission granted;
   *   false - premission not granted;
   * 
   * - error - called on error with a string argument with error description.
   */
  askForPermission: function (success, error) {
    exec(success, error, 'NfcPlugin', 'askForPermission', []);
  },

  /**
   * Opens the setting dialog to allow the user to enable NFC.
   * 
   * Parameters:
   * 
   * - success - called upon success without any arguments.
   * - error - called on error with a string argument with error description.
   */
  showSettings: function (success, error) {
    exec(success, error, 'NfcPlugin', 'showSettings', []);
  },
  /**
   * Begins a read session for detectig a tag.
   * 
   * Parameters:
   * 
   * success - the success calback with the scanned tag as single argument.
   * The tag has following structure:
   * {
   *    tagSerial: string; // e.g. 12:AB:FF:33:CD:D0:B2
   *    ndefRecords: [
   *      {
   *        id: ArrayBuffer | null;
   *        tnf: number; // Integer represnting record TNF as in NFC spec.
   *        mimeType: string | null; // e.g. text/plain OR null
   *        ndefData: JSONArray; // array of unsigned integers for each NDEF data byte or empty for no data
   *      },
   *      ...
   *    ]
   * } 
   * 
   * Example usage:
   * 
   * // 1. Read only example
   * cordova.plugins.NFC.beginScanSession(
   * (tag) => {
   *  console.log("TAG SERIAL: " + tag.tagSerial);
   * 
   *  for(const record of tag.ndefRecords) {
   *    console.log("MIME: " + record.mimeType);
   *    console.log("TNF: " + record.tnf.toString());
   *    console.log("DATA SIZE: " + record.ndefData.length.toString());
   *    //record.id
   *  }
   * 
   *  // Explicit call of endSession to end the session
   *  cordova.plugins.NFC.endSession(() => { console.log("Session ended."); });
   * },
   * (error) => {
   *    console.log("Session closed or error reading tag: " + error);
   * });
   * 
   * // 2. Read and write example
   * cordova.plugins.NFC.beginScanSession(
   * (tag) => {
   *  console.log("TAG SERIAL: " + tag.tagSerial);
   * 
   *  for(const record of tag.ndefRecords) {
   *    console.log("MIME: " + record.mimeType);
   *    console.log("TNF: " + record.tnf.toString());
   *    console.log("DATA SIZE: " + record.ndefData.length.toString());
   *    //record.id
   *  }
   * 
   *  const text = "Hello world";
   *  const encoder = new TextEncoder();
   * 
   *  // The call to write will use the existing session to write the data
   *  // and then close the session
   *  cordova.plugins.NFC.write(
   *  [  
   *    {
   *      id: cordova.plugins.NFC.unset(),
   *      tnf: cordova.plugins.NFC.TNF_MEDIA,
   *      mimeType: "text/plain",
   *      ndefData: encoder.encode(text).buffer
   *    }, 
   *  ]
   *  () => {
   *    // Called on write success
   *    console.log("Write successful.");
   * 
   *    // The session will be closed here automatically.
   *  },
   *  (error) => {
   *    console.log("Error writing tag: " + error);
   *  });
   * },
   * (error) => {
   *   console.log("Error reading tag: " + error);
   * });
   */
  beginScanSession: function (success, error, alertMessage) {
    exec((tag) => {
      const transformedTag = { 
        tagSerial: tag.tagSerial,
        ndefRecords: []
      };
      for(const ndef of tag.ndefRecords) {
        transformedTag.ndefRecords.push({
          id: intArrayToArrayBuffer(ndef.id),
          tnf: ndef.tnf,
          mimeType: ndef.mimeType,
          ndefData: intArrayToArrayBuffer(ndef.ndefData)
        });
      }
      success(transformedTag);
    },
    error, 'NfcPlugin', 'beginScanSession', []);
  },

  /**
   * Writes records to NDEF tag.
   * 
   * Parameters: 
   * 
   * - ndefRecords - array with ndef records to be written. Each record has structure:
   *   {
   *     id: ArrayBuffer; // Use cordova.plugins.NFC.unset() if not used or empty.
   *     tnf: number; // Use one of the TNF_* values from cordova.plugins.NFC
   *     mimeType: string; // The mime type as by the NFC RFC.
   *     ndefData: ArrayBuffer; // The payload or cordova.plugins.NFC.unset() if not used or empty.
   *   }
   * - success - called upon success without any arguments.
   * - error - called on error with a string argument with error description.
   */
  write: function (ndefRecords, success, error) {
    const transformedRecords = [];
    for(const ndef of ndefRecords) {
      const record = {
        id: Array.from(new Uint8Array(ndef.id)),
        tnf: ndef.tnf,
        mimeType: ndef.mimeType,
        ndefData: Array.from(new Uint8Array(ndef.ndefData))
      }
      transformedRecords.push(record);
    }
    exec(success, error, 'NfcPlugin', 'write', [transformedRecords]);
  },

  /**
   * Terminate the session.
   * 
   * success - called if session is closed.
   * 
   * error - error callback for future use. Not called.
   */
  endSession: function (success, error) {
    exec(success, error, 'NfcPlugin', 'endSession', []);
  }
};

function intArrayToArrayBuffer(intArray) {
  var buffer = new ArrayBuffer(intArray.length);
  var view = new Uint8Array(buffer);
  for (var i = 0; i < intArray.length; i++) {
    view[i] = intArray[i];
  }
  return buffer;
}

require('cordova/channel').onCordovaReady.subscribe(() => {
  exec((event) => {
    var stateChangeEvent = new CustomEvent("nfcstatechange", { detail: event });
    document.dispatchEvent(stateChangeEvent);
  }, null, 'NfcPlugin', 'setStateChangeListener', []);

});

//Wait for onther stuff to be loaded and then fire the launhing ndef intent (if any)
document.addEventListener("deviceready", () => {
  setTimeout(() => {
    exec(null, null, 'NfcPlugin', 'startNfcMonitoring', []);
  }, 0);
}, false);

module.exports = NFC;
