package com.ecamt35.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecamt35.userservice.model.dto.UpdateIKDto;
import com.ecamt35.userservice.model.entity.UserIdentityKey;

/**
 * (UserIdentityKey)表服务接口
 *
 * @author ECAMT
 * @since 2025-08-17 18:07:51
 */
public interface UserIdentityKeyService extends IService<UserIdentityKey> {
    boolean updateUserIdentityKey(UpdateIKDto updateIKDto);

    String getIKByUserId(Integer userId);
}
