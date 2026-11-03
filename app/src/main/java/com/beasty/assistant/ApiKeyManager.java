package com.beasty.assistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class ApiKeyManager {

    private static final String PREFS = "beasty_secure_keys";
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String ALIAS = "BeastyApiEncryptionKey";

    private final SharedPreferences preferences;

    public ApiKeyManager(Context context) {
        preferences = context.getSharedPreferences(
            PREFS, Context.MODE_PRIVATE
        );
    }

    private SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(ALIAS)) {
            return ((SecretKey) keyStore.getKey(ALIAS, null));
        }

        KeyGenerator generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        );

        generator.init(new KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT
                | KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(
                KeyProperties.ENCRYPTION_PADDING_NONE
            )
            .setRandomizedEncryptionRequired(true)
            .build()
        );

        return generator.generateKey();
    }

    public synchronized boolean saveKey(
        String provider, String apiKey
    ) {
        if (provider == null || apiKey == null
            || apiKey.trim().isEmpty()) {
            return false;
        }

        try {
            String name = provider.trim().toLowerCase();

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());

            byte[] encrypted = cipher.doFinal(
                apiKey.trim().getBytes(StandardCharsets.UTF_8)
            );

            String iv = Base64.encodeToString(
                cipher.getIV(), Base64.NO_WRAP
            );

            String data = Base64.encodeToString(
                encrypted, Base64.NO_WRAP
            );

            preferences.edit()
                .putString(name + "_iv", iv)
                .putString(name + "_data", data)
                .apply();

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    public synchronized String getKey(String provider) {
        if (provider == null) return "";

        try {
            String name = provider.trim().toLowerCase();

            String ivValue = preferences.getString(
                name + "_iv", ""
            );

            String dataValue = preferences.getString(
                name + "_data", ""
            );

            if (ivValue.isEmpty() || dataValue.isEmpty()) {
                return "";
            }

            byte[] iv = Base64.decode(ivValue, Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(
                dataValue, Base64.NO_WRAP
            );

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            cipher.init(
                Cipher.DECRYPT_MODE,
                getSecretKey(),
                new GCMParameterSpec(128, iv)
            );

            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(
                decrypted, StandardCharsets.UTF_8
            );

        } catch (Exception e) {
            return "";
        }
    }

    public synchronized void deleteKey(String provider) {
        if (provider == null) return;

        String name = provider.trim().toLowerCase();

        preferences.edit()
            .remove(name + "_iv")
            .remove(name + "_data")
            .apply();
    }

    public synchronized boolean hasKey(String provider) {
        return !getKey(provider).isEmpty();
    }
}
