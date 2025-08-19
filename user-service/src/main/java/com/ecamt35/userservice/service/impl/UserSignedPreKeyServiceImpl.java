package com.ecamt35.userservice.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecamt35.userservice.mapper.UserSignedPreKeyMapper;
import com.ecamt35.userservice.model.entity.UserSignedPreKey;
import com.ecamt35.userservice.service.UserSignedPreKeyService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * (UserSignedPreKey)表服务实现类
 *
 * @author ECAMT
 * @since 2025-08-17 18:19:42
 */
@Service
public class UserSignedPreKeyServiceImpl extends ServiceImpl<UserSignedPreKeyMapper, UserSignedPreKey> implements UserSignedPreKeyService {

    @Resource
    private UserSignedPreKeyMapper userSignedPreKeyMapper;

    @Override
    public boolean updateUserSignedPreKey(String publicKey) {
        Integer userId = StpUtil.getLoginIdAsInt();
        if (userId == null) {
            return false;
        }
        userSignedPreKeyMapper.updateUserPublicKey(publicKey, userId);
        return true;
    }

    @Override
    public String getSPKByUserId(Integer userId) {
        return userSignedPreKeyMapper.getPublicKeyByUserId(userId);
    }
}
