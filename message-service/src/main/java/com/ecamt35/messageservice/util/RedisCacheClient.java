package com.ecamt35.messageservice.util;

import com.ecamt35.messageservice.config.RedisCachePropertiesConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 通用 Redis 缓存客户端
 * 1) Cache-Aside：先查缓存，miss 再回源，回填缓存
 * 2) 防穿透：空值 / 空集合占位（短 TTL）
 * 3) 防击穿：分布式锁 + double-check（锁前/锁后都检查缓存）
 * 4) 防雪崩：TTL 抖动（jitter）
 * <p>
 * 注意：
 * - 传入的 key 必须是“业务完整 key”
 * - lockKey = lockPrefix + key
 */
public class RedisCacheClient {

    /**
     * value 空值占位（防穿透）
     */
    private static final String NULL_PLACEHOLDER = "__CACHE_NULL__";

    /**
     * set 空集合占位成员
     */
    private static final String EMPTY_SET_PLACEHOLDER = "__CACHE_EMPTY_SET__";

    /**
     * hash 空 map 占位字段
     */
    private static final String EMPTY_HASH_FIELD = "__CACHE_EMPTY_HASH__";

    /**
     * 获取锁的最大尝试次数（按你要求：3次）
     */
    private static final int LOCK_RETRY_TIMES = 3;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;
    private final RedisCachePropertiesConfig props;

