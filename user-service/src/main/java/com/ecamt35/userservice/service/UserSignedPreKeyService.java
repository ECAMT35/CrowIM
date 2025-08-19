package com.ecamt35.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecamt35.userservice.model.entity.UserSignedPreKey;

/**
 * (UserSignedPreKey)表服务接口
 *
 * @author ECAMT
 * @since 2025-08-17 18:19:42
 */
public interface UserSignedPreKeyService extends IService<UserSignedPreKey> {
    boolean updateUserSignedPreKey(String publicKey);

    String getSPKByUserId(Integer userId);
}
