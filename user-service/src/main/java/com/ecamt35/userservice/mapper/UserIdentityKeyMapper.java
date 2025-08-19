package com.ecamt35.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.userservice.model.entity.UserIdentityKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * (UserIdentityKey)数据库访问层
 *
 * @author ECAMT
 * @since 2025-08-17 18:07:51
 */
@Mapper
public interface UserIdentityKeyMapper extends BaseMapper<UserIdentityKey> {

    @Update("update user_identity_key set public_key = #{publicKey}, update_time=now() where user_id = #{userId}")
    void updateUserPublicKey(String publicKey, Integer userId);

    @Select("select public_key from user_identity_key where user_id=#{userId}")
    String getPublicKeyByUserId(Integer userId);
}

