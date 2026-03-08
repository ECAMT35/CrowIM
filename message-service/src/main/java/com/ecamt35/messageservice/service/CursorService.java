package com.ecamt35.messageservice.service;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.mapper.ConversationMemberMapper;
import com.ecamt35.messageservice.mapper.MessageMapper;
import com.ecamt35.messageservice.model.entity.ConversationMember;
import com.ecamt35.messageservice.util.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CursorService {

    // TTL = 30 day
    private static final String CURSOR_TTL_SECONDS = String.valueOf(24 * 60 * 60 * 30);
    private static final long CURSOR_TTL_SECONDS_LONG = 24L * 60 * 60 * 30;

    private final StringRedisTemplate redisTemplate;
    private final MessageMapper messageMapper;
    private final ConversationMemberMapper memberMapper;
    private final RedissonClient redissonClient;

    private final DefaultRedisScript<Long> incrIfExistsScript;
    private final DefaultRedisScript<Long> initBaseAndIncrScript;
    private final DefaultRedisScript<Long> initOrMaxAndGetScript;
    private final DefaultRedisScript<Long> maxHsetScript;

    public CursorService(
            StringRedisTemplate redisTemplate,
            MessageMapper messageMapper,
            ConversationMemberMapper memberMapper,
            RedissonClient redissonClient,
            @Qualifier("incrIfExistsScript") DefaultRedisScript<Long> incrIfExistsScript,
            @Qualifier("initBaseAndIncrScript") DefaultRedisScript<Long> initBaseAndIncrScript,
            @Qualifier("initOrMaxAndGetScript") DefaultRedisScript<Long> initOrMaxAndGetScript,
            @Qualifier("maxHsetScript") DefaultRedisScript<Long> maxHsetScript
    ) {
        this.redisTemplate = redisTemplate;
        this.messageMapper = messageMapper;
        this.memberMapper = memberMapper;
        this.redissonClient = redissonClient;
        this.incrIfExistsScript = incrIfExistsScript;
        this.initBaseAndIncrScript = initBaseAndIncrScript;
        this.initOrMaxAndGetScript = initOrMaxAndGetScript;
        this.maxHsetScript = maxHsetScript;
    }

    private String lastSeqKey(Long convId) {
        return "im:conv:last_seq:" + convId;
    }

    private String readHashKey(Long userId) {
        return "im:read:" + userId;
    }

    private String seqLockKey(Long convId) {
        return "lock:im:seq:" + convId;
    }

    private String readLockKey(Long userId) {
        return "lock:im:read:" + userId;
    }

    /**
     * 分配会话内递增 seq：
     * 1) Redis 正常：仅当 key 存在才 INCR
     * 2) Redis 故障/ key 丢失：分布式锁串行 + DB max(seq) 保底，并尽量回填 Redis
     */
    public long nextSeq(Long convId) {
        if (convId == null) {
            throw new IllegalArgumentException("convId is null");
        }
        String key = lastSeqKey(convId);

        // 优先 Redis, 存在才 INCR
        Long r;
        try {
            r = redisTemplate.execute(
                    incrIfExistsScript,
                    Collections.singletonList(key),
                    CURSOR_TTL_SECONDS
            );
        } catch (Exception e) {
            r = null;
        }
        if (r != null && r > 0) {
            return r;
        }

        log.info("Try to next seq from DB, convId:{}", convId);

        RLock lock = redissonClient.getLock(seqLockKey(convId));
        boolean locked = false;

        try {
            locked = lock.tryLock(2, 15, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("Seq lock busy, please retry");
            }

            // 锁内 double-check
            Long again;
            try {
                again = redisTemplate.execute(
                        incrIfExistsScript,
                        Collections.singletonList(key),
                        CURSOR_TTL_SECONDS
                );
            } catch (Exception e) {
                again = null;
            }
            if (again != null && again > 0) return again;

            // DB 保底 max(seq) + 1
            Long dbMaxObj = messageMapper.findMaxSeqByConvId(convId);
            long dbMax = dbMaxObj == null ? 0L : dbMaxObj;

            try {
                Long v = redisTemplate.execute(
                        initBaseAndIncrScript,
                        Collections.singletonList(key),
                        String.valueOf(dbMax),
                        CURSOR_TTL_SECONDS
                );
                if (v != null && v > 0) {
                    return v;
                }
            } catch (Exception ignore) {
            }

            // Redis 仍不可用
            return dbMax + 1;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("seq lock interrupted");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 获取会话最新 seq（lastSeq）：
     * 1) Redis 优先
     * 2) Redis miss/故障：DB max(seq) 兜底，并回填 Redis（只允许增大，不允许倒退）
     */
    public long getLastSeq(Long convId) {
        if (convId == null) {
            throw new IllegalArgumentException("convId is null");
        }
        String key = lastSeqKey(convId);

        // Redis 读
        try {
            String v = redisTemplate.opsForValue().get(key);
            if (v != null) return Math.max(0L, Long.parseLong(v));
        } catch (Exception ignore) {
        }

        log.info("try to get last seq from DB, convId:{}", convId);

        // redis key miss/redis故障
        RLock lock = redissonClient.getLock(seqLockKey(convId));
        boolean locked = false;

        try {
            locked = lock.tryLock(2, 15, TimeUnit.SECONDS);

            // 拿不到锁直接 DB 兜底返回
            if (!locked) {
                Long dbMaxObj = messageMapper.findMaxSeqByConvId(convId);
                return dbMaxObj == null ? 0L : dbMaxObj;
            }

            // 锁内 double-check
            try {
                String again = redisTemplate.opsForValue().get(key);
                if (again != null) return Math.max(0L, Long.parseLong(again));
            } catch (Exception ignore) {
            }

            Long dbMaxObj = messageMapper.findMaxSeqByConvId(convId);
            long dbMax = dbMaxObj == null ? 0L : dbMaxObj;

            try {
                Long filled = redisTemplate.execute(
                        initOrMaxAndGetScript,
                        Collections.singletonList(key),
                        String.valueOf(dbMax),
                        CURSOR_TTL_SECONDS
                );
                if (filled != null) return filled;
            } catch (Exception ignore) {
            }

            return dbMax;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("seq lock interrupted");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 推进用户在会话内的已读游标（read cursor）：
     * 1) Redis Lua max(old,new) 优先
     * 2) Redis 故障：DB 兜底（取 DB last_read_seq 与 seq 的 max），并尽量回填 Redis
     */
    public long advanceRead(Long userId, Long convId, long seq) {
        if (userId == null || convId == null) {
            throw new IllegalArgumentException("userId/convId is null");
        }
        if (seq < 0) seq = 0;

        String hk = readHashKey(userId);
        String field = String.valueOf(convId);

        // Redis 推进
        try {
            Long v = redisTemplate.execute(
                    maxHsetScript,
                    Collections.singletonList(hk),
                    field,
                    String.valueOf(seq),
                    CURSOR_TTL_SECONDS
            );
            if (v != null) {
                // todo 扔到MQ去更新DB ReadSeq
                memberMapper.updateReadCursor(convId, userId, v);
                return v;
            }
        } catch (Exception ignore) {
        }

        // DB 兜底
        ConversationMember m = memberMapper.findActive(convId, userId);
        long db = (m == null || m.getLastReadSeq() == null) ? 0L : m.getLastReadSeq();
        long safe = Math.max(db, seq);

        // 回填 Redis
        try {
            Long v2 = redisTemplate.execute(
                    maxHsetScript,
                    Collections.singletonList(hk),
                    field,
                    String.valueOf(safe),
                    CURSOR_TTL_SECONDS
            );
            if (v2 != null) safe = v2;
        } catch (Exception ignore) {
        }
        // todo 扔到MQ去更新DB ReadSeq
        memberMapper.updateReadCursor(convId, userId, safe);

        return safe;
    }

    /**
     * 获取用户在会话内的已读游标 readSeq：
     * 1) Redis 优先
     * 2) Redis miss/故障：DB last_read_seq 兜底，并回填 Redis
     */
    public long getRead(Long userId, Long convId) {
        if (userId == null || convId == null) {
            throw new IllegalArgumentException("userId/convId is null");
        }

        // Redis 读
        try {
            String hashKey = readHashKey(userId);
            String field = String.valueOf(convId);
            Object v = redisTemplate.opsForHash().get(hashKey, field);
            if (v != null) {
                return Math.max(0L, Long.parseLong(String.valueOf(v)));
            }
        } catch (Exception ignore) {
        }

        // redis key miss/redis故障
        RLock lock = redissonClient.getLock(readLockKey(userId));
        boolean locked = false;

        try {
            locked = lock.tryLock(2, 15, TimeUnit.SECONDS);

            if (!locked) {
                ConversationMember m = memberMapper.findActive(convId, userId);
                return (m == null || m.getLastReadSeq() == null) ? 0L : m.getLastReadSeq();
            }

            // 锁内 double-check
            try {
                String hashKey = readHashKey(userId);
                String field = String.valueOf(convId);
                Object again = redisTemplate.opsForHash().get(hashKey, field);
                if (again != null) {
                    return Math.max(0L, Long.parseLong(String.valueOf(again)));
                }
            } catch (Exception ignore) {
            }

            ConversationMember m = memberMapper.findActive(convId, userId);
            long dbRead = (m == null || m.getLastReadSeq() == null) ? 0L : m.getLastReadSeq();

            // 回填 Redis
            try {
                String hk = readHashKey(userId);
                redisTemplate.opsForHash().put(hk, String.valueOf(convId), String.valueOf(dbRead));
                redisTemplate.expire(hk, CURSOR_TTL_SECONDS_LONG, TimeUnit.SECONDS);
            } catch (Exception ignore) {
            }

            return dbRead;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("read lock interrupted");
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 批量获取多个会话的 lastSeq。
     * 优先 Redis 批量读取，miss 再批量查 DB，并回填 Redis。
     *
     * @param convIds 会话 ID 列表
     * @return convId -> lastSeq 映射
     */
    public Map<Long, Long> batchGetLastSeq(List<Long> convIds) {
        Map<Long, Long> result = new HashMap<>();
        if (convIds == null || convIds.isEmpty()) {
            return result;
        }

        List<String> keys = new ArrayList<>(convIds.size());
        for (Long convId : convIds) {
            keys.add(lastSeqKey(convId));
        }

        List<String> values = null;
        try {
            values = redisTemplate.opsForValue().multiGet(keys);
        } catch (Exception ignore) {
        }

        Set<Long> misses = new HashSet<>();
        for (int i = 0; i < convIds.size(); i++) {
            Long convId = convIds.get(i);
            String raw = (values != null && i < values.size()) ? values.get(i) : null;
            if (raw == null) {
                misses.add(convId);
                continue;
            }
            try {
                result.put(convId, Math.max(0L, Long.parseLong(raw)));
            } catch (Exception ignore) {
                misses.add(convId);
            }
        }

        if (!misses.isEmpty()) {
            List<Map<String, Object>> rows = messageMapper.batchFindMaxSeqByConvIds(new ArrayList<>(misses));
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Long convId = Convert.toLong(row.get("convId"));
                    Long maxSeq = Convert.toLong(row.get("maxSeq"));
                    if (convId == null) {
                        continue;
                    }
                    result.put(convId, normalizeNonNegative(maxSeq));
                }
            }
        }

        for (Long convId : misses) {
            result.putIfAbsent(convId, 0L);
        }

        for (Long convId : misses) {
            try {
                redisTemplate.opsForValue().set(lastSeqKey(convId),
                        String.valueOf(result.getOrDefault(convId, 0L)),
                        CURSOR_TTL_SECONDS_LONG,
                        TimeUnit.SECONDS);
            } catch (Exception ignore) {
            }
        }
        return result;
    }

    /**
     * 批量获取用户在多个会话中的 readSeq。
     * 优先 Redis 批量读取，miss 再批量查 DB，并回填 Redis。
     *
     * @param userId  用户 ID
     * @param convIds 会话 ID 列表
     * @return convId -> readSeq 映射
     */
    public Map<Long, Long> batchGetRead(Long userId, List<Long> convIds) {
        Map<Long, Long> result = new HashMap<>();
        if (userId == null || convIds == null || convIds.isEmpty()) {
            return result;
        }

        String hashKey = readHashKey(userId);
        List<String> fields = new ArrayList<>(convIds.size());
        for (Long convId : convIds) {
            fields.add(String.valueOf(convId));
        }

        List<Object> values = null;
        try {
            values = redisTemplate.opsForHash().multiGet(hashKey, new ArrayList<>(fields));
        } catch (Exception ignore) {
        }

        Set<Long> misses = new HashSet<>();
        for (int i = 0; i < convIds.size(); i++) {
            Long convId = convIds.get(i);
            String field = String.valueOf(convId);
            Object raw = (values != null && i < values.size()) ? values.get(i) : null;
            if (raw == null) {
                misses.add(convId);
                continue;
            }
            try {
                result.put(convId, Math.max(0L, Long.parseLong(String.valueOf(raw))));
            } catch (Exception ignore) {
                misses.add(convId);
            }
        }

        if (!misses.isEmpty()) {
            List<Map<String, Object>> rows = memberMapper.batchFindReadSeqByUserAndConvIds(userId, new ArrayList<>(misses));
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    Long convId = Convert.toLong(row.get("convId"));
                    Long readSeq = Convert.toLong(row.get("readSeq"));
                    if (convId == null) {
                        continue;
                    }
                    result.put(convId, normalizeNonNegative(readSeq));
                }
            }
        }

        for (Long convId : misses) {
            result.putIfAbsent(convId, 0L);
        }

        Map<String, String> refill = new HashMap<>();
        for (Long convId : misses) {
            refill.put(String.valueOf(convId), String.valueOf(result.getOrDefault(convId, 0L)));
        }
        if (!refill.isEmpty()) {
            try {
                redisTemplate.opsForHash().putAll(hashKey, refill);
                redisTemplate.expire(hashKey, CURSOR_TTL_SECONDS_LONG, TimeUnit.SECONDS);
            } catch (Exception ignore) {
            }
        }
        return result;
    }

    private long normalizeNonNegative(Long value) {
        if (value == null) {
            return 0L;
        }
        return Math.max(value, 0L);
    }
}
