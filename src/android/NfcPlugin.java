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
import android.nfc.FormatException;
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

import java.io.IOException;
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
    private CallbackContext sessionCallbackContext = null;
    private CallbackContext stateChangeCallbackContext = null;
    private CallbackContext permissionCallbackContext = null;
   
    private NfcAdapter nfcAdapter = null;
    private PendingIntent pendingIntent = null;
    private BroadcastReceiver nfcStateReceiver = null;
    private boolean lastNfcEnabledState = false;
    private boolean isWriteMode = false;
    private NdefMessage messageToWrite = null;
    private Ndef ndefSession = null;

    @Override
    protected void pluginInitialize() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(getActivity());
        lastNfcEnabledState = getNfcStatus().equals(STATUS_NFC_OK);
        createPendingIntent();
        registerNfcStateReceiver();
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

        if(action.equals("beginScanSession")) {
            beginScanSession(callbackContext);
            return true;
        }

        if (action.equals("setStateChangeListener")) {
            setStateChangeListener(callbackContext);
            return true;
        }

        if(action.equals("write")) {
            write(args, callbackContext);
            return true;
        }

        if(action.equals("endSession")) {
            endSession(callbackContext);
            return true;
        }
        
        return false;
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

    private void beginScanSession(CallbackContext callbackContext) {
        isWriteMode = false;
        sessionCallbackContext = callbackContext;
        ndefSession = null;

        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(getActivity(), pendingIntent, null, null);
        }
    }

    private void write(JSONArray args, CallbackContext callbackContext) {
        boolean reusingSession = false;
        isWriteMode = true;

        try {
            JSONArray records = args.getJSONArray(0);

            List<NdefRecord> recordList = new ArrayList<>();

            for (int i = 0; i < records.length(); i++) {
                JSONObject recordJson = records.getJSONObject(i);
    
                short tnf = (short) recordJson.getInt("tnf"); // Extract TNF
                byte[] id = JSONArrayToByteArray(recordJson.getJSONArray("id")); // Extract ID
                byte[] payload = JSONArrayToByteArray(recordJson.getJSONArray("ndefData")); // Extract Payload
                byte[] type = recordJson.getString("mimeType").isEmpty() ? new byte[0] : recordJson.getString("mimeType").getBytes(StandardCharsets.UTF_8);
    
                // Create NdefRecord
                NdefRecord record = new NdefRecord(tnf, type, id, payload);
                recordList.add(record);
            }

            messageToWrite = new NdefMessage(recordList.toArray(new NdefRecord[recordList.size()]));
        }
        catch (JSONException e) {
            callbackContext.error("Invalid JSON data provided for writing.");
            closeSession();
        }

        reusingSession = ndefSession != null && ndefSession.isConnected();
        sessionCallbackContext = callbackContext;
        
        if(reusingSession) {
            writeNdefTag();
        }
        else {
            if (nfcAdapter != null) {
                nfcAdapter.enableForegroundDispatch(getActivity(), pendingIntent, null, null);
            }
        }
    }

    private void endSession(CallbackContext callbackContext) {
        closeSession();
        callbackContext.success();
    }

    private void setStateChangeListener(CallbackContext callbackContext) {
        stateChangeCallbackContext = callbackContext;
    }

    private void readNdefTag(Tag tag) {
        try {
            JSONObject ndefTag = new JSONObject();
            JSONArray ndefRecords = new JSONArray();
            ndefTag.put("tagSerial", getTagSerialNumber(tag));
            ndefTag.put("ndefRecords", ndefRecords);

            // Read existing NDEF records
            NdefMessage ndefMessage = ndefSession.getNdefMessage();
            if (ndefMessage != null) {
                for (NdefRecord record : ndefMessage.getRecords()) {
                    JSONObject ndefRecord = new JSONObject();
                        
                    ndefRecord.put("tnf", record.getTnf());
                    ndefRecord.put("mimeType", new String(record.getType(), StandardCharsets.UTF_8));
                    ndefRecord.put("id", byteArrayToJSONArray(record.getId()));
                    ndefRecord.put("ndefData", byteArrayToJSONArray(record.getPayload()));

                    ndefRecords.put(ndefRecord);
                }
            }

            sessionCallbackContext.success(ndefTag);
        }
        catch (IOException e) {
            closeSession();
            sendSessionError("Error during NDEF read e.g. lost connection or out of range.");
        }
        catch (FormatException e) {
            closeSession();
            sendSessionError("NDEF Format error.");
        }
        catch (Exception e) {
            closeSession();
            sendSessionError("Unknown error during NDEF read.");
        }
    }
    
    private void writeNdefTag() {
        try {
            if (ndefSession.isWritable()) {
                ndefSession.writeNdefMessage(messageToWrite);
                sessionCallbackContext.success();
                closeSession();
            } 
            else {
                sendSessionError("NDEF tag is not writable.");
                closeSession();
            }
        } catch (Exception e) {
            sendSessionError("Failed to write NDEF: " + e.getMessage());
            closeSession();
        }
    }

    private void sendSessionError(String error) {
        if (sessionCallbackContext != null) {
            sessionCallbackContext.error(error);
            sessionCallbackContext = null;
        }
    }

    private void closeSession() {
        if(ndefSession != null) {
            try {
                if(ndefSession.isConnected())
                    ndefSession.close();
            }
            catch(IOException e) {
                e.printStackTrace();
            }
            
            ndefSession = null;
        }
        sessionCallbackContext = null;
        messageToWrite = null;
    }

    private String getNfcStatus() {
        if (nfcAdapter == null) {
            return STATUS_NO_NFC;
        } else if (!nfcAdapter.isEnabled()) {
            return STATUS_NFC_DISABLED;
        } else {
            return STATUS_NFC_OK;
        }
    }

    private void registerNfcStateReceiver() {
        nfcStateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (NfcAdapter.ACTION_ADAPTER_STATE_CHANGED.equals(intent.getAction())) {             
                    boolean currentNfcEnabledState = getNfcStatus().equals(STATUS_NFC_OK);;

                    /* 
                    int state = intent.getIntExtra(NfcAdapter.EXTRA_ADAPTER_STATE, NfcAdapter.STATE_OFF);
                    switch (state) {
                        case NfcAdapter.STATE_OFF:
                            break;
        
                        case NfcAdapter.STATE_TURNING_OFF:
                            break;
        
                        case NfcAdapter.STATE_ON:
                            break;
        
                        case NfcAdapter.STATE_TURNING_ON:
                            break;
                    }
                    */

                    if(currentNfcEnabledState) {
                        getActivity().runOnUiThread(() -> {
                            if (nfcAdapter != null) {
                                nfcAdapter.enableForegroundDispatch(getActivity(), pendingIntent, null, null);
                            }
                        });
                    }

                    if(lastNfcEnabledState != currentNfcEnabledState) {
                        lastNfcEnabledState = currentNfcEnabledState;
                        notifyNfcStateChanged();
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

    private void notifyNfcStateChanged() {
        if(stateChangeCallbackContext != null) {
            PluginResult result = new PluginResult(PluginResult.Status.OK, 
                lastNfcEnabledState ? STATUS_NFC_OK : STATUS_NFC_DISABLED);
            result.setKeepCallback(true);
            stateChangeCallbackContext.sendPluginResult(result);
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

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        
        if (nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(cordova.getActivity());
        }
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);

        if (nfcAdapter != null) {
            nfcAdapter.enableForegroundDispatch(getActivity(), pendingIntent, null, null);
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

        if(sessionCallbackContext == null)
            return; // Nobody is currently interestend in tag scans

        Tag tag;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag.class);
        } else {
            tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }

        if(tag == null)
          return;

        ndefSession = Ndef.get(tag);
        if(ndefSession == null) {
            sendSessionError("This tag does not support NDEF.");
            closeSession();
            return;
        }

        try {
            ndefSession.connect();  // Attempt to connect to the tag
        }
        catch(Exception e) {
            sendSessionError("Error connecting to tag.");
            closeSession();
        }

        if(isWriteMode) {
            writeNdefTag();
        }
        else {
            readNdefTag(tag);
        }
    }

    // Utility functions
    private JSONArray byteArrayToJSONArray(byte[] data) throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (byte b : data) {
            jsonArray.put(b & 0xFF);  // Convert to unsigned integer
        }
        return jsonArray;
    }

    private byte[] JSONArrayToByteArray(JSONArray jsonArray) throws JSONException {
        byte[] byteArray = new byte[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            byteArray[i] = (byte) jsonArray.getInt(i);
        }
        return byteArray;
    }

    private String getTagSerialNumber(Tag tag) {
        byte[] id = tag.getId();
        StringBuilder sb = new StringBuilder();
        for (byte b : id) {
            sb.append(String.format("%02X:", b));
        }
        return sb.substring(0, sb.length() - 1);
    }
}
