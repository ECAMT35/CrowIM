package com.ecamt35.messageservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class LuaScriptsConfig {

    @Bean("incrIfExistsScript")
    public DefaultRedisScript<Long> incrIfExistsScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/incr_if_exists.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("initBaseAndIncrScript")
    public DefaultRedisScript<Long> initBaseAndIncrScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/init_base_and_incr.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("initOrMaxAndGetScript")
    public DefaultRedisScript<Long> initOrMaxAndGetScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/init_or_max_and_get.lua")));
        script.setResultType(Long.class);
        return script;
    }

    @Bean("maxHsetScript")
    public DefaultRedisScript<Long> maxHsetScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/max_hset.lua")));
        script.setResultType(Long.class);
        return script;
    }
}