    public RedisCacheClient(RedisTemplate<String, Object> redisTemplate,
                            RedissonClient redissonClient,
                            ObjectMapper objectMapper,
                            RedisCachePropertiesConfig props) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.props = Objects.requireNonNull(props, "props must not be null");
        validate(props);
    }

    /**
     * 主键查询 value 缓存
     * <p>
     * 用法：
     * - keyPrefix: "user:"
     * - id: 123
     * - key = "user:123"
     */
    public <ID, R> R getOrLoadById(String keyPrefix,
                                   ID id,
                                   Duration ttl,
                                   Class<R> type,
                                   Function<ID, R> loader) {

        final String key = buildKey(keyPrefix, String.valueOf(id));

        // 1) 查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (isNullPlaceholder(cached)) return null;
            return convertTo(cached, type);
        }

        // 2) miss -> 锁
        final String lockKey = props.getLockPrefix() + key;
        final RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = tryLockWithRetry(lock);

            if (!locked) {
                // 3 次都失败 最后再 double-check 一次缓存
                Object again = redisTemplate.opsForValue().get(key);
                if (again != null) {
                    if (isNullPlaceholder(again)) return null;
                    return convertTo(again, type);
                }
                throw new BusinessException("Failed to acquire cache lock after 3 attempts, key=" + key);
            }

            // 锁内 double-check
            Object second = redisTemplate.opsForValue().get(key);
            if (second != null) {
                if (isNullPlaceholder(second)) return null;
                return convertTo(second, type);
            }

            // 回源
            R loaded = loader.apply(id);

            // 回填
            if (loaded == null) {
                redisTemplate.opsForValue().set(key, NULL_PLACEHOLDER, props.getNullTtl());
                return null;
            }

            redisTemplate.opsForValue().set(key, loaded, withJitter(ttl));
            return loaded;

        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Hash：缓存整张 Map
     * <p>
     * 注意：
     * - key 传入必须是业务完整 key
     */
    public <R> Map<String, R> getOrLoadHash(String key,
                                            Duration ttl,
                                            Class<R> valueType,
                                            Supplier<Map<String, R>> loader) {

        // 1) 查缓存
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (!CollectionUtils.isEmpty(entries)) {
            if (entries.size() == 1 && entries.containsKey(EMPTY_HASH_FIELD)) {
                return Collections.emptyMap();
            }
            Map<String, R> result = new HashMap<>(entries.size());
            for (Map.Entry<Object, Object> e : entries.entrySet()) {
                String field = String.valueOf(e.getKey());
                if (EMPTY_HASH_FIELD.equals(field)) continue;
                result.put(field, convertTo(e.getValue(), valueType));
            }
            return result;
        }

        // 2) miss -> 锁
        final String lockKey = props.getLockPrefix() + key;
        final RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = tryLockWithRetry(lock);

            if (!locked) {
                // 3 次都失败：最后再 double-check 一次缓存
                Map<Object, Object> again = redisTemplate.opsForHash().entries(key);
                if (!CollectionUtils.isEmpty(again)) {
                    if (again.size() == 1 && again.containsKey(EMPTY_HASH_FIELD)) {
                        return Collections.emptyMap();
                    }
                    Map<String, R> result = new HashMap<>(again.size());
                    for (Map.Entry<Object, Object> e : again.entrySet()) {
                        String field = String.valueOf(e.getKey());
                        if (EMPTY_HASH_FIELD.equals(field)) continue;
                        result.put(field, convertTo(e.getValue(), valueType));
                    }
                    return result;
                }
                throw new BusinessException("Failed to acquire cache lock after 3 attempts, hashKey=" + key);
            }

            // 锁内 double-check
            Map<Object, Object> second = redisTemplate.opsForHash().entries(key);
            if (!CollectionUtils.isEmpty(second)) {
                if (second.size() == 1 && second.containsKey(EMPTY_HASH_FIELD)) {
                    return Collections.emptyMap();
                }
                Map<String, R> result = new HashMap<>(second.size());
                for (Map.Entry<Object, Object> e : second.entrySet()) {
                    String field = String.valueOf(e.getKey());
                    if (EMPTY_HASH_FIELD.equals(field)) continue;
                    result.put(field, convertTo(e.getValue(), valueType));
                }
                return result;
            }

            // 回源
            Map<String, R> loaded = loader.get();
            if (loaded == null || loaded.isEmpty()) {
                redisTemplate.opsForHash().put(key, EMPTY_HASH_FIELD, 1);
                redisTemplate.expire(key, props.getNullTtl());
                return Collections.emptyMap();
            }

            // 回填
            redisTemplate.opsForHash().putAll(key, loaded);
            redisTemplate.expire(key, withJitter(ttl));
            return loaded;

        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * Set：缓存整套 Set
     * <p>
     * 注意：
     * - key 传入必须是业务完整 key
     */
    public <R> Set<R> getOrLoadSet(String key,
                                   Duration ttl,
                                   Class<R> elementType,
                                   Supplier<Set<R>> loader) {

        // 1) 查缓存
        Set<Object> members = redisTemplate.opsForSet().members(key);
        if (!CollectionUtils.isEmpty(members)) {
            if (members.size() == 1 && members.contains(EMPTY_SET_PLACEHOLDER)) {
                return Collections.emptySet();
            }
            Set<R> result = new HashSet<>(members.size());
            for (Object m : members) {
                if (Objects.equals(m, EMPTY_SET_PLACEHOLDER)) continue;
                result.add(convertTo(m, elementType));
            }
            return result;
        }

        // 2) miss -> 锁
        final String lockKey = props.getLockPrefix() + key;
        final RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            locked = tryLockWithRetry(lock);

            if (!locked) {
                // 3 次都失败：最后再 double-check 一次缓存
                Set<Object> again = redisTemplate.opsForSet().members(key);
                if (!CollectionUtils.isEmpty(again)) {
                    if (again.size() == 1 && again.contains(EMPTY_SET_PLACEHOLDER)) {
                        return Collections.emptySet();
                    }
                    Set<R> result = new HashSet<>(again.size());
                    for (Object m : again) {
                        if (Objects.equals(m, EMPTY_SET_PLACEHOLDER)) continue;
                        result.add(convertTo(m, elementType));
                    }
                    return result;
                }
                throw new BusinessException("Failed to acquire cache lock after 3 attempts, setKey=" + key);
            }

            // 锁内 double-check
            Set<Object> second = redisTemplate.opsForSet().members(key);
            if (!CollectionUtils.isEmpty(second)) {
                if (second.size() == 1 && second.contains(EMPTY_SET_PLACEHOLDER)) {
                    return Collections.emptySet();
                }
                Set<R> result = new HashSet<>(second.size());
                for (Object m : second) {
                    if (Objects.equals(m, EMPTY_SET_PLACEHOLDER)) continue;
                    result.add(convertTo(m, elementType));
                }
                return result;
            }

            // 回源
            Set<R> loaded = loader.get();
            if (loaded == null || loaded.isEmpty()) {
                redisTemplate.opsForSet().add(key, EMPTY_SET_PLACEHOLDER);
                redisTemplate.expire(key, props.getNullTtl());
                return Collections.emptySet();
            }

            //  避免把整个 Object[] 当成一个元素写进 set
            Object[] arr = loaded.toArray(new Object[0]);
            redisTemplate.opsForSet().add(key, arr);
            redisTemplate.expire(key, withJitter(ttl));
            return loaded;

        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 删除缓存（写后删缓存策略常用）
     */
    public void evict(String key) {
        redisTemplate.delete(key);
    }

    /**
     * 批量删除
     */
    public void evict(Collection<String> keys) {
        if (CollectionUtils.isEmpty(keys)) return;
        List<String> realKeys = keys.stream().toList();
        redisTemplate.delete(realKeys);
    }

    /**
     * 尝试获取分布式锁（最多重试 3 次），带线性退避。
     */
    private boolean tryLockWithRetry(RLock lock) {
        for (int i = 1; i <= LOCK_RETRY_TIMES; i++) {
            try {
                if (lock.tryLock(props.getLockWaitTime().toMillis(), TimeUnit.MILLISECONDS)) {
                    return true;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException("Interrupted while acquiring cache lock", e);
            }
            // 退避：50ms, 100ms, 150ms（可按需调）
            sleepQuietly(50L * i);
        }
        return false;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("Interrupted while waiting for cache lock retry", e);
        }
    }

    /**
     * 构造业务 key。
     * <p>
     * prefix 仅用于 getOrLoadById 这种拼 id 的场景。
     */
    private String buildKey(String prefix, String keyPart) {
        String p = prefix == null ? "" : prefix;
        String k = keyPart == null ? "" : keyPart;
        String real = p + k;

        // 避免误传空 key，导致锁/缓存污染
        if (real.isBlank()) {
            throw new BusinessException("cache key must not be blank");
        }
        return real;
    }

    /**
     * TTL 抖动，防止雪崩。
     */
    private Duration withJitter(Duration baseTtl) {
        if (baseTtl == null || baseTtl.isZero() || baseTtl.isNegative()) {
            throw new BusinessException("baseTtl must be positive");
        }
        long baseSeconds = baseTtl.getSeconds();
        long jitter = ThreadLocalRandom.current().nextLong(
                props.getJitterSecondsMin(),
                props.getJitterSecondsMax() + 1
        );
        return Duration.ofSeconds(baseSeconds + jitter);
    }

    private boolean isNullPlaceholder(Object cached) {
        return (cached instanceof String s) && NULL_PLACEHOLDER.equals(s);
    }

    private <T> T convertTo(Object obj, Class<T> type) {
        if (obj == null) return null;
        if (type.isInstance(obj)) return type.cast(obj);
        return objectMapper.convertValue(obj, type);
    }

    private void validate(RedisCachePropertiesConfig p) {
        if (p.getJitterSecondsMin() < 0 || p.getJitterSecondsMax() < 0 || p.getJitterSecondsMax() < p.getJitterSecondsMin()) {
            throw new IllegalArgumentException("Invalid jitter seconds range");
        }
        if (p.getNullTtl() == null || p.getNullTtl().isNegative() || p.getNullTtl().isZero()) {
            throw new IllegalArgumentException("nullTtl must be positive");
        }
        if (p.getLockWaitTime() == null || p.getLockWaitTime().isNegative()) {
            throw new IllegalArgumentException("lockWaitTime must be non-negative");
        }
        if (p.getLockPrefix() == null) {
            throw new IllegalArgumentException("lockPrefix must not be null");
        }
    }
}