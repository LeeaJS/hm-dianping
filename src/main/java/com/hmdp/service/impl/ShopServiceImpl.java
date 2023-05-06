package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    // 定义线程池管理线程
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryById(Long id) {

//        // 缓存穿透
//        Shop shop = cacheClient
//                .queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        // 使用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);

        // 使用逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicExpire(id);

        Shop shop = cacheClient
                .queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);


        if (shop == null) {
            return Result.fail("店铺不存在！");
        }

        // 7. 返回
        return Result.ok(shop);
    }

//    public Shop queryWithMutex(Long id) {
//
//        // 1. 从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        // 2. 判断商铺是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 3. 存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        // 判断命中的是否为null
//        if (shopJson != null) {
//            return null;
//        }
//
//        // 开始实现缓存重建
//        // 获取互斥锁
//        String lockKey = "lock:shop:" + id;
//        Shop shop = null;
//
//        try {
//            boolean isLock = tryLock(lockKey);
//            // 判断是否获取成功
//
//            if (!isLock) {
//                // 获取锁失败，休眠
//                Thread.sleep(50);
//                // 重试，进行递归，休眠结束后继续获取锁
//                return queryWithMutex(id);
//            }
//            // 获取锁成功
//            // 4. 不存在，根据id查询数据库
//            shop = getById(id);
//            Thread.sleep(200);
//            if (shop == null) {
//                // 将空值写入redis
//                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//                // 5. 数据库不存在，返回错误
//                return null;
//            }
//            // 6. 数据库存在，将数据写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        } catch (Exception e) {
//            throw new RuntimeException(e);
//        } finally {
//            // 释放锁
//            unlock(lockKey);
//        }
//
//        // 7. 返回
//        return shop;
//
//    }

//    /**
//     * 尝试获取锁
//     *
//     * @param key
//     * @return
//     */
//    private boolean tryLock(String key) {
//
//        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
//        return BooleanUtil.isTrue(flag);
//    }
//
//    /**
//     * 删除锁
//     *
//     * @param key
//     */
//    private void unlock(String key) {
//        stringRedisTemplate.delete(key);
//    }


    @Override
    @Transactional
    public Result update(Shop shop) {

        // 因为更新数据库和写入到redis操作需要保证要么同时执行成功，要么同时执行失败，因此要添加事务

        Long id = shop.getId();

        if (id == null) {
            return Result.fail("店铺id为空");
        }
        // 1.更新数据库
        updateById(shop);
        // 2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);

        return Result.ok();
    }

//    public Shop queryWithLogicExpire(Long id) {
//
//        // 1. 从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        // 2. 判断商铺是否存在
//        if (StrUtil.isBlank(shopJson)) {
//            // 3. 不存在，直接返回null
//            return null;
//        }
//
//        // 4. 命中，需要先把json反序列化为对象
//
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        // 从redisData里获得商铺信息
//        JSONObject data = (JSONObject) redisData.getData();
//        // 将JSONObject转为Shop
//        Shop shop = JSONUtil.toBean(data, Shop.class);
//        // 获取过期时间
//        LocalDateTime expireTime = redisData.getExpireTime();
//
//        // 5. 判断是否过期
//        // 6. 未过期，直接返回店铺信息
//        if (expireTime.isAfter(LocalDateTime.now())) {
//            // 直接返回商铺信息
//            return shop;
//        }
//
//        // 7. 过期，需要缓存重建
//        // 8.获取互斥锁
//        String lockKey = LOCK_SHOP_KEY + id;
//        boolean isLock = tryLock(lockKey);
//        // 9. 判断是否获取锁成功
//        if (isLock) {
//            // 10. 获取锁成功，开启独立线程，实现缓存重建
//            // 使用线程池
//            CACHE_REBUILD_EXECUTOR.submit(() -> {
//
//                try {
//                    // 重建
//                    this.saveShop2Redis(id, 20L);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    // 释放锁
//                    unlock(lockKey);
//                }
//            });
//        }
//
//        // 11. 获取锁失败，返回旧的商铺信息
//        return shop;
//
//    }

//    public Shop queryWithPassThrough(Long id) {
//
//        // 1. 从redis中查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
//
//        // 2. 判断商铺是否存在
//        if (StrUtil.isNotBlank(shopJson)) {
//            // 3. 存在，直接返回
//            return JSONUtil.toBean(shopJson, Shop.class);
//        }
//
//        // 判断命中的是否为null
//        if (shopJson != null) {
//
//            return null;
//        }
//
//        // 4. 不存在，根据id查询数据库
//
//        Shop shop = getById(id);
//        if (shop == null) {
//            // 将空值写入redis
//            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
//            // 5. 数据库不存在，返回错误
//            return null;
//        }
//        // 6. 数据库存在，将数据写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
//
//        // 7. 返回
//        return shop;
//
//    }

//    // 将数据提前放入redis中，数据预热
//    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
//        // 1. 查询店铺数据
//        Shop shop = getById(id);
//        Thread.sleep(200);
//        // 2. 封装逻辑过期时间
//        RedisData redisData = new RedisData();
//        redisData.setData(shop);
//        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
//        // 3. 写入redis
//        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
//    }



}
