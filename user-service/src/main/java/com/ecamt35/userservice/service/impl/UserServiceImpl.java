package com.ecamt35.userservice.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecamt35.userservice.constant.CacheConstant;
import com.ecamt35.userservice.constant.CaptchaCacheConstant;
import com.ecamt35.userservice.mapper.UserMapper;
import com.ecamt35.userservice.model.bo.RoleBo;
import com.ecamt35.userservice.model.bo.UserLiteBo;
import com.ecamt35.userservice.model.dto.SignUpDto;
import com.ecamt35.userservice.model.entity.User;
import com.ecamt35.userservice.model.entity.UserIdentityKey;
import com.ecamt35.userservice.model.entity.UserOneTimePreKey;
import com.ecamt35.userservice.model.entity.UserSignedPreKey;
import com.ecamt35.userservice.model.vo.AccountSessionUserVo;
import com.ecamt35.userservice.service.UserIdentityKeyService;
import com.ecamt35.userservice.service.UserOneTimePreKeyService;
import com.ecamt35.userservice.service.UserService;
import com.ecamt35.userservice.service.UserSignedPreKeyService;
import com.ecamt35.userservice.util.BusinessErrorCodeEnum;
import com.ecamt35.userservice.util.BusinessException;
import com.ecamt35.userservice.util.PasswordUtil;
import com.ecamt35.userservice.util.RedisStrategyComponent;
import jakarta.annotation.Resource;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * (User)表服务实现类
 *
 * @author ECAMT
 * @since 2025-08-15 01:12:25
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Resource
    private UserMapper userMapper;
    @Resource
    private RedisStrategyComponent redisStrategyComponent;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private UserIdentityKeyService userIdentityKeyService;
    @Resource
    private UserOneTimePreKeyService userOneTimePreKeyService;
    @Resource
    private UserSignedPreKeyService userSignedPreKeyService;

    @Override
    @Transactional
    public void signUp(SignUpDto signupDto) throws NoSuchAlgorithmException {
        String userName = signupDto.getUserName();
        if (userMapper.getIdByUserName(userName) != null) {
            throw new BusinessException(BusinessErrorCodeEnum.SIGNUP_USERNAME_ERROR);
        }

        String email = signupDto.getEmail();
        if (userMapper.getIdByEmail(email) != null) {
            throw new BusinessException(BusinessErrorCodeEnum.SIGNUP_EMAIL_ERROR);
        }

        // 验证码
        String captcha = signupDto.getCaptcha();
        String key = CaptchaCacheConstant.SIGNUP_MAIL_PREFIX + email;
        String code = stringRedisTemplate.opsForValue().get(key);
        if (!captcha.equals(code)) {
            throw new BusinessException(BusinessErrorCodeEnum.SIGNUP_CAPTCHA_ERROR);
        }

        User user = new User();
        user.setUserName(signupDto.getUserName());
        user.setPassword(PasswordUtil.encrypt(signupDto.getPassword()));
        user.setEmail(signupDto.getEmail());
        this.save(user);

        // IK
        UserIdentityKey userIdentityKey = new UserIdentityKey();
        userIdentityKey.setUserId(user.getId());
        userIdentityKey.setPublicKey(signupDto.getIdentityKey());
        userIdentityKeyService.save(userIdentityKey);
        // SPK
        UserSignedPreKey userSignedPreKey = new UserSignedPreKey();
        userSignedPreKey.setUserId(user.getId());
        userSignedPreKey.setPublicKey(signupDto.getSignedPreKey());
        userSignedPreKeyService.save(userSignedPreKey);
        // OPK
        List<UserOneTimePreKey> userOneTimePreKeys = new ArrayList<>();
        for (String opk : signupDto.getOneTimePreKey()) {
            UserOneTimePreKey userOneTimePreKey = new UserOneTimePreKey();
            userOneTimePreKey.setUserId(user.getId());
            userOneTimePreKey.setPublicKey(opk);
            userOneTimePreKeys.add(userOneTimePreKey);
        }
        userOneTimePreKeyService.saveBatch(userOneTimePreKeys);

    }

    @Override
    public Integer signInByUserName(String userName, String password) throws NoSuchAlgorithmException {
        UserLiteBo userLiteBo = userMapper.signInByUserName(userName);
        if (userLiteBo == null) {
            return null;
        }
        boolean verified = PasswordUtil.verify(password, userLiteBo.getPassword());
        if (verified) {
            return userLiteBo.getId();
        }
        return null;
    }

    @Override
    public Integer signInByEmail(String email, String password) throws NoSuchAlgorithmException {
        UserLiteBo userLiteBo = userMapper.signInByEmail(email);
        if (userLiteBo == null) {
            return null;
        }
        boolean verified = PasswordUtil.verify(password, userLiteBo.getPassword());
        if (verified) {
            return userLiteBo.getId();
        }
        return null;
    }

    // 返回向下的role, 用于网关的鉴权
    @Override
    public List<String> getRoleNameList(Integer userId) throws InterruptedException {
        Map<String, String> roleMap = this.getRoleMap();
        List<String> realRoles = new ArrayList<>();
        Integer roleId = this.getAccountSessionUser(userId).getRoleId();
        roleMap.forEach((key, value) -> {
            if (Integer.parseInt(key) <= roleId) {
                realRoles.add(value);
            }
        });
        return realRoles;
    }

    // 往 session 加一些个人公开的信息
    @Override
    public AccountSessionUserVo getAccountSessionUser(Integer userId) {
        SaSession session = StpUtil.getSessionByLoginId(userId);
        AccountSessionUserVo accountSessionUser = (AccountSessionUserVo) session.get(CacheConstant.ACCOUNT_SESSION_USER_MSG);
        if (accountSessionUser == null) {
            accountSessionUser = new AccountSessionUserVo();
            BeanUtils.copyProperties(this.getById(userId), accountSessionUser);
            session.set(CacheConstant.ACCOUNT_SESSION_USER_MSG, accountSessionUser);
        }
        return accountSessionUser;
    }

    @Override
    public Map<String, String> getRoleMap() throws InterruptedException {
        String key = CacheConstant.ROLE_NAME_MAP;
        return redisStrategyComponent.forHash(key, () -> getRoleMapDB(), 10L, TimeUnit.DAYS);
    }

    // 从数据库获取 role 表所有角色
    private Map<String, String> getRoleMapDB() {
        Map<String, String> roleMap = new HashMap<>();
        for (RoleBo role : userMapper.getAllRoles()) {
            roleMap.put(String.valueOf(role.getId()), role.getRoleName());
        }
        return roleMap;
    }
}