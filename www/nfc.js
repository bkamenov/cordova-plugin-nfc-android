var exec = require('cordova/exec');

var NFC = {
  status: function (success, error) {
    exec(success, error, 'NfcPlugin', 'status', []);
  },
  askForPermission: function (success, error) {
    exec(success, error, 'NfcPlugin', 'askForPermission', []);
  },
  showSettings: function (success, error) {
    exec(success, error, 'NfcPlugin', 'showSettings', []);
  },
  write: function (message, success, error) {
    exec(success, error, 'NfcPlugin', 'write', [message]);
  },
  setMimeTypeFilter: function (mimeTypes, success, error) {
    exec(success, error, 'NfcPlugin', 'setMimeTypeFilter', [mimeTypes]);
  }
};

require('cordova/channel').onCordovaReady.subscribe(() => {

  function intArrayToArrayBuffer(intArray) {
    var buffer = new ArrayBuffer(intArray.length);
    var view = new Uint8Array(buffer);
    for (var i = 0; i < intArray.length; i++) {
      view[i] = intArray[i];
    }
    return buffer;
  }

  exec((event) => {
    var transformedEvent = { ...event };
    transformedEvent.ndefData = intArrayToArrayBuffer(event.ndefData);
    var ndefEvent = new CustomEvent("ndef", { detail: transformedEvent });
    document.dispatchEvent(ndefEvent);
  }, null, 'NfcPlugin', 'setNdefListener', []);

  exec((event) => {
    var stateChangeEvent = new CustomEvent("nfcstatechange", { detail: event });
    document.dispatchEvent(stateChangeEvent);
  }, null, 'NfcPlugin', 'setStateChangeListener', []);

});

module.exports = NFC;
