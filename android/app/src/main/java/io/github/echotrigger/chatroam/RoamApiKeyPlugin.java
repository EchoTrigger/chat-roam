package io.github.echotrigger.chatroam;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;
import android.util.Base64;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONException;
import org.json.JSONObject;

@CapacitorPlugin(name = "RoamApiKey")
public final class RoamApiKeyPlugin extends Plugin {
    private static final String PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "chat-roam.api-key.v1";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int AUTHENTICATION_TAG_BITS = 128;
    private static final Object FILE_LOCK = new Object();

    private AtomicFile storage;

    @Override
    public void load() {
        storage = new AtomicFile(new File(getContext().getNoBackupFilesDir(), "roam-api-key.json"));
    }

    @PluginMethod
    public void get(PluginCall call) {
        synchronized (FILE_LOCK) {
            try {
                String value = read();
                call.resolve(new JSObject().put("value", value == null ? JSONObject.NULL : value));
            } catch (GeneralSecurityException | IOException | JSONException | IllegalArgumentException error) {
                call.reject("Could not read the saved API key", "API_KEY_READ_FAILED");
            }
        }
    }

    @PluginMethod
    public void set(PluginCall call) {
        synchronized (FILE_LOCK) {
            try {
                Object value = call.getData().get("value");
                if (value == JSONObject.NULL) {
                    storage.delete();
                    if (storage.getBaseFile().exists()) {
                        throw new IOException("Could not clear the stored credential");
                    }
                } else if (value instanceof String key && !key.isEmpty()) {
                    save(key);
                } else {
                    call.reject("Expected an API key", "INVALID_API_KEY");
                    return;
                }
                call.resolve();
            } catch (GeneralSecurityException | IOException | JSONException | IllegalArgumentException error) {
                call.reject("Could not save the API key", "API_KEY_SAVE_FAILED");
            }
        }
    }

    private String read() throws IOException, JSONException, GeneralSecurityException {
        byte[] payload;
        try {
            payload = storage.readFully();
        } catch (FileNotFoundException error) {
            if (storage.getBaseFile().exists()) {
                throw error;
            }
            return null;
        }
        JSONObject saved = new JSONObject(new String(payload, StandardCharsets.UTF_8));
        if (saved.getInt("version") != 1) {
            throw new IOException("Unsupported credential storage version");
        }
        Cipher cipher = Cipher.getInstance(CIPHER);
        byte[] iv = Base64.decode(saved.getString("iv"), Base64.NO_WRAP);
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(false), new GCMParameterSpec(AUTHENTICATION_TAG_BITS, iv));
        byte[] ciphertext = Base64.decode(saved.getString("ciphertext"), Base64.NO_WRAP);
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    private void save(String value) throws IOException, JSONException, GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey(true));
        byte[] ciphertext = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        JSONObject saved = new JSONObject()
            .put("version", 1)
            .put("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
            .put("ciphertext", Base64.encodeToString(ciphertext, Base64.NO_WRAP));
        FileOutputStream output = storage.startWrite();
        try {
            output.write(saved.toString().getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
            storage.finishWrite(output);
        } catch (IOException error) {
            storage.failWrite(output);
            throw error;
        }
        if (!value.equals(read())) {
            throw new IOException("Stored credential verification failed");
        }
    }

    private static SecretKey encryptionKey(boolean create) throws GeneralSecurityException, IOException {
        KeyStore store = KeyStore.getInstance(PROVIDER);
        store.load(null);
        Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey secretKey) {
            return secretKey;
        }
        if (!create || existing != null) {
            throw new GeneralSecurityException("Credential encryption key is unavailable");
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER);
        generator.init(
            new KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        );
        return generator.generateKey();
    }
}
