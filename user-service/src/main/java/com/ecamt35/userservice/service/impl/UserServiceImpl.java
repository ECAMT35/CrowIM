package com.ecamt35.userservice.service.impl;

import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ecamt35.userservice.constant.CacheConstant;
import com.ecamt35.userservice.constant.CaptchaCacheConstant;
import com.ecamt35.userservice.constant.UserConstant;
import com.ecamt35.userservice.mapper.UserMapper;
import com.ecamt35.userservice.model.bo.RoleBo;
import com.ecamt35.userservice.model.bo.UserLiteBo;
import com.ecamt35.userservice.model.dto.SignInDto;
import com.ecamt35.userservice.model.dto.SignUpDto;
import com.ecamt35.userservice.model.entity.User;
import com.ecamt35.userservice.model.vo.AccountSessionUserVo;
import com.ecamt35.userservice.service.UserIdentityKeyService;
import com.ecamt35.userservice.service.UserOneTimePreKeyService;
import com.ecamt35.userservice.service.UserService;
import com.ecamt35.userservice.service.UserSignedPreKeyService;
import com.ecamt35.userservice.util.BusinessErrorCodeEnum;
import com.ecamt35.userservice.util.BusinessException;
import com.ecamt35.userservice.util.PasswordUtil;
import com.ecamt35.userservice.util.RedisStrategyComponent;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * (User)表服务实现类
 *
 * @author ECAMT
 * @since 2025-08-15 01:12:25
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final UserMapper userMapper;
    private final RedisStrategyComponent redisStrategyComponent;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserIdentityKeyService userIdentityKeyService;
    private final UserOneTimePreKeyService userOneTimePreKeyService;
    private final UserSignedPreKeyService userSignedPreKeyService;
    private final Snowflake snowflake;
    private final RedissonClient redissonClient;


    public UserServiceImpl(UserMapper userMapper,
                           RedisStrategyComponent redisStrategyComponent,
                           RedisTemplate<String, Object> redisTemplate,
                           UserIdentityKeyService userIdentityKeyService,
                           UserOneTimePreKeyService userOneTimePreKeyService,
                           UserSignedPreKeyService userSignedPreKeyService,
                           Snowflake snowflake,
                           RedissonClient redissonClient) {
        this.userMapper = userMapper;
        this.redisStrategyComponent = redisStrategyComponent;
        this.redisTemplate = redisTemplate;
        this.userIdentityKeyService = userIdentityKeyService;
        this.userOneTimePreKeyService = userOneTimePreKeyService;
        this.userSignedPreKeyService = userSignedPreKeyService;
        this.snowflake = snowflake;
        this.redissonClient = redissonClient;

    }


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
        String code = Objects.requireNonNull(redisTemplate.opsForValue().get(key)).toString();
        if (!captcha.equals(code)) {
            throw new BusinessException(BusinessErrorCodeEnum.SIGNUP_CAPTCHA_ERROR);
        }

        long id = snowflake.nextId();
        User user = new User();
        user.setId(id);
        user.setUserName(signupDto.getUserName());
        user.setPassword(PasswordUtil.encrypt(signupDto.getPassword()));
        user.setEmail(signupDto.getEmail());
        this.save(user);

        // todo 适配密钥部分
