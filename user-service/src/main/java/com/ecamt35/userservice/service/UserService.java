package com.ecamt35.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecamt35.userservice.model.dto.SignUpDto;
import com.ecamt35.userservice.model.entity.User;
import com.ecamt35.userservice.model.vo.AccountSessionUserVo;

import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;

/**
 * (User)表服务接口
 *
 * @author ECAMT
 * @since 2025-08-15 01:18:57
 */
public interface UserService extends IService<User> {
    void signUp(SignUpDto signupDto) throws NoSuchAlgorithmException;

    Integer signInByUserName(String username, String password) throws NoSuchAlgorithmException;

    Integer signInByEmail(String email, String password) throws NoSuchAlgorithmException;

    List<String> getRoleNameList(Integer userId) throws InterruptedException;

    Map<String, String> getRoleMap() throws InterruptedException;

    AccountSessionUserVo getAccountSessionUser(Integer userId);
}
