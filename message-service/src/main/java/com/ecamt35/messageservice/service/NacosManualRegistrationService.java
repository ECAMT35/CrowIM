package com.ecamt35.messageservice.service;

import com.alibaba.nacos.api.PropertyKeyConst;
import com.alibaba.nacos.api.exception.NacosException;
import com.alibaba.nacos.api.naming.NamingFactory;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import com.ecamt35.messageservice.config.NacosManualRegistrationProperties;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.InetAddress;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nacos 手动注册服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NacosManualRegistrationService {

    private final NacosManualRegistrationProperties properties;
    private final Environment environment;

    private final AtomicBoolean registered = new AtomicBoolean(false);
    private final Object namingServiceLock = new Object();

    private volatile NamingService namingService;
    private volatile RegisteredInstance registeredInstance;

    /**
     * Netty 端口绑定成功后执行 Nacos 注册。
     *
     * @param boundPort Netty 实际绑定端口
     */
    public void registerAfterNettyBound(int boundPort) {
        // 仅当显式开启手动注册时才执行。
        if (!properties.isEnabled()) {
            log.debug("Nacos manual registration disabled, skip register.");
            return;
        }
        // 避免重复注册。
        if (registered.get()) {
            log.debug("Nacos manual registration skipped because instance is already registered.");
            return;
        }
        RegisteredInstance instance = buildRegisteredInstance(boundPort);
        log.info(
                "Nacos manual register start, service={}, group={}, cluster={}, ip={}, port={}",
                instance.serviceName(),
                instance.group(),
                instance.clusterName(),
                instance.ip(),
                instance.port()
        );
        doRegisterWithRetry(instance);
    }

    /**
     * 应用关闭时执行 Nacos 注销，避免脏实例残留。
     */
    @PreDestroy
    public void onShutdown() {
        try {
            deregisterIfNecessary();
        } catch (Throwable ex) {
            log.warn("Nacos manual deregistration got unexpected throwable during shutdown: {}", ex.getMessage());
        }
        try {
            shutdownNamingService();
        } catch (Throwable ex) {
            log.warn("Nacos namingService shutdown got unexpected throwable during shutdown: {}", ex.getMessage());
        }
    }

    /**
     * 构建注册实例快照，统一收敛配置校验。
     *
     * @param boundPort Netty 绑定端口
     * @return 注册实例快照
     */
    private RegisteredInstance buildRegisteredInstance(int boundPort) {
        String serverAddr = trimToNull(properties.getServerAddr());
        String serviceName = resolveServiceName();
        String namespace = defaultIfBlank(trimToNull(properties.getNamespace()), "public");
        String group = defaultIfBlank(trimToNull(properties.getGroup()), "DEFAULT_GROUP");
        String cluster = defaultIfBlank(trimToNull(properties.getClusterName()), "DEFAULT");
        String ip = resolveRegisterIp();
        int registerPort = resolveRegisterPort(boundPort);

        if (!StringUtils.hasText(serverAddr)) {
            throw new IllegalStateException("Nacos manual registration requires non-empty serverAddr.");
        }
        if (!StringUtils.hasText(serviceName)) {
            throw new IllegalStateException("Nacos manual registration requires non-empty serviceName.");
        }

        return new RegisteredInstance(
                serverAddr,
                namespace,
                group,
                cluster,
                serviceName,
                ip,
                registerPort,
                properties.isEphemeral(),
                properties.getWeight()
        );
    }

    /**
     * 执行注册并按配置重试。
     *
     * @param instance 注册实例快照
     */
    private void doRegisterWithRetry(RegisteredInstance instance) {
        int maxAttempts = Math.max(1, properties.getRegisterRetryMaxAttempts());
        long backoffMs = Math.max(100L, properties.getRegisterRetryInitialBackoffMs());
        Exception lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                log.debug(
                        "Nacos manual register attempt, service={}, ip={}, port={}, attempt={}/{}",
                        instance.serviceName(),
                        instance.ip(),
                        instance.port(),
                        attempt,
                        maxAttempts
                );
                NamingService naming = getOrCreateNamingService(instance);
                Instance nacosInstance = new Instance();
                nacosInstance.setIp(instance.ip());
                nacosInstance.setPort(instance.port());
                nacosInstance.setClusterName(instance.clusterName());
                nacosInstance.setEphemeral(instance.ephemeral());
                nacosInstance.setWeight(instance.weight());
                nacosInstance.setMetadata(properties.getMetadata());

                naming.registerInstance(instance.serviceName(), instance.group(), nacosInstance);
                registeredInstance = instance;
                registered.set(true);
                log.info(
                        "Nacos manual register success, service={}, group={}, cluster={}, ip={}, port={}, ephemeral={}",
                        instance.serviceName(),
                        instance.group(),
                        instance.clusterName(),
                        instance.ip(),
                        instance.port(),
                        instance.ephemeral()
                );
                return;
            } catch (Exception ex) {
                lastError = ex;
                log.warn(
                        "Nacos manual registration failed, service={}, group={}, cluster={}, ip={}, port={}, attempt={}/{}, reason={}",
                        instance.serviceName(),
                        instance.group(),
                        instance.clusterName(),
                        instance.ip(),
                        instance.port(),
                        attempt,
                        maxAttempts,
                        buildNacosErrorReason(ex),
                        ex
                );
                if (attempt < maxAttempts) {
                    if (!sleepBeforeRetry(backoffMs)) {
                        break;
                    }
                    backoffMs = backoffMs * 2L;
                }
            }
        }

        if (properties.isFailFast()) {
            throw new IllegalStateException("Nacos manual registration exhausted retries.", lastError);
        }
        log.warn(
                "Nacos manual registration exhausted retries but failFast=false, service continues without registration, service={}, group={}, ip={}, port={}",
                instance.serviceName(),
                instance.group(),
                instance.ip(),
                instance.port()
        );
    }

    /**
     * 注销已注册实例，确保服务关闭时及时下线。
     */
    private void deregisterIfNecessary() {
        if (!registered.compareAndSet(true, false)) {
            log.debug("Nacos manual deregistration skipped because no active registration.");
            return;
        }
        RegisteredInstance instance = registeredInstance;
        if (instance == null) {
            log.debug("Nacos manual deregistration skipped because registered instance snapshot is empty.");
            return;
        }
        try {
            NamingService naming = namingService;
            if (naming != null) {
                // 关闭边界日志：确认实例已经从注册中心下线。
                naming.deregisterInstance(
                        instance.serviceName(),
                        instance.group(),
                        instance.ip(),
                        instance.port(),
                        instance.clusterName()
                );
                log.info(
                        "Nacos manual deregister success, service={}, group={}, cluster={}, ip={}, port={}",
                        instance.serviceName(),
                        instance.group(),
                        instance.clusterName(),
                        instance.ip(),
                        instance.port()
                );
            }
        } catch (Exception ex) {
            log.error(
                    "Nacos manual deregistration failed, service={}, group={}, cluster={}, ip={}, port={}",
                    instance.serviceName(),
                    instance.group(),
                    instance.clusterName(),
                    instance.ip(),
                    instance.port(),
                    ex
            );
        } finally {
            registeredInstance = null;
        }
    }

    /**
     * 延迟重试。
     *
     * @param backoffMs 等待毫秒
     * @return true 表示可继续重试，false 表示线程被中断
     */
    private boolean sleepBeforeRetry(long backoffMs) {
        try {
            TimeUnit.MILLISECONDS.sleep(backoffMs);
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.error("Nacos manual registration retry sleep interrupted.");
            return false;
        }
    }

    /**
     * 获取或创建 NamingService。
     *
     * @param instance 注册实例快照
     * @return NamingService 实例
     */
    private NamingService getOrCreateNamingService(RegisteredInstance instance) throws Exception {
        NamingService current = namingService;
        if (current != null) {
            return current;
        }
        synchronized (namingServiceLock) {
            current = namingService;
            if (current != null) {
                return current;
            }
            Properties nacosProps = new Properties();
            nacosProps.setProperty(PropertyKeyConst.SERVER_ADDR, instance.serverAddr());
            nacosProps.setProperty(PropertyKeyConst.NAMESPACE, instance.namespace());
            if (StringUtils.hasText(properties.getUsername())) {
                nacosProps.setProperty(PropertyKeyConst.USERNAME, properties.getUsername().trim());
            }
            if (StringUtils.hasText(properties.getPassword())) {
                nacosProps.setProperty(PropertyKeyConst.PASSWORD, properties.getPassword().trim());
            }
            namingService = NamingFactory.createNamingService(nacosProps);
            return namingService;
        }
    }

    /**
     * 关闭 NamingService，释放 Nacos 客户端资源。
     */
    private void shutdownNamingService() {
        NamingService naming = namingService;
        // 先置空引用，避免关闭阶段并发重复操作。
        namingService = null;
        if (naming == null) {
            return;
        }
        try {
            naming.shutDown();
            log.debug("Nacos namingService shutdown success.");
        } catch (Throwable ex) {
            if (isNotifyCenterAlreadyDestroyed(ex)) {
                // 进程关闭阶段 Nacos 静态资源已销毁时，关闭客户端可能抛该异常，不影响实例注销结果。
                log.debug("Nacos namingService shutdown skipped because nacos notify center already destroyed.");
                return;
            }
            log.warn("Nacos namingService shutdown failed: {}", ex.getMessage());
        }
    }

    /**
     * 解析服务名，未配置时回退 spring.application.name。
     *
     * @return 服务名
     */
    private String resolveServiceName() {
        String serviceName = trimToNull(properties.getServiceName());
        if (StringUtils.hasText(serviceName)) {
            return serviceName;
        }
        return trimToNull(environment.getProperty("spring.application.name"));
    }

    /**
     * 解析注册端口，优先使用显式配置。
     *
     * @param boundPort Netty 绑定端口
     * @return 注册端口
     */
    private int resolveRegisterPort(int boundPort) {
        Integer customPort = properties.getPort();
        int registerPort = (customPort != null && customPort > 0) ? customPort : boundPort;
        if (registerPort <= 0 || registerPort > 65535) {
            throw new IllegalStateException("Nacos manual registration got invalid port: " + registerPort);
        }
        return registerPort;
    }

    /**
     * 解析注册 IP，未配置时自动探测本机地址。
     *
     * @return 注册 IP
     */
    private String resolveRegisterIp() {
        String configuredIp = trimToNull(properties.getIp());
        if (StringUtils.hasText(configuredIp)) {
            return configuredIp;
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception ex) {
            throw new IllegalStateException("Nacos manual registration failed to resolve local ip.", ex);
        }
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private String trimToNull(String value) {
        if (Objects.isNull(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 构造 Nacos 异常摘要，便于快速定位鉴权/网络/配置问题。
     *
     * @param ex 原始异常
     * @return 异常摘要字符串
     */
    private String buildNacosErrorReason(Exception ex) {
        if (ex instanceof NacosException nacosException) {
            return "NacosException{errCode=" + nacosException.getErrCode() + ", errMsg=" + nacosException.getErrMsg() + "}";
        }
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root instanceof NacosException nacosException) {
            return "NacosException{errCode=" + nacosException.getErrCode() + ", errMsg=" + nacosException.getErrMsg() + "}";
        }
        return ex.getClass().getSimpleName() + ": " + ex.getMessage();
    }

    /**
     * 判断是否为 Nacos 关闭阶段静态资源已销毁导致的非关键异常。
     *
     * @param ex 异常
     * @return true 表示可忽略
     */
    private boolean isNotifyCenterAlreadyDestroyed(Throwable ex) {
        Throwable cur = ex;
        while (cur != null) {
            String msg = cur.getMessage();
            if (StringUtils.hasText(msg)
                    && msg.contains("NotifyCenter.INSTANCE")
                    && msg.contains("is null")) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /**
     * 注册实例快照，避免注册与注销参数不一致。
     */
    private record RegisteredInstance(
            String serverAddr,
            String namespace,
            String group,
            String clusterName,
            String serviceName,
            String ip,
            int port,
            boolean ephemeral,
            float weight
    ) {
    }
}
