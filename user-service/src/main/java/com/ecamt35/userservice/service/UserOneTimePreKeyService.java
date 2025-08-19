package com.ecamt35.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecamt35.userservice.model.entity.UserOneTimePreKey;

/**
 * (UserOneTimePreKey)表服务接口
 *
 * @author ECAMT
 * @since 2025-08-17 18:14:38
 */
public interface UserOneTimePreKeyService extends IService<UserOneTimePreKey> {

    String getOPKByUserId(Integer userId);
}
