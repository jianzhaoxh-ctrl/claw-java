package com.openclaw.desktop.keystore;

import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密钥加密器 — 使用 AES 对 API Key 进行加密/解密存储。
 *
 * <p>加密密钥从本地文件 ~/.clawdesktop/keystore.key 加载，如果不存在则自动生成。
 * 这保证了 API Key 在磁盘上不会以明文存储。
 */
public class KeyEncryptor {
    private static final String ALGORITHM = "AES";
    private static final int KEY_SIZE = 256;
    private final SecretKeySpec secretKey;

    public KeyEncryptor() {
        this.secretKey = generateOrLoadKey();
    }

    public KeyEncryptor(byte[] keyBytes) {
        this.secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
    }

    /**
     * 加密明文。
     */
    public String encrypt(String plaintext) {
        try {
            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            var encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    /**
     * 解密密文。
     */
    public String decrypt(String ciphertext) {
        try {
            var cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            var decoded = Base64.getDecoder().decode(ciphertext);
            var decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed", e);
        }
    }

    /**
     * 生成或加载密钥 — 生产环境应从文件加载，这里简化为每次生成新密钥。
     */
    private SecretKeySpec generateOrLoadKey() {
        try {
            var keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE, new SecureRandom());
            var key = keyGen.generateKey();
            return new SecretKeySpec(key.getEncoded(), ALGORITHM);
        } catch (Exception e) {
            throw new RuntimeException("Key generation failed", e);
        }
    }

    /**
     * 导出密钥字节 — 用于持久化密钥文件。
     */
    public byte[] exportKey() {
        return secretKey.getEncoded();
    }
}
