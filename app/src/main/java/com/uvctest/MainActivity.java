package com.uvctest;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    private static final int VENDOR_REQUEST_GET = 0x01;
    private static final int VENDOR_REQUEST_SET = 0x09;

    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbDeviceConnection connection;
    private UsbInterface usbInterface;
    private boolean isConnected = false;

    private EditText editVid, editPid, editUnitId, editCs;
    private EditText editInput;
    private TextView textResult;
    private RadioButton radioHex, radioString;
    private Button btnConnect, btnGet, btnSet;

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getIntent().getAction();
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                appendResult("[USB] Device attached\n");
                autoConnect();
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                appendResult("[USB] Device detached\n");
                isConnected = false;
                updateButtonState();
            } else if (UsbManager.ACTION_USB_PERMISSION_GRANTED.equals(action)) {
                appendResult("[USB] Permission granted\n");
                openDevice();
            } else if (UsbManager.ACTION_USB_PERMISSION_DENIED.equals(action)) {
                appendResult("[USB] Permission denied\n");
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind views
        editVid = findViewById(R.id.editVid);
        editPid = findViewById(R.id.editPid);
        editUnitId = findViewById(R.id.editUnitId);
        editCs = findViewById(R.id.editCs);
        editInput = findViewById(R.id.editInput);
        textResult = findViewById(R.id.textResult);
        radioHex = findViewById(R.id.radioHex);
        radioString = findViewById(R.id.radioString);
        btnConnect = findViewById(R.id.btnConnect);
        btnGet = findViewById(R.id.btnGet);
        btnSet = findViewById(R.id.btnSet);

        btnConnect.setOnClickListener(v -> connect());
        btnGet.setOnClickListener(v -> performGet());
        btnSet.setOnClickListener(v -> performSet());

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_PERMISSION_GRANTED);
        filter.addAction(UsbManager.ACTION_USB_PERMISSION_DENIED);
        registerReceiver(usbReceiver, filter);

        updateButtonState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        closeDevice();
    }

    private int parseHex(String hex) {
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void autoConnect() {
        runOnUiThread(this::connect);
    }

    private void connect() {
        closeDevice();

        String vidStr = editVid.getText().toString().trim();
        String pidStr = editPid.getText().toString().trim();

        if (vidStr.isEmpty() || pidStr.isEmpty()) {
            appendResult("[ERROR] Please enter VID and PID\n");
            return;
        }

        int vid = parseHex(vidStr);
        int pid = parseHex(pidStr);

        appendResult("[USB] Searching for VID=" + String.format("%04x", vid) +
                " PID=" + String.format("%04x", pid) + "\n");

        HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
        usbDevice = null;

        for (UsbDevice device : deviceList.values()) {
            appendResult("[USB] Found: " + device.getDeviceName() +
                    " VID=" + String.format("%04x", device.getVendorId()) +
                    " PID=" + String.format("%04x", device.getProductId()) + "\n");

            if (device.getVendorId() == vid && device.getProductId() == pid) {
                usbDevice = device;
                appendResult("[USB] Target device found!\n");
                break;
            }
        }

        if (usbDevice == null) {
            appendResult("[ERROR] Device not found. Check VID/PID and try again.\n");
            return;
        }

        if (usbManager.hasPermission(usbDevice)) {
            openDevice();
        } else {
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, 0,
                    new Intent(UsbManager.ACTION_USB_PERMISSION_GRANTED), 0);
            usbManager.requestPermission(usbDevice, pendingIntent);
        }
    }

    private void openDevice() {
        if (usbDevice == null) return;

        usbInterface = usbDevice.getInterface(0);
        if (usbInterface == null) {
            appendResult("[ERROR] No interface found\n");
            return;
        }

        connection = usbManager.openDevice(usbDevice);
        if (connection == null) {
            appendResult("[ERROR] Cannot open device\n");
            return;
        }

        if (!connection.claimInterface(usbInterface, true)) {
            appendResult("[ERROR] Cannot claim interface\n");
            connection.close();
            return;
        }

        isConnected = true;
        updateButtonState();
        appendResult("[OK] Device connected successfully\n");
    }

    private void closeDevice() {
        if (connection != null) {
            if (usbInterface != null) {
                connection.releaseInterface(usbInterface);
            }
            connection.close();
            connection = null;
        }
        isConnected = false;
        updateButtonState();
    }

    private void updateButtonState() {
        runOnUiThread(() -> {
            btnConnect.setText(isConnected ? "Disconnect" : "Connect");
            btnGet.setEnabled(isConnected);
            btnSet.setEnabled(isConnected);
        });
    }

    private int getUnitId() {
        try {
            return Integer.parseInt(editUnitId.getText().toString().trim());
        } catch (NumberFormatException e) {
            return 12;
        }
    }

    private int getCs() {
        try {
            return Integer.parseInt(editCs.getText().toString().trim());
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private byte[] hexStringToBytes(String hex) {
        hex = hex.replaceAll("\\s", "");
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return bytes;
    }

    private String bytesToHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x ", b & 0xff));
        }
        return sb.toString().trim();
    }

    private void performGet() {
        if (connection == null) {
            appendResult("[ERROR] Device not connected\n");
            return;
        }

        String input = editInput.getText().toString().trim();
        byte[] data;

        if (input.isEmpty()) {
            // No input = just query, use 1 byte
            data = new byte[1];
            data[0] = 0x00;
        } else if (radioHex.isChecked()) {
            data = hexStringToBytes(input);
        } else {
            data = input.getBytes();
        }

        int unitId = getUnitId();
        int cs = getCs();

        appendResult("[GET] UnitID=" + unitId + " CS=" + cs + " len=" + data.length + "\n");
        appendResult("[GET] Sending: " + bytesToHexString(data) + "\n");

        // UVC Extension Unit control transfer
        // bmRequestType: 0x01 (Vendor, Recipient=Interface)
        // bRequest: GET (0x01)
        // wValue: CS (control selector) << 8
        // wIndex: Unit ID
        int requestType = 0x01;
        int request = VENDOR_REQUEST_GET;
        int wValue = (cs << 8) | 0x00;
        int wIndex = unitId;

        byte[] buffer = new byte[data.length];
        int ret = connection.controlTransfer(requestType, request, wValue, wIndex,
                buffer, data.length, 1000);

        if (ret >= 0) {
            appendResult("[GET] Success, received " + ret + " bytes\n");
            appendResult("[GET] Data: " + bytesToHexString(buffer) + "\n");

            if (radioString.isChecked()) {
                String str = new String(buffer).trim();
                appendResult("[GET] String: " + str + "\n");
            }
        } else {
            appendResult("[GET] Failed: " + ret + "\n");
        }
    }

    private void performSet() {
        if (connection == null) {
            appendResult("[ERROR] Device not connected\n");
            return;
        }

        String input = editInput.getText().toString().trim();
        if (input.isEmpty()) {
            appendResult("[ERROR] Please enter data\n");
            return;
        }

        byte[] data;
        if (radioHex.isChecked()) {
            data = hexStringToBytes(input);
        } else {
            data = input.getBytes();
        }

        int unitId = getUnitId();
        int cs = getCs();

        appendResult("[SET] UnitID=" + unitId + " CS=" + cs + " len=" + data.length + "\n");
        appendResult("[SET] Sending: " + bytesToHexString(data) + "\n");

        int requestType = 0x01;
        int request = VENDOR_REQUEST_SET;
        int wValue = (cs << 8) | 0x00;
        int wIndex = unitId;

        int ret = connection.controlTransfer(requestType, request, wValue, wIndex,
                data, data.length, 1000);

        if (ret >= 0) {
            appendResult("[SET] Success, sent " + ret + " bytes\n");
        } else {
            appendResult("[SET] Failed: " + ret + "\n");
        }
    }

    private void appendResult(String text) {
        runOnUiThread(() -> {
            textResult.append(text);
            int scrollAmount = textResult.getLayout().getLineTop(textResult.getLineCount())
                    - textResult.getHeight();
            if (scrollAmount > 0) {
                textResult.scrollTo(0, scrollAmount);
            } else {
                textResult.scrollTo(0, 0);
            }
        });
    }
}
