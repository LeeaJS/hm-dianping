package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author ljs
 * @version 1.0
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient() {

        // 配置
        Config config = new Config();
        // 使用单节点模式
        config.useSingleServer().setAddress("redis://127.0.0.1:6379");
        // 创建redisson对象
        return Redisson.create(config);
    }
}