//        // IK
//        UserIdentityKey userIdentityKey = new UserIdentityKey();
//        userIdentityKey.setUserId(user.getId());
//        userIdentityKey.setPublicKey(signupDto.getIdentityKey());
//        userIdentityKeyService.save(userIdentityKey);
//        // SPK
//        UserSignedPreKey userSignedPreKey = new UserSignedPreKey();
//        userSignedPreKey.setUserId(user.getId());
//        userSignedPreKey.setPublicKey(signupDto.getSignedPreKey());
//        userSignedPreKeyService.save(userSignedPreKey);
//        // OPK
//        List<UserOneTimePreKey> userOneTimePreKeys = new ArrayList<>();
//        for (String opk : signupDto.getOneTimePreKey()) {
//            UserOneTimePreKey userOneTimePreKey = new UserOneTimePreKey();
//            userOneTimePreKey.setUserId(user.getId());
//            userOneTimePreKey.setPublicKey(opk);
//            userOneTimePreKeys.add(userOneTimePreKey);
//        }
//        userOneTimePreKeyService.saveBatch(userOneTimePreKeys);

    }

    @Override
    public Long signIn(SignInDto signInDto) throws NoSuchAlgorithmException {

        String userName = signInDto.getUserName();
        String email = signInDto.getEmail();
        String password = signInDto.getPassword();

        Long userId;
        if (userName == null || userName.isEmpty()) {
            userId = this.signInByEmail(email, password);
        } else {
            userId = this.signInByUserName(userName, password);
        }
        if (userId == null) {
            return null;
        }

        String deviceId = signInDto.getDeviceId();

        String lockKey = "user:login:lock:" + userId;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {

            locked = lock.tryLock(3, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException("Too much signIn");
            }

            SaSession session = StpUtil.getSessionByLoginId(userId, false);
            List<SaTerminalInfo> terminals = (session != null) ? session.getTerminalList() : Collections.emptyList();
            int currentCount = terminals.size();

            // 检查当前设备是否已在线
            boolean deviceAlreadyOnline = false;
            SaTerminalInfo existingTerminal = null;
            for (SaTerminalInfo t : terminals) {
                if (deviceId.equals(t.getDeviceId())) {
                    deviceAlreadyOnline = true;
                    existingTerminal = t;
                    break;
                }
            }

            if (deviceAlreadyOnline) {
                // 设备已在线, 先注销旧 token, 清理业务 Redis
                logoutDevice(userId, existingTerminal.getTokenValue(), deviceId);

            } else {
                // 设备未在线，检查是否达到上限
                if (currentCount >= 2) {
                    // 踢掉最早登录的设备
                    SaTerminalInfo oldest = terminals.stream()
                            .min(Comparator.comparingLong(SaTerminalInfo::getCreateTime))
                            .orElseThrow(() -> new BusinessException("no device"));
                    logoutDevice(userId, oldest.getTokenValue(), oldest.getDeviceId());
                }
            }

            // 执行登录
            StpUtil.login(userId, new SaLoginParameter()
                    .setDeviceId(deviceId)
                    .setIsLastingCookie(signInDto.isRememberMe())
            );

            // 获取新 token，更新业务 Redis
            String tokenValue = StpUtil.getTokenValue();
            String key = UserConstant.USER_DEVICES + userId;
            redisTemplate.opsForHash().put(key, deviceId, tokenValue);

            return userId;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("锁等待被中断");
        } finally {
            if (locked) {
                lock.unlock();
            }
        }

    }

    @Override
    public void logout(String deviceId) {

        long userId = StpUtil.getLoginIdAsLong();

        // 遍历终端列表，找到目标 deviceId 对应的 token
        SaSession session = StpUtil.getSession();
        String targetToken = null;
        for (SaTerminalInfo terminal : session.getTerminalList()) {
            if (deviceId.equals(terminal.getDeviceId())) {
                targetToken = terminal.getTokenValue();
                break;
            }
        }

        if (targetToken == null) {
            throw new BusinessException("can't find device token");
        }

        logoutDevice(userId, targetToken, deviceId);

    }

    /**
     * 校验密码, user_name
     */
    public Long signInByUserName(String userName, String password) throws NoSuchAlgorithmException {
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

    /**
     * 校验密码, email
     */
    public Long signInByEmail(String email, String password) throws NoSuchAlgorithmException {
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
    public List<String> getRoleNameList(Long userId) throws InterruptedException {
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
    public AccountSessionUserVo getAccountSessionUser(Long userId) {
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

    /**
     * 私有方法：注销指定设备
     *
     * @param userId     用户ID
     * @param tokenValue 要注销的 token
     * @param deviceId   设备ID
     */
    private void logoutDevice(Long userId, String tokenValue, String deviceId) {
        StpUtil.logoutByTokenValue(tokenValue);
        String key = UserConstant.USER_DEVICES + userId;
        redisTemplate.opsForHash().delete(key, deviceId);
    }


}