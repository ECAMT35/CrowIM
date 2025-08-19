package com.ecamt35.userservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecamt35.userservice.mapper.UserOneTimePreKeyMapper;
import com.ecamt35.userservice.model.bo.OPKBo;
import com.ecamt35.userservice.model.entity.UserOneTimePreKey;
import com.ecamt35.userservice.service.UserOneTimePreKeyService;
import com.ecamt35.userservice.util.BusinessException;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * (UserOneTimePreKey)表服务实现类
 *
 * @author ECAMT
 * @since 2025-08-17 18:14:38
 */
@Service
@Slf4j
public class UserOneTimePreKeyServiceImpl extends ServiceImpl<UserOneTimePreKeyMapper, UserOneTimePreKey> implements UserOneTimePreKeyService {

    @Resource
    private UserOneTimePreKeyMapper userOneTimePreKeyMapper;

    @Resource
    private RedisTemplate redisTemplate;

    private static final int MAX_RETRY = 100;
    private static final long LOCK_SECONDS = 10; // 延长锁时间

    @Override
    public String getOPKByUserId(Integer userId) {

        return getAvailableOpkRecursive(userId, null, 0);
    }

    /**
     * 递归查询：跳过已锁定的OPK，直到找到可用的
     *
     * @param userId     用户ID
     * @param skipId     需要跳过的OPK ID（首次调用为null）
     * @param retryCount 当前重试次数
     */
    private String getAvailableOpkRecursive(Integer userId, Integer skipId, int retryCount) {
        // 终止条件：超过最大重试次数
        if (retryCount >= MAX_RETRY) {
            log.warn("获取OPK失败，已达最大重试次数: userId={}", userId);
            throw new BusinessException("获取OPK失败，已达最大重试次数");
        }

        // 1. 查询候选OPK（首次查第一个，后续查跳过skipId的下一个）

        OPKBo opkBo;
        if (skipId == null) {
            opkBo = userOneTimePreKeyMapper.getPublicKeyByUserId(userId);
        } else {
            opkBo = userOneTimePreKeyMapper.getPublicKeyByUserIdAndId(userId, skipId);
        }

        // 无可用OPK
        if (opkBo == null) {
            return null;
        }

        // 2. 检查Redis中是否有该OPK的锁
        String lockKey = "opk:lock:" + opkBo.getId();
        Boolean isLocked = redisTemplate.opsForValue().setIfAbsent(
                lockKey,
                "",
                LOCK_SECONDS,
                TimeUnit.SECONDS
        );


        if (Boolean.FALSE.equals(isLocked)) {
            // 3. 已被锁定，递归查询下一个（跳过当前ID）
            return getAvailableOpkRecursive(userId, opkBo.getId(), retryCount + 1);
        } else {
            // 4. 未被锁定，自己已在上面进行了锁定，接下来需要更新为已使用
            int isUsed = userOneTimePreKeyMapper.markIsUsed(opkBo.getId());
            if (isUsed == 0) {
                return getAvailableOpkRecursive(userId, opkBo.getId(), retryCount + 1);
            }
            return opkBo.getPublicKey();
        }
    }
}
