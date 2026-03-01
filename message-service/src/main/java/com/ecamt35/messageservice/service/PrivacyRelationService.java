package com.ecamt35.messageservice.service;

import cn.hutool.core.convert.Convert;
import com.ecamt35.messageservice.mapper.UserPrivacySettingMapper;
import com.ecamt35.messageservice.model.entity.UserPrivacySetting;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PrivacyRelationService {

    private final UserPrivacySettingMapper userPrivacySettingMapper;
    private final RelationPermissionService relationPermissionService;

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

        relationPermissionService.evictPrivacy(userId);
        return Map.of("allowStrangerChat", allow);
    }

    /**
     * 获取当前用户隐私设置。
     */
    public Map<String, Object> privacyGetSettings(Long userId) {
        boolean allow = relationPermissionService.isAllowStrangerConversation(userId);
        return Map.of("allowStrangerChat", allow);
    }
}
