package com.enterprise.auth.platform.modules.system.application;

import com.enterprise.auth.platform.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MailChannelSecretService {

    private static final String PREFIX = "{enc}v1:";
    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;
    private static final int KEY_BITS = 256;
    private static final int GCM_TAG_BITS = 128;
    private static final int PBKDF2_ITERATIONS = 65536;

    private final MailChannelProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();

    public MailChannelSecretService(MailChannelProperties properties) {
        this.properties = properties;
    }

    public boolean canProtect() {
        return StringUtils.hasText(properties.resolvedSecretKey());
    }

    public boolean isProtected(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    public String protect(String rawSecret) {
        if (!StringUtils.hasText(rawSecret) || isProtected(rawSecret)) {
            return rawSecret;
        }
        if (!canProtect()) {
            throw new BusinessException("MAIL_SECRET_KEY_REQUIRED", "保存 SMTP 密码前必须配置 APP_MAIL_SECRET_KEY");
        }
        try {
            byte[] salt = randomBytes(SALT_BYTES);
            byte[] iv = randomBytes(IV_BYTES);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(rawSecret.getBytes(StandardCharsets.UTF_8));
            return PREFIX + encode(salt) + ":" + encode(iv) + ":" + encode(encrypted);
        } catch (GeneralSecurityException ex) {
            throw new BusinessException("MAIL_SECRET_PROTECT_FAILED", "SMTP 密码保护失败");
        }
    }

    public String reveal(String storedSecret) {
        if (!StringUtils.hasText(storedSecret) || !isProtected(storedSecret)) {
            return storedSecret;
        }
        if (!canProtect()) {
            throw new BusinessException("MAIL_SECRET_KEY_REQUIRED", "SMTP 密码已加密，当前环境缺少 APP_MAIL_SECRET_KEY");
        }
        try {
            String[] parts = storedSecret.substring(PREFIX.length()).split(":", 3);
            if (parts.length != 3) {
                throw new BusinessException("MAIL_SECRET_INVALID", "SMTP 密码密文格式无效");
            }
            byte[] salt = decode(parts[0]);
            byte[] iv = decode(parts[1]);
            byte[] encrypted = decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(salt), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("MAIL_SECRET_REVEAL_FAILED", "SMTP 密码解密失败，请确认 APP_MAIL_SECRET_KEY 是否正确");
        }
    }

    private SecretKeySpec deriveKey(byte[] salt) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(
                properties.resolvedSecretKey().toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                KEY_BITS
        );
        byte[] key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec)
                .getEncoded();
        return new SecretKeySpec(key, "AES");
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