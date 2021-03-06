package io.thisapp.cordova.plugin;

import android.app.AlertDialog;
import android.content.res.Resources;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import org.apache.cordova.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.satoshilabs.trezor.lib.TrezorManager;
import com.satoshilabs.trezor.lib.protobuf.TrezorMessage.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.nayuki.bitcoin.crypto.Base58Check;

interface OnPinListener {
    void onSuccess(Message response);
}

interface OnPassphraseListener {
    void onSuccess(Message response);
}

interface OnMessageListener {
    void onComplete(Message response);
}

public class CordovaTrezor extends CordovaPlugin {
    private String action = "";
    private JSONObject payload;
    private CallbackContext callbackContext;
    private TrezorManager trezorManager;
    private String pin = "";
    private String passphrase = "";
    private Features features;
    private byte[] mainnetPrefixBytes = new byte[0];
    private byte[] testnetPrefixBytes = new byte[0];
    private Resources r;
    private String packageNme;

    private TrezorManager.UsbPermissionReceiver usbPermissionReceiver = new TrezorManager.UsbPermissionReceiver() {
        @Override
        public void onUsbPermissionResult(boolean granted) {
            if (granted) {
                initialize();

                switch (action) {
                    case "getPublicKeys": {
                        getPublicKeys(payload);
                        break;
                    }
                }
            } else {
                sendErrorCallback("Access to device not allowed!");
            }
        }
    };

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);
        usbPermissionReceiver.register(cordova.getContext());
    }

    @Override
    public void onPause(boolean multitasking) {
        super.onPause(multitasking);
        usbPermissionReceiver.unregister(cordova.getContext());
    }

    @Override
    public boolean execute(String action, JSONArray data, CallbackContext callbackContext) {
        r = cordova.getActivity().getResources();
        packageNme = cordova.getActivity().getPackageName();
        this.action = action;
        this.callbackContext = callbackContext;
        if (action.equals("getPublicKeys")) {
            try {
                payload = data.getJSONObject(0);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            trezorManager = new TrezorManager(cordova.getContext());

            if (trezorManager.hasDeviceWithoutPermission(true)) {
                trezorManager.requestDevicePermissionIfCan(true);
            } else {
                try {
                    initialize();
                } catch (Exception e) {
                    sendErrorCallback("Device not connected!");
                    return false;
                }
                getPublicKeys(payload);
            }
            return true;
        } else {
            return false;
        }
    }

    private void initialize() {
        this.features = (Features) trezorManager.sendMessage(Initialize.newBuilder().build());
    }

    private void preProcessMessage(Message message, OnMessageListener onCompleteCallback) {
        if (message instanceof PinMatrixRequest) {
            showPinDialog(r -> {
                if (r instanceof PassphraseRequest) {
                    showPassphraseDialog(onCompleteCallback::onComplete);
                } else {
                    onCompleteCallback.onComplete(r);
                }
            });
        } else if (message instanceof PassphraseRequest) {
            showPassphraseDialog(onCompleteCallback::onComplete);
        } else {
            onCompleteCallback.onComplete(message);
        }
    }

    private void getPublicKeys(JSONObject paths) {
        preProcessMessage(trezorManager.sendMessage(GetPublicKey.newBuilder().build()), response -> {
            cordova.getThreadPool().execute(() -> callbackContext.success(
                    sendPublicKeyMessages(paths)
            ));
        });
    }

    private JSONObject sendPublicKeyMessages(JSONObject paths) {
        JSONObject result = new JSONObject();

        try {
            JSONArray pathsArray = paths.getJSONArray("bundle");
            JSONArray publicKeys = new JSONArray();
            for (int i = 0; i < pathsArray.length(); i++) {
                JSONObject pathObject = pathsArray.getJSONObject(i);
                String serializedPath = pathObject.getString("path");
                Integer[] pathInt = convertPathToArray(serializedPath);
                GetPublicKey.Builder messageBuilder = GetPublicKey.newBuilder();

                for (Integer p : pathInt) {
                    messageBuilder.addAddressN(p);
                }

                Message response = trezorManager.sendMessage(messageBuilder.build());
                PublicKey parsedMessage = PublicKey.parseFrom(response.toByteArray());
                publicKeys.put(transformPublicKey(parsedMessage, serializedPath, pathInt));
            }
            result.put("success", true);
            result.put("payload", publicKeys);
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorCallback("There was an error while fetching public keys.");
        }

        return result;
    }

    private JSONObject transformPublicKey(PublicKey publicKey, String serializedPath, Integer[] path) throws JSONException {
        JSONArray pathJson = new JSONArray();
        for (Integer p : path) {
            pathJson.put(p);
        }
        JSONObject jsonPublicKey = new JSONObject()
                .put("path", pathJson)
                .put("serializedPath", serializedPath)
                .put("xpub", publicKey.getXpub())
                .put("chainCode" , byteStringToHexString(publicKey.getNode().getChainCode()))
                .put("childNum", publicKey.getNode().getChildNum())
                .put("publicKey", byteStringToHexString(publicKey.getNode().getPublicKey()))
                .put("fingerprint", publicKey.getNode().getFingerprint())
                .put("depth", publicKey.getNode().getDepth());
        if ((path.length > 0) && (path[0] == -2147483599L)) {
            jsonPublicKey.put("xpubSegwit", convertXpubToYpub(publicKey.getXpub()));
        }
        return jsonPublicKey;
    }

    private String convertXpubToYpub(String xpub) {
        byte[] xpubBytes = Base58Check.base58ToBytes(xpub);
        xpubBytes = Arrays.copyOfRange(xpubBytes, 4, xpubBytes.length);

        byte[] ypubBytes = new byte[xpubBytes.length + 4];
        System.arraycopy(getPrefixBytes(xpub.startsWith("xpub")), 0, ypubBytes, 0, 4);
        System.arraycopy(xpubBytes, 0, ypubBytes, 4, xpubBytes.length);
        return Base58Check.bytesToBase58(ypubBytes);
    }

    private byte[] getPrefixBytes(boolean isMainNet) {
        String prefix = isMainNet ? "049d7cb2" : "044a5262";

        if (isMainNet && (mainnetPrefixBytes.length != 0)) {
            return mainnetPrefixBytes;
        } else if (!isMainNet && (testnetPrefixBytes.length != 0)) {
            return testnetPrefixBytes;
        }

        byte[] val = new byte[4];
        for (int i = 0; i < val.length; i++) {
            int index = i * 2;
            int j = Integer.parseInt(prefix.substring(index, index + 2), 16);
            val[i] = (byte) j;
        }

        if (isMainNet) {
            mainnetPrefixBytes = val;
        } else {
            testnetPrefixBytes = val;
        }

        return val;
    }

    private void showPinDialog(OnPinListener onSuccessCallback) {
        this.pin = "";
        AtomicBoolean positiveButtonClicked = new AtomicBoolean(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getActivity());
        LayoutInflater inflater = cordova.getActivity().getLayoutInflater();

        builder.setView(inflater.inflate(r.getIdentifier("enter_pin_dialog", "layout", packageNme), null))
            .setPositiveButton("Confirm", (dialog, id) -> {
                positiveButtonClicked.set(true);
                Message response = trezorManager.sendMessage(PinMatrixAck.newBuilder().setPin(pin).build());
                if (!(response instanceof Failure)) {
                    onSuccessCallback.onSuccess(response);
                } else {
                    try {
                        sendErrorCallback(Failure.parseFrom(response.toByteArray()).getMessage());
                    } catch (InvalidProtocolBufferException e) {
                        e.printStackTrace();
                    }
                }
            })
            .setNegativeButton("Cancel", (dialog, id) -> sendErrorCallback("Access denied!"))
            .setOnDismissListener(dialog -> {
                if (!positiveButtonClicked.get()) {
                    sendErrorCallback("Access denied!");
                }
            });
        AlertDialog pinDialog = builder.create();
        pinDialog.setOnShowListener(dialog -> {
            TextView pinStars = pinDialog.findViewById(r.getIdentifier("txt_pin_stars", "id", packageNme));
            pinDialog.findViewById(r.getIdentifier("button1", "id", packageNme)).setOnClickListener(v -> updatePin(1, pinStars));
            pinDialog.findViewById(r.getIdentifier("button2", "id", packageNme)).setOnClickListener(v -> updatePin(2, pinStars));
            pinDialog.findViewById(r.getIdentifier("button3", "id", packageNme)).setOnClickListener(v -> updatePin(3, pinStars));
            pinDialog.findViewById(r.getIdentifier("button4", "id", packageNme)).setOnClickListener(v -> updatePin(4, pinStars));
            pinDialog.findViewById(r.getIdentifier("button5", "id", packageNme)).setOnClickListener(v -> updatePin(5, pinStars));
            pinDialog.findViewById(r.getIdentifier("button6", "id", packageNme)).setOnClickListener(v -> updatePin(6, pinStars));
            pinDialog.findViewById(r.getIdentifier("button7", "id", packageNme)).setOnClickListener(v -> updatePin(7, pinStars));
            pinDialog.findViewById(r.getIdentifier("button8", "id", packageNme)).setOnClickListener(v -> updatePin(8, pinStars));
            pinDialog.findViewById(r.getIdentifier("button9", "id", packageNme)).setOnClickListener(v -> updatePin(9, pinStars));
            pinDialog.findViewById(r.getIdentifier("btn_backspace", "id", packageNme)).setOnClickListener(v -> updatePin(-1, pinStars));
        });
        pinDialog.show();
    }

    private void updatePin(int digit, TextView pinStars) {
        if (digit == -1) {
            if (pin.length() > 0) {
                pin = pin.substring(0, pin.length() - 1);
            }
        } else {
            pin = pin + digit;
        }
        pinStars.setText(pin);
    }

    private void showPassphraseDialog(OnPassphraseListener onSuccessCallback) {
        this.passphrase = "";
        AtomicBoolean positiveButtonClicked = new AtomicBoolean(false);
        AlertDialog.Builder builder = new AlertDialog.Builder(cordova.getActivity());
        LayoutInflater inflater = cordova.getActivity().getLayoutInflater();

        builder.setView(inflater.inflate(r.getIdentifier("enter_passphrase_dialog", "layout", packageNme), null))
                .setPositiveButton("Confirm", (dialog, id) -> {
                    positiveButtonClicked.set(true);
                    Message passphraseMessage = trezorManager.sendMessage(PassphraseAck.newBuilder().setPassphrase(passphrase).build());
                    onSuccessCallback.onSuccess(passphraseMessage);
                })
                .setNegativeButton("Cancel", (dialog, id) ->
                        sendErrorCallback("No wallet selected!")
                )
                .setOnDismissListener(dialog -> {
                    if (!positiveButtonClicked.get()) {
                        sendErrorCallback("No wallet selected!");
                    }
                });
        AlertDialog passphraseDialog = builder.create();
        passphraseDialog.setOnShowListener(dialog -> {
            TextView errorText = passphraseDialog.findViewById(r.getIdentifier("passphrase_error_text", "id", packageNme));
            EditText passphraseEditText = passphraseDialog.findViewById(r.getIdentifier("passphrase", "id", packageNme));
            EditText confirmEditText = passphraseDialog.findViewById(r.getIdentifier("confirm_passphrase", "id", packageNme));

            TextWatcher textWatcher = new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    checkPassphrase(passphraseEditText.getText().toString(), confirmEditText.getText().toString(), errorText, passphraseDialog);
                }

                @Override
                public void afterTextChanged(Editable s) { }
            };
            passphraseEditText.addTextChangedListener(textWatcher);
            confirmEditText.addTextChangedListener(textWatcher);
        });
        passphraseDialog.show();
    }

    private void checkPassphrase(String pass, String confirmPass, TextView errorText, AlertDialog dialog) {
        if (!pass.equals(confirmPass)) {
            errorText.setVisibility(View.VISIBLE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        } else {
            errorText.setVisibility(View.INVISIBLE);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
        }
        this.passphrase = pass;
    }

    private String byteStringToHexString(ByteString bytes) {
        StringBuilder result = new StringBuilder();
        for (byte aByte : bytes) {
            int decimal = (int) aByte & 0xff;
            // get last 8 bits
            String hex = Integer.toHexString(decimal);
            if (hex.length() % 2 == 1) {
                hex = "0" + hex;
            }
            result.append(hex);
        }
        return result.toString();
    }

    private Integer[] getHDPath(String path) throws Exception {
        String[] parts = path.toLowerCase().split("/");
        if (!parts[0].equals("m")) throw new Exception("Not a valid path");
        ArrayList<Integer> normalizedPath = new ArrayList<>();
        for (String part : parts) {
            if (!part.equals("m") && !part.equals("")) {
                boolean hardened = false;
                if (part.substring(part.length() - 1).equals("'")) {
                    hardened = true;
                    part = part.substring(0, part.length() - 1);
                }
                int n = Integer.parseInt(part);
                if (n < 0) {
                    throw new Exception("Path cannot contain negative values");
                }
                if (hardened) {
                    n = n | 0x80000000;
                }
                normalizedPath.add(n);
            }
        }
        return normalizedPath.toArray(new Integer[0]);
    }

    private Integer[] convertPathToArray(String path) throws Exception {
        try {
            return getHDPath(path);
        } catch (Exception e) {
            throw new Exception("Not a valid path");
        }
    }

    private void sendErrorCallback(String message) {
        try {
            trezorManager.sendMessage(Cancel.newBuilder().build());
        } catch (Exception ignored) {}

        try {
            JSONObject result = new JSONObject();
            result.put("success", false);
            result.put("payload", new JSONObject().put("error", message));
            callbackContext.success(result);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
