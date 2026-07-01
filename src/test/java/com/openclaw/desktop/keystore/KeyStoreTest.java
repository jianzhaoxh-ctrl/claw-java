package com.openclaw.desktop.keystore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 密钥管理单元测试。
 */
class KeyStoreTest {
    private KeyStore keyStore;
    private KeyEncryptor encryptor;

    @BeforeEach
    void setup() {
        var tempFile = Path.of(System.getProperty("java.io.tmpdir"), "claw-test-keystore.conf");
        try { java.nio.file.Files.deleteIfExists(tempFile); } catch (Exception e) {}
        keyStore = new KeyStore(tempFile);
        encryptor = new KeyEncryptor();
    }

    @Test
    void testPutAndGet() {
        var entry = ApiKeyEntry.of("openai", "sk-test-key-123");
        keyStore.put(entry);

        var retrieved = keyStore.get("openai");
        assertTrue(retrieved.isPresent());
        assertEquals("sk-test-key-123", retrieved.get().apiKey());
        assertEquals("openai", retrieved.get().providerId());
    }

    @Test
    void testGetApiKey() {
        keyStore.put(ApiKeyEntry.of("anthropic", "sk-ant-test"));
        var apiKey = keyStore.getApiKey("anthropic");
        assertTrue(apiKey.isPresent());
        assertEquals("sk-ant-test", apiKey.get());
    }

    @Test
    void testGetApiKeyDisabled() {
        var entry = ApiKeyEntry.of("deepseek", "sk-ds-test").withDisabled();
        keyStore.put(entry);
        var apiKey = keyStore.getApiKey("deepseek");
        assertFalse(apiKey.isPresent());
    }

    @Test
    void testGetApiKeyNotFound() {
        var apiKey = keyStore.getApiKey("nonexistent");
        assertFalse(apiKey.isPresent());
    }

    @Test
    void testRemove() {
        keyStore.put(ApiKeyEntry.of("openai", "sk-test"));
        keyStore.remove("openai");
        assertFalse(keyStore.hasKey("openai"));
    }

    @Test
    void testEnableDisable() {
        keyStore.put(ApiKeyEntry.of("test-prov", "sk-abc").withDisabled());
        assertFalse(keyStore.getApiKey("test-prov").isPresent());

        keyStore.enable("test-prov");
        assertTrue(keyStore.getApiKey("test-prov").isPresent());

        keyStore.disable("test-prov");
        assertFalse(keyStore.getApiKey("test-prov").isPresent());
    }

    @Test
    void testMarkVerified() {
        keyStore.put(ApiKeyEntry.of("openai", "sk-test"));
        assertFalse(keyStore.get("openai").get().verified());

        keyStore.markVerified("openai");
        assertTrue(keyStore.get("openai").get().verified());
    }

    @Test
    void testListAll() {
        keyStore.put(ApiKeyEntry.of("openai", "sk-1"));
        keyStore.put(ApiKeyEntry.of("anthropic", "sk-2"));
        keyStore.put(ApiKeyEntry.of("deepseek", "sk-3"));

        assertEquals(3, keyStore.listAll().size());
    }

    @Test
    void testEncryptionRoundtrip() {
        var plaintext = "sk-super-secret-api-key-12345";
        var encrypted = encryptor.encrypt(plaintext);
        var decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void testEncryptionDifferentPlaintexts() {
        var key1 = encryptor.encrypt("key-one");
        var key2 = encryptor.encrypt("key-two");
        // 加密后应该不同
        assertNotEquals(key1, key2);
        // 但都能正确解密
        assertEquals("key-one", encryptor.decrypt(key1));
        assertEquals("key-two", encryptor.decrypt(key2));
    }

    @Test
    void testApiKeyEntryFactory() {
        var simple = ApiKeyEntry.of("openai", "sk-test");
        assertEquals("openai", simple.providerId());
        assertTrue(simple.enabled());
        assertFalse(simple.verified());

        var withUrl = ApiKeyEntry.of("openai", "sk-test", "https://custom.api.com");
        assertEquals("https://custom.api.com", withUrl.baseUrl());
    }

    @Test
    void testApiKeyEntryStateChanges() {
        var entry = ApiKeyEntry.of("openai", "sk-test");
        assertFalse(entry.verified());

        assertFalse(entry.verified());

        var verifiedEntry = entry.withVerified();
        assertTrue(verifiedEntry.verified());

        var disabled = entry.withDisabled();
        assertFalse(disabled.enabled());

        var enabled = disabled.withEnabled();
        assertTrue(enabled.enabled());
    }

    @Test
    void testKeyManagerRegister() {
        var manager = new KeyManager(keyStore);
        var result = manager.registerKey("openai", "sk-test-123", null).block();
        assertNotNull(result);
        assertEquals("openai", result.providerId());
        assertTrue(keyStore.hasKey("openai"));
    }

    @Test
    void testKeyManagerRemove() {
        var manager = new KeyManager(keyStore);
        manager.registerKey("openai", "sk-test", null).block();
        assertTrue(keyStore.hasKey("openai"));

        manager.removeKey("openai").block();
        assertFalse(keyStore.hasKey("openai"));
    }

    @Test
    void testKeyManagerRotate() {
        var manager = new KeyManager(keyStore);
        manager.registerKey("openai", "sk-old-key", null).block();

        var rotated = manager.rotateKey("openai", "sk-new-key", "https://custom.api.com").block();
        assertNotNull(rotated);
        assertEquals("sk-new-key", rotated.apiKey());
        assertEquals("https://custom.api.com", rotated.baseUrl());
    }
}
