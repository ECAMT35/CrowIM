package com.ecamt35.userservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecamt35.userservice.model.bo.RoleBo;
import com.ecamt35.userservice.model.bo.UserLiteBo;
import com.ecamt35.userservice.model.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("select id from user where user_name = #{userName}")
    Integer getIdByUserName(String userName);

    @Select("select id from user where email = #{email}")
    Integer getIdByEmail(String email);

    @Select("select id, password from user where user_name = #{userName}")
    UserLiteBo signInByUserName(String userName);

    @Select("select id, password from user where email = #{email}")
    UserLiteBo signInByEmail(String email);

    @Select("select id, role_name as roleName from role")
    List<RoleBo> getAllRoles();
}
