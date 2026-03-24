package com.enterprise.auth.platform.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

class AuthPrincipalCacheServiceTest {

    @Test
    void shouldEvictIdAndUsernameKeys() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(AuthPrincipalCacheService.CACHE_NAME)).thenReturn(cache);
        AuthPrincipalCacheService service = new AuthPrincipalCacheService(cacheManager);

        service.evictByUser(10L, "tenant-a", "alice");

        verify(cache).evict("id:10");
        verify(cache).evict("username:tenant-a:alice");
    }

    @Test
    void shouldSkipWhenCacheAbsent() {
        CacheManager cacheManager = mock(CacheManager.class);
        when(cacheManager.getCache(AuthPrincipalCacheService.CACHE_NAME)).thenReturn(null);
        AuthPrincipalCacheService service = new AuthPrincipalCacheService(cacheManager);

        service.evictByUser(10L, "tenant-a", "alice");
        service.evictAll();

        verify(cacheManager, times(2)).getCache(AuthPrincipalCacheService.CACHE_NAME);
    }

    @Test
    void shouldSkipUsernameEvictionWhenUsernameBlank() {
        CacheManager cacheManager = mock(CacheManager.class);
        Cache cache = mock(Cache.class);
        when(cacheManager.getCache(AuthPrincipalCacheService.CACHE_NAME)).thenReturn(cache);
        AuthPrincipalCacheService service = new AuthPrincipalCacheService(cacheManager);

        service.evictByUser(10L, "tenant-a", " ");

        verify(cache).evict("id:10");
        verify(cache, never()).evict("username:tenant-a: ");
    }
}
