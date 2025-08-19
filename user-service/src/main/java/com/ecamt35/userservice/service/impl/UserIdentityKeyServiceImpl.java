package com.ecamt35.userservice.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecamt35.userservice.mapper.UserIdentityKeyMapper;
import com.ecamt35.userservice.model.dto.UpdateIKDto;
import com.ecamt35.userservice.model.entity.UserIdentityKey;
import com.ecamt35.userservice.service.UserIdentityKeyService;
import com.ecamt35.userservice.service.UserSignedPreKeyService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * (UserIdentityKey)表服务实现类
 *
 * @author ECAMT
 * @since 2025-08-17 18:07:51
 */
@Service
public class UserIdentityKeyServiceImpl extends ServiceImpl<UserIdentityKeyMapper, UserIdentityKey> implements UserIdentityKeyService {

    @Resource
    private UserSignedPreKeyService userSignedPreKeyService;
    @Resource
    private UserIdentityKeyMapper userIdentityKeyMapper;

    @Override
    @Transactional
    public boolean updateUserIdentityKey(UpdateIKDto updateIKDto) {
        Integer userId = StpUtil.getLoginIdAsInt();
        if (userId == null) {
            return false;
        }
        userIdentityKeyMapper.updateUserPublicKey(updateIKDto.getIdentityKey(), userId);
        userSignedPreKeyService.updateUserSignedPreKey(updateIKDto.getSignedPreKey());

        return true;
    }

    @Override
    public String getIKByUserId(Integer userId) {
        return userIdentityKeyMapper.getPublicKeyByUserId(userId);
    }
}
