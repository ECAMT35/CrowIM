package com.ecamt35.userservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ecamt35.userservice.model.dto.SignInDto;
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

    Long signIn(SignInDto signInDto) throws NoSuchAlgorithmException;

    void logout(String deviceId);

    List<String> getRoleNameList(Long userId) throws InterruptedException;

    Map<String, String> getRoleMap() throws InterruptedException;

    AccountSessionUserVo getAccountSessionUser(Long userId);
}
