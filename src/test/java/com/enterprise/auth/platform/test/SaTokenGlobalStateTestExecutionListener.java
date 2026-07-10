package com.enterprise.auth.platform.test;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.http.SaHttpTemplate;
import cn.dev33.satoken.json.SaJsonTemplate;
import cn.dev33.satoken.log.SaLog;
import cn.dev33.satoken.plugin.SaTokenPluginForJackson;
import cn.dev33.satoken.same.SaSameTemplate;
import cn.dev33.satoken.secure.totp.SaTotpTemplate;
import cn.dev33.satoken.serializer.SaSerializerTemplate;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.temp.SaTempTemplate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.Ordered;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/** Restores Sa-Token's JVM-global components after an isolated Spring test context. */
public final class SaTokenGlobalStateTestExecutionListener implements TestExecutionListener, Ordered {

    private static final Map<Class<?>, Snapshot> SNAPSHOTS = new ConcurrentHashMap<>();

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        SNAPSHOTS.put(testContext.getTestClass(), Snapshot.capture());
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        bind(testContext.getApplicationContext());
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        Snapshot snapshot = SNAPSHOTS.remove(testContext.getTestClass());
        if (snapshot != null) {
            snapshot.restore();
        }
    }

    private void bind(ApplicationContext applicationContext) {
        setIfAvailable(applicationContext, SaTokenConfig.class, SaManager::setConfig);
        setIfAvailable(applicationContext, SaTokenDao.class, SaManager::setSaTokenDao);
        setIfAvailable(applicationContext, StpInterface.class, SaManager::setStpInterface);
        setIfAvailable(applicationContext, SaTokenContext.class, SaManager::setSaTokenContext);
        setIfAvailable(applicationContext, SaTempTemplate.class, SaManager::setSaTempTemplate);
        setIfAvailable(applicationContext, SaJsonTemplate.class, SaManager::setSaJsonTemplate);
        setIfAvailable(applicationContext, SaHttpTemplate.class, SaManager::setSaHttpTemplate);
        setIfAvailable(applicationContext, SaSerializerTemplate.class, SaManager::setSaSerializerTemplate);
        setIfAvailable(applicationContext, SaSameTemplate.class, SaManager::setSaSameTemplate);
        setIfAvailable(applicationContext, SaLog.class, SaManager::setLog);
        setIfAvailable(applicationContext, SaTotpTemplate.class, SaManager::setSaTotpTemplate);
        setIfAvailable(applicationContext, StpLogic.class, StpUtil::setStpLogic);
        new SaTokenPluginForJackson().install();
    }

    private <T> void setIfAvailable(
            ApplicationContext applicationContext,
            Class<T> type,
            java.util.function.Consumer<T> setter
    ) {
        T bean = applicationContext.getBeanProvider(type).getIfAvailable();
        if (bean != null) {
            setter.accept(bean);
        }
    }

    private record Snapshot(
            SaTokenConfig config,
            SaTokenDao dao,
            StpInterface stpInterface,
            SaTokenContext context,
            SaTempTemplate tempTemplate,
            SaJsonTemplate jsonTemplate,
            SaHttpTemplate httpTemplate,
            SaSerializerTemplate serializerTemplate,
            SaSameTemplate sameTemplate,
            SaLog log,
            SaTotpTemplate totpTemplate,
            StpLogic stpLogic,
            Map<String, StpLogic> stpLogicMap
    ) {
        private static Snapshot capture() {
            return new Snapshot(
                    SaManager.getConfig(),
                    SaManager.getSaTokenDao(),
                    SaManager.getStpInterface(),
                    SaManager.getSaTokenContext(),
                    SaManager.getSaTempTemplate(),
                    SaManager.getSaJsonTemplate(),
                    SaManager.getSaHttpTemplate(),
                    SaManager.getSaSerializerTemplate(),
                    SaManager.getSaSameTemplate(),
                    SaManager.getLog(),
                    SaManager.getSaTotpTemplate(),
                    StpUtil.stpLogic,
                    new LinkedHashMap<>(SaManager.stpLogicMap)
            );
        }

        private void restore() {
            SaManager.setConfig(config);
            SaManager.setSaTokenDao(dao);
            SaManager.setStpInterface(stpInterface);
            SaManager.setSaTokenContext(context);
            SaManager.setSaTempTemplate(tempTemplate);
            SaManager.setSaJsonTemplate(jsonTemplate);
            new SaTokenPluginForJackson().install();
            SaManager.setSaHttpTemplate(httpTemplate);
            SaManager.setSaSerializerTemplate(serializerTemplate);
            SaManager.setSaSameTemplate(sameTemplate);
            SaManager.setLog(log);
            SaManager.setSaTotpTemplate(totpTemplate);
            SaManager.stpLogicMap.clear();
            SaManager.stpLogicMap.putAll(stpLogicMap);
            StpUtil.stpLogic = stpLogic;
        }
    }
}
