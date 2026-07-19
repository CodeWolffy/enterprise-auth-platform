package com.enterprise.auth.platform.modules.system.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 对包含一次性凭据的 Outbox 载荷进行应用层保护。
 *
 * <p>密文包含随机 salt/IV，事件类型作为 AES-GCM AAD，避免密文被跨事件类型替换。
 */
@Service
public class OutboxPayloadProtectionService {

    private static final String PREFIX = "{enc}outbox:v1:";
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int MIN_SECRET_KEY_LENGTH = 32;

    private final OutboxProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public OutboxPayloadProtectionService(OutboxProperties properties, Environment environment) {
        this.properties = properties;
        if (environment.acceptsProfiles(Profiles.of("prod", "staging"))) {
            requireConfiguredKey();
        }
    }

    public String protect(String eventType, String payloadJson) {
        if (!isSensitive(eventType) || !StringUtils.hasText(payloadJson) || isProtected(payloadJson)) {
            return payloadJson;
        }
        String secretKey = requireConfiguredKey();
        try {
            byte[] salt = randomBytes(SALT_BYTES);
            byte[] iv = randomBytes(IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(secretKey, salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(eventType.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(payloadJson.getBytes(StandardCharsets.UTF_8));
            return PREFIX + encode(salt) + ":" + encode(iv) + ":" + encode(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("sensitive outbox payload encryption failed", ex);
        }
    }

    public String reveal(String eventType, String storedPayload) {
        if (!isProtected(storedPayload)) {
            // 兼容升级前已入库的事件；投递完成或进入 DEAD 后会被清理。
            return storedPayload;
        }
        String secretKey = requireConfiguredKey();
        try {
            String[] parts = storedPayload.substring(PREFIX.length()).split(":", 3);
            if (parts.length != 3) {
                throw new IllegalArgumentException("invalid encrypted outbox payload format");
            }
            byte[] salt = decode(parts[0]);
            byte[] iv = decode(parts[1]);
            byte[] encrypted = decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(secretKey, salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(eventType.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("sensitive outbox payload decryption failed", ex);
        }
    }

    public boolean isProtected(String payload) {
        return payload != null && payload.startsWith(PREFIX);
    }

    private boolean isSensitive(String eventType) {
        return OutboxWriter.TYPE_PASSWORD_RESET_MAIL.equals(eventType);
    }

    private String requireConfiguredKey() {
        String secretKey = properties.resolvedPayloadSecretKey();
        if (secretKey.length() < MIN_SECRET_KEY_LENGTH) {
            throw new IllegalStateException(
                    "APP_OUTBOX_PAYLOAD_SECRET_KEY must be configured with at least 32 characters"
            );
        }
        return secretKey;
    }

    private SecretKeySpec deriveKey(String secretKey, byte[] salt) throws GeneralSecurityException {
        char[] password = secretKey.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, KEY_BITS);
        try {
            byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
            return new SecretKeySpec(key, "AES");
        } finally {
            spec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }

    private byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        secureRandom.nextBytes(bytes);
        return bytes;
    }

    private String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private byte[] decode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }
}
