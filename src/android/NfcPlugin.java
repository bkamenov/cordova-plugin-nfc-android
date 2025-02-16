package com.cordova.plugin.nfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Build;
import android.os.Bundle;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NfcPlugin extends CordovaPlugin {
    private static final String STATUS_NFC_OK = "NFC_OK";
    private static final String STATUS_NO_NFC = "NO_NFC";
    private static final String STATUS_NFC_DISABLED = "NFC_DISABLED";

    private static final int NFC_PERMISSION_REQUEST_CODE = 1967;
    private CallbackContext ndefCallbackContext;
    private CallbackContext stateChangeCallbackContext;
    private CallbackContext permissionCallbackContext;
    private CallbackContext writeCallbackContext;
    private Tag currentTag;
    private PendingIntent pendingIntent;
    private IntentFilter[] intentFiltersArray;
    private String[][] techListsArray;
    private BroadcastReceiver nfcStateReceiver;
    private boolean lastNfcEnabledState;

    private String debug = "";

    @Override
    protected void pluginInitialize() {
        lastNfcEnabledState = getNfcStatus().equals(STATUS_NFC_OK);
        registerNfcStateReceiver();
        createPendingIntent();
        super.pluginInitialize();
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        
        if(action.equals("askForPermission")) {
            askForPermission(callbackContext);
            return true;
        }

        String nfcStatus = getNfcStatus();

        if(action.equals("status")) {
            status(nfcStatus, callbackContext);
            return true;
        }

        if(action.equals("showSettings")) {
            showSettings(callbackContext);
            return true;
        }

        if(action.equals("handleLaunchNfcIntent")) {
            handleLaunchNfcIntent(callbackContext);
            return true;
        }

        if(action.equals("setTagListener")) {
            setNdefListener(callbackContext);
            return true;
        }

        if (action.equals("setStateChangeListener")) {
            setStateChangeListener(callbackContext);
            return true;
        }

        if (!nfcStatus.equals(STATUS_NFC_OK)) {
            callbackContext.error(nfcStatus);
            return true; // NFC is not present or is disabled
        }

        if(action.equals("write")) {
            write(args, callbackContext);
        }
        else if(action.equals("beginNfc")) {
            beginNfc(callbackContext);
            return true;
        }
        else {
          return false;
        }
        
        return true;
    }

    private void status(String nfcStatus, CallbackContext callbackContext) {
        callbackContext.success(nfcStatus);
    }

    private void askForPermission(CallbackContext callbackContext) {
        if (ContextCompat.checkSelfPermission(getActivity(), android.Manifest.permission.NFC) == PackageManager.PERMISSION_GRANTED) {
            callbackContext.success(1);
        } else {
            permissionCallbackContext = callbackContext;
            ActivityCompat.requestPermissions(getActivity(), new String[]{android.Manifest.permission.NFC}, NFC_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == NFC_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionCallbackContext.success(1);
            } else {
                permissionCallbackContext.success(0);
            }
        }
    }

    private void showSettings(CallbackContext callbackContext) {
        Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
        cordova.getActivity().startActivity(intent);
        callbackContext.success();
    }

    private void handleLaunchNfcIntent(CallbackContext callbackContext) {
      Intent intent = getIntent();
      handleNfcIntent(intent);
      callbackContext.success();
    }

    private void write(JSONArray args, CallbackContext callbackContext) {
        try {
            if (currentTag == null) {
                callbackContext.error("No NFC tag available for writing");
                return;
            }

            JSONArray records = args.getJSONArray(0);

            List<NdefRecord> recordList = new ArrayList<>();

            for (int i = 0; i < records.length(); i++) {
                JSONObject recordJson = records.getJSONObject(i);
    
                short tnf = (short) recordJson.getInt("tnf"); // Extract TNF
                byte[] id = toByteArray(recordJson.getJSONArray("id")); // Extract ID
                byte[] payload = toByteArray(recordJson.getJSONArray("ndefData")); // Extract Payload
                byte[] type = recordJson.getString("mimeType").isEmpty() ? new byte[0] : recordJson.getString("mimeType").getBytes(StandardCharsets.UTF_8);
    
                // Create NdefRecord
                NdefRecord record = new NdefRecord(tnf, type, id, payload);
                recordList.add(record);
            }

            NdefMessage ndefMessage = new NdefMessage(recordList.toArray(new NdefRecord[recordList.size()]));

            writeCallbackContext = callbackContext;
            writeTag(currentTag, ndefMessage);
        } catch (JSONException e) {
            callbackContext.error("Invalid JSON format for write message");
        }
    }

    private byte[] toByteArray(JSONArray jsonArray) throws JSONException {
        byte[] byteArray = new byte[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            byteArray[i] = (byte) jsonArray.getInt(i);
        }
        return byteArray;
    }

    private void setNdefListener(CallbackContext callbackContext) {
        this.ndefCallbackContext = callbackContext;
    }

    private void setStateChangeListener(CallbackContext callbackContext) {
        this.stateChangeCallbackContext = callbackContext;
    }

    private void beginNfc(CallbackContext callbackContext) {
      try {
          startNfc();
          callbackContext.success();
      } catch (Exception e) {
          callbackContext.error("Failed to begin listening for NFC intents. Error: " + e.toString());
      }
    }

    private Activity getActivity() {
      return cordova.getActivity();
    }

    private Intent getIntent() {
        return getActivity().getIntent();
    }

    private void createPendingIntent() {
      if (pendingIntent == null) {
          Activity activity = getActivity();
          Intent intent = new Intent(activity, activity.getClass());
          intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
          
          int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M)
              pendingIntentFlags |= PendingIntent.FLAG_MUTABLE;

          pendingIntent = PendingIntent.getActivity(activity, 0, intent, pendingIntentFlags);
      }
    }

    private String getNfcStatus() {
        NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
        if (nfcAdapter == null) {
            return STATUS_NO_NFC;
        } else if (!nfcAdapter.isEnabled()) {
            return STATUS_NFC_DISABLED;
        } else {
            return STATUS_NFC_OK;
        }
    }

    private PendingIntent getPendingIntent() {
        return pendingIntent;
    }

    private void restartNfc() {
        stopNfc();
        startNfc();
    }

    private void startNfc() {
        getActivity().runOnUiThread(() -> {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
            if (nfcAdapter != null) {
                try {
                    // Update the global intentFiltersArray and techListsArray
                    intentFiltersArray = new IntentFilter[3];
                    intentFiltersArray[0] = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
                    intentFiltersArray[1] = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
                    intentFiltersArray[2] = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
                    
                    techListsArray = new String[][] { new String[] { Ndef.class.getName() }, new String[] { NdefFormatable.class.getName() } };

                    // Enable foreground dispatch with the updated filters
                    nfcAdapter.enableForegroundDispatch(getActivity(), getPendingIntent(), intentFiltersArray, techListsArray);
                } catch (Exception ex) {
                    // Handle other potential exceptions
                    ex.printStackTrace();
                }
            }
        });
    }

    private void stopNfc() {
        getActivity().runOnUiThread(() -> {
            NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
            if (nfcAdapter != null) {
                try {
                    nfcAdapter.disableForegroundDispatch(getActivity());
                }
                catch(Exception ex) {
                    //App is possibly terminating
                }
            }
        });
    }

    private void registerNfcStateReceiver() {
        nfcStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {

                NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
                if (nfcAdapter != null && stateChangeCallbackContext != null) {
                    boolean isEnabled = nfcAdapter.isEnabled();
                    if(lastNfcEnabledState != isEnabled)
                    {
                        lastNfcEnabledState = isEnabled;

                        if(isEnabled) {
                          restartNfc();
                        }
                        else {
                          stopNfc();
                        }

                      PluginResult result = new PluginResult(PluginResult.Status.OK, isEnabled ? STATUS_NFC_OK : STATUS_NFC_DISABLED);
                      result.setKeepCallback(true);
                      stateChangeCallbackContext.sendPluginResult(result);
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_ADAPTER_STATE_CHANGED);
        getActivity().registerReceiver(nfcStateReceiver, filter);
    }

    private void unregisterNfcStateReceiver() {
        if (nfcStateReceiver != null) {
            getActivity().unregisterReceiver(nfcStateReceiver);
            nfcStateReceiver = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterNfcStateReceiver();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleNfcIntent(intent);
    }

    private void handleNfcIntent(Intent intent) {
        if(intent == null)
          return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            currentTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
        } else {
            currentTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }

        if(currentTag == null)
          return;

        try {
            Ndef ndef = Ndef.get(currentTag);
            JSONObject ndefTag = new JSONObject();
            JSONArray ndefRecords = new JSONArray();
            ndefTag.put("tagSerial", getTagSerialNumber(currentTag));
            ndefTag.put("ndefRecords", ndefRecords);

            if (ndef != null && ndefCallbackContext != null) {
                NdefMessage ndefMessage = ndef.getCachedNdefMessage();
                if(ndefMessage != null) {
                    NdefRecord[] records = ndefMessage.getRecords();
                    for (NdefRecord record : records) {
                        JSONObject ndefRecord = new JSONObject();
                        
                        ndefRecord.put("tnf", record.getTnf());
                        ndefRecord.put("mimeType", new String(record.getType(), StandardCharsets.UTF_8));
                        ndefRecord.put("id", byteArrayToJSONArray(record.getId()));
                        ndefRecord.put("ndefData", byteArrayToJSONArray(record.getPayload()));

                        ndefRecords.put(ndefRecord);
                    }
                }
            }

            PluginResult result = new PluginResult(PluginResult.Status.OK, ndefTag);
            result.setKeepCallback(true);
            ndefCallbackContext.sendPluginResult(result);
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private JSONArray byteArrayToJSONArray(byte[] data) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (byte b : data) {
            jsonArray.put(b & 0xFF);  // Convert to unsigned integer
        }
        return jsonArray;
    }

    private String getTagSerialNumber(Tag tag) {
        byte[] id = tag.getId();
        StringBuilder sb = new StringBuilder();
        for (byte b : id) {
            sb.append(String.format("%02X:", b));
        }
        return sb.substring(0, sb.length() - 1);
    }

    private void writeTag(Tag tag, NdefMessage message) {
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (ndef.isWritable()) {
                    ndef.writeNdefMessage(message);
                    writeCallbackContext.success();
                } else {
                    writeCallbackContext.error("NDEF tag is not writable.");
                }
                ndef.close();
            } else {
                NdefFormatable formatable = NdefFormatable.get(tag);
                if (formatable != null) {
                    formatable.connect();
                    formatable.format(message);
                    writeCallbackContext.success();
                    formatable.close();
                } else {
                    writeCallbackContext.error("Tag does not support NDEF.");
                }
            }
        } catch (Exception e) {
            writeCallbackContext.error("Failed to write NDEF message: " + e.getMessage());
        } finally {
            writeCallbackContext = null;
        }
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        stopNfc();
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        startNfc();
    }
}
