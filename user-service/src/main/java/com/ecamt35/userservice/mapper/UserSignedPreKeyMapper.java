package com.ecamt35.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.userservice.model.entity.UserSignedPreKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * (UserSignedPreKey)数据库访问层
 *
 * @author ECAMT
 * @since 2025-08-17 18:19:42
 */
@Mapper
public interface UserSignedPreKeyMapper extends BaseMapper<UserSignedPreKey> {

    @Update("update user_signed_pre_key set public_key = #{publicKey}, update_time=now() where user_id = #{userId}")
    void updateUserPublicKey(String publicKey, Integer userId);

    @Select("select public_key from user_signed_pre_key where user_id=#{userId}")
    String getPublicKeyByUserId(Integer userId);
}

