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
    var tagEvent = new CustomEvent("ndef-tag", { detail: transformedTag });
    document.dispatchEvent(tagEvent);
  }, null, 'NfcPlugin', 'setTagListener', []);

  exec((event) => {
    var stateChangeEvent = new CustomEvent("nfcstatechange", { detail: event });
    document.dispatchEvent(stateChangeEvent);
  }, null, 'NfcPlugin', 'setStateChangeListener', []);

});

// Wait for onther stuff to be loaded and then fire the launhing ndef intent (if any)
document.addEventListener("deviceready", () => {
  setTimeout(() => {
    exec(null, null, 'NfcPlugin', 'beginNfc', []);
    exec(null, null, 'NfcPlugin', 'handleLaunchNfcIntent', []);
  }, 0);
}, false);

module.exports = NFC;
