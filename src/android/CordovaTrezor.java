package io.thisapp.cordova.plugin;

import android.app.AlertDialog;
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

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import io.cordova.hellocordova.R;
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
            JSONArray pathsArray = paths.getJSONArray("paths");
            JSONArray publicKeys = new JSONArray();
            for (int i = 0; i < pathsArray.length(); i++) {
                JSONArray path = pathsArray.getJSONArray(i);
                GetPublicKey.Builder messageBuilder = GetPublicKey.newBuilder();

                for (int j = 0; j < path.length(); j++) {
                    messageBuilder.addAddressN((int) path.getLong(j));
                }

                Message response = trezorManager.sendMessage(messageBuilder.build());
                PublicKey parsedMessage = PublicKey.parseFrom(response.toByteArray());
                publicKeys.put(transformPublicKey(parsedMessage, path));
            }
            result.put("success", true);
            result.put("payload", publicKeys);
        } catch (Exception e) {
            e.printStackTrace();
            sendErrorCallback("There was an error while fetching public keys.");
        }

        return result;
    }

    private JSONObject transformPublicKey(PublicKey publicKey, JSONArray path) throws JSONException {
        JSONObject jsonPublicKey = new JSONObject()
                .put("path", path)
                .put("xpub", publicKey.getXpub())
                .put("chainCode" , byteStringToHexString(publicKey.getNode().getChainCode()))
                .put("childNum", publicKey.getNode().getChildNum())
                .put("publicKey", byteStringToHexString(publicKey.getNode().getPublicKey()))
                .put("fingerprint", publicKey.getNode().getFingerprint())
                .put("depth", publicKey.getNode().getDepth());
        if ((path.length() > 0) && (path.getLong(0) == 2147483697L)) {
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

        builder.setView(inflater.inflate(R.layout.enter_pin_dialog, null))
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
            TextView pinStars = pinDialog.findViewById(R.id.txt_pin_stars);
            pinDialog.findViewById(R.id.button1).setOnClickListener(v -> updatePin(1, pinStars));
            pinDialog.findViewById(R.id.button2).setOnClickListener(v -> updatePin(2, pinStars));
            pinDialog.findViewById(R.id.button3).setOnClickListener(v -> updatePin(3, pinStars));
            pinDialog.findViewById(R.id.button4).setOnClickListener(v -> updatePin(4, pinStars));
            pinDialog.findViewById(R.id.button5).setOnClickListener(v -> updatePin(5, pinStars));
            pinDialog.findViewById(R.id.button6).setOnClickListener(v -> updatePin(6, pinStars));
            pinDialog.findViewById(R.id.button7).setOnClickListener(v -> updatePin(7, pinStars));
            pinDialog.findViewById(R.id.button8).setOnClickListener(v -> updatePin(8, pinStars));
            pinDialog.findViewById(R.id.button9).setOnClickListener(v -> updatePin(9, pinStars));
            pinDialog.findViewById(R.id.btn_backspace).setOnClickListener(v -> updatePin(-1, pinStars));
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

        builder.setView(inflater.inflate(R.layout.enter_passphrase_dialog, null))
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
            TextView errorText = passphraseDialog.findViewById(R.id.passphrase_error_text);
            EditText passphraseEditText = passphraseDialog.findViewById(R.id.passphrase);
            EditText confirmEditText = passphraseDialog.findViewById(R.id.confirm_passphrase);

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
