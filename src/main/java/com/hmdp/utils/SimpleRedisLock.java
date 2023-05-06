package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    // 业务的名称，不同的业务对应不同的锁
    private String name;

    private StringRedisTemplate stringRedisTemplate;

    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        // ClassPathResource可以直接去resource下找文件
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {

        // 获取线程的标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 获取锁
        // 使用前缀加上业务名称作为key
        // 当前的线程作为value
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        // 不要直接返回success，因为success是包装类，返回的时候会被拆箱
        // 如果传入的是null，那么拆箱的时候会报空指针异常
        // 使用Boolean.TRUE.equals(success)进行判断，如果为true则返回true，如果是null或者false都返回false
        return Boolean.TRUE.equals(success);
    }

//    @Override
////    public void unlock() {
////
////        // 获取线程标识
////        String threadId = ID_PREFIX + Thread.currentThread().getId();
////
////        // 获取锁中的线程标识
////        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
////
////        // 判断标识是否一致
////        if(threadId.equals(id)){
////            // 释放锁
////            stringRedisTemplate.delete(KEY_PREFIX + name);
////        }
////    }

    @Override
    public void unlock() {
        // 调用lua脚本
        // 第一个参数传入脚本
        // 第二个参数将key的名称转化成单个集合
        // 第三个参数为当前线程标识
        // 这样释放锁的代码就变成了一行，查询和判断要么执行成功，要么都执行失败
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());

    }
}
