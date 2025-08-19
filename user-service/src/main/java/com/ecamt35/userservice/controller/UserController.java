package com.ecamt35.userservice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ecamt35.userservice.model.dto.SignInDto;
import com.ecamt35.userservice.model.dto.SignUpDto;
import com.ecamt35.userservice.model.vo.AccountSessionUserVo;
import com.ecamt35.userservice.service.EmailService;
import com.ecamt35.userservice.service.UserService;
import com.ecamt35.userservice.util.AppHttpCodeEnum;
import com.ecamt35.userservice.util.CaptchaType;
import com.ecamt35.userservice.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.mail.MessagingException;
import org.springframework.web.bind.annotation.*;

import java.security.NoSuchAlgorithmException;

@Tag(name = "用户", description = "")
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private EmailService emailService;


    @Operation(summary = "用户登录")
    @PostMapping("/signin")
    public Result signIn(@RequestBody SignInDto signInDto) throws NoSuchAlgorithmException {

        if (signInDto.getPassword() == null || signInDto.getPassword().isEmpty()) {
            return Result.fail("密码不能为空");
        }

        Integer userId;
        if ((signInDto.getUserName() == null || signInDto.getUserName().isEmpty()) &&
                (signInDto.getEmail() == null || signInDto.getEmail().isEmpty())) {
            return Result.fail("用户名或邮箱不能同时为空");
        } else if (signInDto.getUserName() != null && !signInDto.getUserName().isEmpty() &&
                signInDto.getEmail() != null && !signInDto.getEmail().isEmpty()) {
            return Result.fail("用户名和邮箱不能同时存在");
        }

        // 只有用户名或者邮箱其中一个为空或者不为空
        if (signInDto.getUserName() == null || signInDto.getUserName().isEmpty()) {
            userId = userService.signInByEmail(signInDto.getEmail(), signInDto.getPassword());
        } else {
            userId = userService.signInByUserName(signInDto.getUserName(), signInDto.getPassword());

        }

        if (userId == null) {
            return Result.fail("用户名或密码错误");
        }

        StpUtil.login(userId, signInDto.isRememberMe());
        return Result.success(userId);
    }

    @Operation(summary = "用户登出")
    @GetMapping("/logout")
    public Result logout() {
        StpUtil.logout();
        return Result.success("登出成功");
    }

    @PostMapping("/signup")
    @Operation(summary = "用户注册")
    public Result signUp(@RequestBody SignUpDto signUpDto) throws NoSuchAlgorithmException {

        if (signUpDto.getUserName() == null || signUpDto.getUserName().isEmpty()) {
            return Result.fail("用户名不能为空");
        }

        if (signUpDto.getPassword() == null || signUpDto.getPassword().isEmpty()) {
            return Result.fail("密码不能为空");
        }

        if (signUpDto.getUserName().length() > 24 || signUpDto.getPassword().length() > 20) {
            return Result.fail("用户名或密码长度不合法");
        }

        if (signUpDto.getEmail() == null || signUpDto.getEmail().isEmpty()) {
            return Result.fail("邮箱不能为空");
        }

        userService.signUp(signUpDto);
        return Result.success("注册成功");
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result getAccountSessionUserVo() {
        int userId = StpUtil.getLoginIdAsInt();
        AccountSessionUserVo accountSessionUserVo = userService.getAccountSessionUser(userId);
        return Result.success(accountSessionUserVo);
    }

    @Operation(summary = "发送注册验证码")
    @GetMapping("/send_signup_captcha")
    public Result sendSignupCaptcha(@RequestParam String email) throws MessagingException {

        if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+(\\.[a-zA-Z0-9_-]+)+$")) {
            return Result.fail("邮箱格式错误");
        }
        boolean b = emailService.sendCaptchaMail(CaptchaType.REGISTER, email);
        if (!b) {
            return Result.fail(AppHttpCodeEnum.FREQUENT_REQUEST);
        }
        return Result.success();
    }
}
