package com.enterprise.auth.platform.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.dao.SaTokenDaoForRedisTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class SaTokenStorageConfigTest {

    private final SaTokenStorageConfig config = new SaTokenStorageConfig();

    @Test
    void redisSaTokenDaoShouldBeExplicitlyInitialized() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);

        SaTokenDao dao = config.redisSaTokenDao(connectionFactory);

        assertThat(dao).isInstanceOf(SaTokenDaoForRedisTemplate.class);
        SaTokenDaoForRedisTemplate redisDao = (SaTokenDaoForRedisTemplate) dao;
        assertThat(redisDao.isInit).isTrue();
        assertThat(redisDao.stringRedisTemplate).isNotNull();
    }

    @Test
    void localSaTokenDaoShouldRemainAvailableWhenRedisSessionDisabled() {
        SaTokenDao dao = config.localSaTokenDao();

        assertThat(dao).isInstanceOf(SaTokenDaoDefaultImpl.class);
    }
}