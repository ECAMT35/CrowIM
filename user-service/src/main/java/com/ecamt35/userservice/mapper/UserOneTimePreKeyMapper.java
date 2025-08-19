package com.ecamt35.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.userservice.model.bo.OPKBo;
import com.ecamt35.userservice.model.entity.UserOneTimePreKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * (UserOneTimePreKey)数据库访问层
 *
 * @author ECAMT
 * @since 2025-08-17 18:14:38
 */
@Mapper
public interface UserOneTimePreKeyMapper extends BaseMapper<UserOneTimePreKey> {
    @Select("select id, public_key from user_one_time_pre_key where user_id=#{userId} and is_used =0 limit 1")
    OPKBo getPublicKeyByUserId(Integer userId);

    @Select("select id, public_key from user_one_time_pre_key where user_id=#{userId} and is_used =0 and id>#{skipId} limit 1")
    OPKBo getPublicKeyByUserIdAndId(Integer userId, Integer skipId);

    @Update("update user_one_time_pre_key set is_used=1, update_time=now() where id=#{id} and is_used=0")
    int markIsUsed(Integer id);

}


