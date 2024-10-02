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
    var transformedMessage = { ...message };
    transformedMessage.ndefData = Array.from(new Uint8Array(message.ndefData));
    exec(success, error, 'NfcPlugin', 'write', [transformedMessage]);
  },
  setMimeTypeFilter: function (mimeTypes, success, error) {
    exec(success, error, 'NfcPlugin', 'setMimeTypeFilter', [mimeTypes]);
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

//Wait for onther stuff to be loaded and then fire the launhing ndef intent (if any)
document.addEventListener("deviceready", () => {
  setTimeout(() => {
    exec(null, null, 'NfcPlugin', 'beginNfc', []);
    exec(null, null, 'NfcPlugin', 'handleLaunchNfcIntent', []);
  }, 0);
}, false);

module.exports = NFC;
