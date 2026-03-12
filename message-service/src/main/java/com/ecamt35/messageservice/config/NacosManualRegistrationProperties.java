package com.ecamt35.messageservice.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Nacos 手动注册配置。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "im.nacos.manual")
public class NacosManualRegistrationProperties {

    /**
     * 是否启用手动注册。
     */
    private boolean enabled = false;

    /**
     * 注册失败是否快速失败（抛异常终止启动）。
     */
    private boolean failFast = true;

    /**
     * Nacos 地址，如 127.0.0.1:8848。
     */
    private String serverAddr;

    /**
     * Nacos 命名空间。
     */
    private String namespace = "public";

    /**
     * Nacos 分组。
     */
    private String group = "DEFAULT_GROUP";

    /**
     * Nacos 集群名。
     */
    private String clusterName = "DEFAULT";

    /**
     * 服务名，默认与 spring.application.name 一致。
     */
    private String serviceName;

    /**
     * 注册 IP，未设置则自动探测。
     */
    private String ip;

    /**
     * 注册端口，<=0 时使用 Netty 绑定端口。
     */
    private Integer port = 0;

    /**
     * Nacos 鉴权用户名。
     */
    private String username;

    /**
     * Nacos 鉴权密码。
     */
    private String password;

    /**
     * 是否临时实例（默认 true，Nacos Client 自动心跳）。
     */
    private boolean ephemeral = true;

    /**
     * 实例权重。
     */
    private float weight = 1.0F;

    /**
     * 注册重试次数。
     */
    private int registerRetryMaxAttempts = 3;

    /**
     * 注册首次重试等待时间（毫秒）。
     */
    private long registerRetryInitialBackoffMs = 500L;

    /**
     * 实例元数据。
     */
    private Map<String, String> metadata = new HashMap<>();
}

