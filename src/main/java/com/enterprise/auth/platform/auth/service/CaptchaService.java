package com.enterprise.auth.platform.auth.service;

import com.enterprise.auth.platform.common.exception.BusinessException;
import com.enterprise.auth.platform.config.SecurityRedisProperties;
import com.enterprise.auth.platform.config.SecurityProperties;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.UUID;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CaptchaService {

    private static final char[] CAPTCHA_CHARS = "346789ACDEFGHJKMNPQRTUVWXY".toCharArray();
    private static final String[] CAPTCHA_COLORS = {
            "#0f172a",
            "#1d4ed8",
            "#0f766e",
            "#7c2d12",
            "#6d28d9"
    };
    private static final int CAPTCHA_WIDTH = 144;
    private static final int CAPTCHA_HEIGHT = 52;
    private static final int CAPTCHA_LENGTH = 5;
    private static final int DECORATION_CIRCLE_COUNT = 7;
    private static final int DECORATION_CURVE_COUNT = 5;
    private static final int DECORATION_DOT_COUNT = 34;
    private static final int OCCLUSION_BLOCK_COUNT = 5;
    private static final int SVG_CAPACITY = 4096;

    private final SecurityProperties securityProperties;
    private final SecurityRedisProperties redisProperties;
    private final StringRedisTemplate redisTemplate;
    private final RedissonClient redissonClient;
    private final SecureRandom secureRandom = new SecureRandom();

    public CaptchaService(
            SecurityProperties securityProperties,
            SecurityRedisProperties redisProperties,
            @Nullable StringRedisTemplate redisTemplate,
            @Nullable RedissonClient redissonClient
    ) {
        this.securityProperties = securityProperties;
        this.redisProperties = redisProperties;
        this.redisTemplate = redisTemplate;
        this.redissonClient = redissonClient;
    }

    public CaptchaChallenge create() {
        String captchaId = UUID.randomUUID().toString();
        String answer = normalize(generateAnswer());
        Instant expiresAt = Instant.now().plus(securityProperties.captchaTtl());
        byte[] imageBytes = renderImageBytes(answer.toUpperCase(Locale.ROOT));

        if (redissonCaptchaEnabled()) {
            try {
                redissonClient.getBucket(captchaKey(captchaId)).set(answer, securityProperties.captchaTtl());
                return new CaptchaChallenge(captchaId, expiresAt, imageBytes);
            } catch (Exception ex) {
                throw new IllegalStateException("通过 Redisson 持久化验证码失败", ex);
            }
        }

        if (redisCaptchaEnabled()) {
            try {
                redisTemplate.opsForValue().set(captchaKey(captchaId), answer, securityProperties.captchaTtl());
                return new CaptchaChallenge(captchaId, expiresAt, imageBytes);
            } catch (Exception ex) {
                throw new IllegalStateException("通过 RedisTemplate 持久化验证码失败", ex);
            }
        }

        throw new IllegalStateException("验证码存储依赖 Redis，但 Redis 当前不可用");
    }

    public void validate(String captchaId, String captchaCode) {
        if (!StringUtils.hasText(captchaId) || !StringUtils.hasText(captchaCode)) {
            throw new BusinessException("验证码 ID 和验证码不能为空");
        }

        String answer;
        if (redissonCaptchaEnabled()) {
            try {
                RBucket<String> bucket = redissonClient.getBucket(captchaKey(captchaId));
                answer = bucket.getAndDelete();
            } catch (Exception ex) {
                throw new IllegalStateException("通过 Redisson 校验验证码失败", ex);
            }
        } else if (redisCaptchaEnabled()) {
            try {
                answer = redisTemplate.opsForValue().getAndDelete(captchaKey(captchaId));
            } catch (Exception ex) {
                throw new IllegalStateException("通过 RedisTemplate 校验验证码失败", ex);
            }
        } else {
            throw new IllegalStateException("验证码存储依赖 Redis，但 Redis 当前不可用");
        }

        if (!StringUtils.hasText(answer)) {
            throw new BusinessException("验证码已过期");
        }
        if (!answer.equals(normalize(captchaCode))) {
            throw new BusinessException("验证码错误");
        }
    }

    private boolean redisCaptchaEnabled() {
        return redisProperties.captchaEnabled() && redisTemplate != null;
    }

    private boolean redissonCaptchaEnabled() {
        return redisProperties.captchaEnabled() && redisProperties.redissonEnabled() && redissonClient != null;
    }

    private String captchaKey(String captchaId) {
        return redisProperties.resolvedNamespacePrefix() + "captcha:id:" + captchaId;
    }

    private String normalize(String captchaCode) {
        return captchaCode.trim().toLowerCase(Locale.ROOT);
    }

    private String generateAnswer() {
        StringBuilder builder = new StringBuilder(CAPTCHA_LENGTH);
        for (int i = 0; i < CAPTCHA_LENGTH; i++) {
            builder.append(CAPTCHA_CHARS[secureRandom.nextInt(CAPTCHA_CHARS.length)]);
        }
        return builder.toString();
    }

    private byte[] renderImageBytes(String answer) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        StringBuilder svg = new StringBuilder(SVG_CAPACITY);
        svg.append("""
                <svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" viewBox="0 0 %d %d" role="img" aria-label="captcha">
                  <defs>
                    <linearGradient id="captcha-bg" x1="0%%" y1="0%%" x2="100%%" y2="100%%">
                      <stop offset="0%%" stop-color="#f8fafc"/>
                      <stop offset="100%%" stop-color="#e2e8f0"/>
                    </linearGradient>
                    <filter id="soft-blur" x="-20%%" y="-20%%" width="140%%" height="140%%">
                      <feGaussianBlur stdDeviation="1.2"/>
                    </filter>
                    <filter id="roughen" x="-20%%" y="-20%%" width="140%%" height="140%%">
                      <feTurbulence type="fractalNoise" baseFrequency="0.9" numOctaves="1" seed="%d" result="grain"/>
                      <feColorMatrix in="grain" type="saturate" values="0"/>
                      <feComponentTransfer>
                        <feFuncA type="table" tableValues="0 0.18"/>
                      </feComponentTransfer>
                      <feBlend in="SourceGraphic" in2="grain" mode="multiply"/>
                    </filter>
                    <filter id="warp">
                      <feTurbulence type="fractalNoise" baseFrequency="0.022 0.11" numOctaves="2" seed="%d" result="noise"/>
                      <feDisplacementMap in="SourceGraphic" in2="noise" scale="5.4" xChannelSelector="R" yChannelSelector="G"/>
                    </filter>
                  </defs>
                  <rect width="%d" height="%d" rx="16" fill="url(#captcha-bg)"/>
                """.formatted(
                CAPTCHA_WIDTH,
                CAPTCHA_HEIGHT,
                CAPTCHA_WIDTH,
                CAPTCHA_HEIGHT,
                random.nextInt(10_000),
                random.nextInt(10_000),
                CAPTCHA_WIDTH,
                CAPTCHA_HEIGHT
        ));
        appendDecorationCircles(svg, random);
        appendDecorationCurves(svg, random);
        svg.append("<g filter=\"url(#warp)\">");
        appendGlyphs(svg, answer, random);
        svg.append("</g>");
        appendOcclusionBlocks(svg, random);
        appendStrikeThroughs(svg, random);
        appendNoiseDots(svg, random);
        svg.append("</svg>");
        return svg.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendDecorationCircles(StringBuilder svg, ThreadLocalRandom random) {
        for (int i = 0; i < DECORATION_CIRCLE_COUNT; i++) {
            svg.append("<circle cx=\"")
                    .append(10 + random.nextInt(CAPTCHA_WIDTH - 20))
                    .append("\" cy=\"")
                    .append(10 + random.nextInt(CAPTCHA_HEIGHT - 20))
                    .append("\" r=\"")
                    .append(4 + random.nextInt(11))
                    .append("\" fill=\"")
                    .append(CAPTCHA_COLORS[i % CAPTCHA_COLORS.length])
                    .append("\" opacity=\"0.05\"/>");
        }
    }

    private void appendDecorationCurves(StringBuilder svg, ThreadLocalRandom random) {
        for (int i = 0; i < DECORATION_CURVE_COUNT; i++) {
            svg.append("<path d=\"M")
                    .append(random.nextInt(16))
                    .append(' ')
                    .append(12 + random.nextInt(CAPTCHA_HEIGHT - 24))
                    .append(" C")
                    .append(28 + random.nextInt(24))
                    .append(' ')
                    .append(random.nextInt(CAPTCHA_HEIGHT))
                    .append(", ")
                    .append(82 + random.nextInt(24))
                    .append(' ')
                    .append(random.nextInt(CAPTCHA_HEIGHT))
                    .append(", ")
                    .append(CAPTCHA_WIDTH - random.nextInt(14))
                    .append(' ')
                    .append(12 + random.nextInt(CAPTCHA_HEIGHT - 24))
                    .append("\" stroke=\"")
                    .append(CAPTCHA_COLORS[(i + 1) % CAPTCHA_COLORS.length])
                    .append("\" stroke-width=\"1.35\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.28\"/>");
        }
    }

    private void appendGlyphs(StringBuilder svg, String answer, ThreadLocalRandom random) {
        for (int i = 0; i < answer.length(); i++) {
            int x = 16 + i * 23 + random.nextInt(7);
            int y = 28 + random.nextInt(14);
            int rotation = random.nextInt(43) - 21;
            int fontSize = 26 + random.nextInt(5);
            String color = CAPTCHA_COLORS[i % CAPTCHA_COLORS.length];
            char currentChar = answer.charAt(i);

            svg.append("<text x=\"")
                    .append(x + 1)
                    .append("\" y=\"")
                    .append(y + 1)
                    .append("\" fill=\"#ffffff\" opacity=\"0.4\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"")
                    .append(fontSize)
                    .append("\" font-weight=\"700\" transform=\"rotate(")
                    .append(rotation)
                    .append(' ')
                    .append(x)
                    .append(' ')
                    .append(y)
                    .append(")\" filter=\"url(#soft-blur)\">")
                    .append(currentChar)
                    .append("</text>");

            svg.append("<text x=\"")
                    .append(x)
                    .append("\" y=\"")
                    .append(y)
                    .append("\" fill=\"")
                    .append(color)
                    .append("\" font-family=\"'Segoe UI', Arial, sans-serif\" font-size=\"")
                    .append(fontSize)
                    .append("\" font-weight=\"700\" letter-spacing=\"0.08em\" transform=\"rotate(")
                    .append(rotation)
                    .append(' ')
                    .append(x)
                    .append(' ')
                    .append(y)
                    .append(")\" filter=\"url(#roughen)\" opacity=\"0.86\">")
                    .append(currentChar)
                    .append("</text>");
        }
    }

    private void appendStrikeThroughs(StringBuilder svg, ThreadLocalRandom random) {
        for (int i = 0; i < 2; i++) {
            svg.append("<path d=\"M")
                    .append(random.nextInt(10))
                    .append(' ')
                    .append(16 + random.nextInt(CAPTCHA_HEIGHT - 28))
                    .append(" Q")
                    .append(40 + random.nextInt(30))
                    .append(' ')
                    .append(random.nextInt(CAPTCHA_HEIGHT))
                    .append(' ')
                    .append(72 + random.nextInt(18))
                    .append(' ')
                    .append(16 + random.nextInt(CAPTCHA_HEIGHT - 28))
                    .append(" T")
                    .append(CAPTCHA_WIDTH - random.nextInt(10))
                    .append(' ')
                    .append(16 + random.nextInt(CAPTCHA_HEIGHT - 28))
                    .append("\" stroke=\"")
                    .append(CAPTCHA_COLORS[(i + 2) % CAPTCHA_COLORS.length])
                    .append("\" stroke-width=\"1.8\" fill=\"none\" stroke-linecap=\"round\" opacity=\"0.36\"/>");
        }
    }

    private void appendOcclusionBlocks(StringBuilder svg, ThreadLocalRandom random) {
        for (int i = 0; i < OCCLUSION_BLOCK_COUNT; i++) {
            int width = 10 + random.nextInt(10);
            int height = 4 + random.nextInt(6);
            int x = 8 + random.nextInt(CAPTCHA_WIDTH - width - 16);
            int y = 10 + random.nextInt(CAPTCHA_HEIGHT - height - 20);
            svg.append("<rect x=\"")
                    .append(x)
                    .append("\" y=\"")
                    .append(y)
                    .append("\" width=\"")
                    .append(width)
                    .append("\" height=\"")
                    .append(height)
                    .append("\" rx=\"2\" fill=\"#ffffff\" opacity=\"0.28\" transform=\"rotate(")
                    .append(random.nextInt(31) - 15)
                    .append(' ')
                    .append(x + width / 2)
                    .append(' ')
                    .append(y + height / 2)
                    .append(")\"/>");
        }
    }

    private void appendNoiseDots(StringBuilder svg, ThreadLocalRandom random) {
        for (int i = 0; i < DECORATION_DOT_COUNT; i++) {
            svg.append("<circle cx=\"")
                    .append(random.nextInt(CAPTCHA_WIDTH))
                    .append("\" cy=\"")
                    .append(random.nextInt(CAPTCHA_HEIGHT))
                    .append("\" r=\"")
                    .append(1 + random.nextInt(2))
                    .append("\" fill=\"#94a3b8\" opacity=\"0.5\"/>");
        }
    }

    public record CaptchaChallenge(String captchaId, Instant expiresAt, byte[] imageBytes) {
    }
}
