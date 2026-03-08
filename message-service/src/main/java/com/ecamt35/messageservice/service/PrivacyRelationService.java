package com.ecamt35.messageservice.service;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.constant.RelationCacheKeyConstant;
import com.ecamt35.messageservice.mapper.UserPrivacySettingMapper;
import com.ecamt35.messageservice.model.entity.UserPrivacySetting;
import com.ecamt35.messageservice.util.RedisCacheClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrivacyRelationService {

    private static final Duration PRIVACY_TTL = Duration.ofDays(7);

    private final UserPrivacySettingMapper userPrivacySettingMapper;
    private final RedisCacheClient cacheClient;

    /**
     * 设置是否允许陌生人发起聊天。
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> privacySetStrangerChat(Long userId, Map<String, Object> payload) {
        boolean allow = Convert.toBool(payload.get("allow"), false);

        UserPrivacySetting setting = userPrivacySettingMapper.selectById(userId);
        if (setting == null) {
            setting = new UserPrivacySetting();
            setting.setUserId(userId);
            setting.setAllowStrangerChat(allow ? 1 : 0);
            setting.setDeleted(0);
            userPrivacySettingMapper.insert(setting);
        } else {
            setting.setAllowStrangerChat(allow ? 1 : 0);
            setting.setDeleted(0);
            userPrivacySettingMapper.updateById(setting);
        }

        evictPrivacyAfterCommit(userId);
        return Map.of("allowStrangerChat", allow);
    }

    /**
     * 获取当前用户隐私设置。
     */
    public Map<String, Object> privacyGetSettings(Long userId) {
        boolean allow = isAllowStrangerConversation(userId);
        return Map.of("allowStrangerChat", allow);
    }

    /**
     * 是否允许陌生人发起私聊（缓存优先）。
     */
    public boolean isAllowStrangerConversation(Long userId) {
        if (userId == null) {
            return false;
        }
        UserPrivacySetting setting = cacheClient.getOrLoadById(
                RelationCacheKeyConstant.PRIVACY_PREFIX,
                userId,
                PRIVACY_TTL,
                UserPrivacySetting.class,
                userPrivacySettingMapper::selectById
        );
        if (setting == null) {
            return false;
        }
        return setting.getAllowStrangerChat() != null && setting.getAllowStrangerChat() == 1;
    }

    /**
     * 立即失效隐私缓存。
     */
    public void evictPrivacy(Long userId) {
        if (userId == null) {
            return;
        }
        cacheClient.evict(RelationCacheKeyConstant.PRIVACY_PREFIX + userId);
    }

    /**
     * 在事务提交后失效隐私缓存，避免未提交事务触发脏回填。
     */
    public void evictPrivacyAfterCommit(Long userId) {
        if (userId == null) {
            return;
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            Long uid = userId;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    cacheClient.evict(RelationCacheKeyConstant.PRIVACY_PREFIX + uid);
                }
            });
            return;
        }
        cacheClient.evict(RelationCacheKeyConstant.PRIVACY_PREFIX + userId);
    }
}
