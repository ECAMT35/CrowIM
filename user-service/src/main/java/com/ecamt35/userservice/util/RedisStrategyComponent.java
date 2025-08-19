package com.ecamt35.userservice.util;

import cn.hutool.core.util.RandomUtil;
import jakarta.annotation.Resource;
import org.redisson.Redisson;
import org.redisson.api.RLock;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class RedisStrategyComponent {

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private Redisson redisson;

    /**
     * 查询缓存，如果缓存不存在则查询数据库，并将查询结果放入缓存，集成一系列策略
     * (用于有主键查询)
     *
     * @param prefix   缓存前缀
     * @param id       主键
     * @param fallback 查询数据库的方法
     * @param <ID>     主键类型
     * @param <R>      返回类型
     * @return 查询结果
     */
    public <ID, R> R queryWithPassThrough(final String prefix, ID id
            , Function<ID, R> fallback, Long time, TimeUnit timeUnit) throws InterruptedException {

        String redisKey = prefix + id;

        R result = (R) redisTemplate.opsForValue().get(redisKey);

        if (result != null) {
            // 缓存命中
            return result;
        }

        // 缓存未命中
//        if (bloomFilter.contains(redisKey)) {
//            // 布隆过滤器未命中
//            return null;
//        }

        // 设置分布式锁
        RLock lock = redisson.getLock("lock:" + redisKey);

        for (int i = 0; i < 5; i++) {
            // 设置锁超时时间，防止死锁
            if (!lock.tryLock(20, 20, TimeUnit.SECONDS)) {
                Thread.sleep(100 * (i + 1));
            } else {
                try {
                    result = fallback.apply(id);
                    if (result != null) {
                        // 将查询结果放入缓存,防止缓存雪崩
                        long randomTime = RandomUtil.randomLong(60, 300);
                        long relayTime = TimeUnit.SECONDS.convert(time, timeUnit) + randomTime;
                        redisTemplate.opsForValue().set(redisKey, result, relayTime, timeUnit);
                        // bloomFilter.add(redisKey);
                        return result;
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
        throw new BusinessException(BusinessErrorCodeEnum.SYSTEM_ERROR);
    }

    /**
     * 查询指定缓存，如果缓存不存在则调用提供的查询函数，并将结果存入缓存
     * 用于 Hash 数据结构的查询
     *
     * @param redisKey 键
     * @param fallback 查询数据库的方法，返回一个 Map 对象
     * @param time     基础有效时间
     * @param timeUnit 有效时间的单位
     * @param <R>      Map 中 value 的数据类型
     * @return 查询结果
     */
    public <R> Map<String, R> forHash(final String redisKey, Supplier<Map<String, R>> fallback, Long time, TimeUnit timeUnit) throws InterruptedException {
        // 尝试从 Redis 中获取数据
        Map<String, R> result = redisTemplate.opsForHash().entries(redisKey);

        // 缓存命中
        if (result != null && !result.isEmpty()) {
            return result;
        }

        // 缓存未命中
        // 设置分布式锁
        RLock lock = redisson.getLock("lock:" + redisKey);

        for (int i = 0; i < 5; i++) {
            // 设置锁超时时间，防止死锁
            if (!lock.tryLock(10, 20, TimeUnit.SECONDS)) {
                Thread.sleep(200 * (i + 1));
            } else {
                try {
                    result = fallback.get();
                    if (result != null && !result.isEmpty()) {
                        long randomTime = RandomUtil.randomLong(60, 300);
                        long relayTime = TimeUnit.SECONDS.convert(time, timeUnit) + randomTime;
                        redisTemplate.opsForHash().putAll(redisKey, result);
                        redisTemplate.expire(redisKey, relayTime, timeUnit);
                        return result;
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
        throw new BusinessException(BusinessErrorCodeEnum.SYSTEM_ERROR);
    }


    /**
     * 查询指定缓存，如果缓存不存在则调用提供的查询函数，并将结果存入缓存
     * 用于 Hash 数据结构的查询
     *
     * @param redisKey 键
     * @param fallback 查询数据库的方法，返回一个 Map 对象
     * @param time     基础有效时间
     * @param timeUnit 有效时间的单位
     * @param <R>      Map 中 value 的数据类型
     * @return 查询结果
     */
    public <R> Set<R> forSet(final String redisKey, Supplier<Set<R>> fallback, Long time, TimeUnit timeUnit) throws InterruptedException {
        // 尝试从 Redis 中获取数据
        Set<R> result = redisTemplate.opsForSet().members(redisKey);

        // 缓存命中
        if (result != null && !result.isEmpty()) {
            return result;
        }

        // 缓存未命中
        // 设置分布式锁
        RLock lock = redisson.getLock("lock:" + redisKey);

        for (int i = 0; i < 5; i++) {
            // 设置锁超时时间，防止死锁
            if (!lock.tryLock(10, 20, TimeUnit.SECONDS)) {
                Thread.sleep(200 * (i + 1));
            } else {
                try {
                    result = fallback.get();
                    if (result != null && !result.isEmpty()) {
                        long randomTime = RandomUtil.randomLong(60, 300);
                        long relayTime = TimeUnit.SECONDS.convert(time, timeUnit) + randomTime;
                        redisTemplate.opsForSet().add(redisKey, result.toArray());
                        redisTemplate.expire(redisKey, relayTime, timeUnit);
                        return result;
                    }
                } finally {
                    lock.unlock();
                }
            }
        }
        throw new BusinessException(BusinessErrorCodeEnum.SYSTEM_ERROR);
    }

}
