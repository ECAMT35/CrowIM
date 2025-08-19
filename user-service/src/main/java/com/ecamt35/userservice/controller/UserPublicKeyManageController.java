package com.ecamt35.userservice.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ecamt35.userservice.model.dto.OPKListDto;
import com.ecamt35.userservice.model.dto.UpdateIKDto;
import com.ecamt35.userservice.model.dto.UpdateSPKDto;
import com.ecamt35.userservice.model.entity.UserOneTimePreKey;
import com.ecamt35.userservice.model.vo.PublicKeysVo;
import com.ecamt35.userservice.service.UserIdentityKeyService;
import com.ecamt35.userservice.service.UserOneTimePreKeyService;
import com.ecamt35.userservice.service.UserSignedPreKeyService;
import com.ecamt35.userservice.util.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "用户密钥管理", description = "")
@RestController
@RequestMapping("/public_keys")
public class UserPublicKeyManageController {
    @Resource
    private UserIdentityKeyService userIdentityKeyService;
    @Resource
    private UserOneTimePreKeyService userOneTimePreKeyService;
    @Resource
    private UserSignedPreKeyService userSignedPreKeyService;

    @Operation(summary = "更换IK")
    @PostMapping("/update_IK")
    public Result updateIK(@RequestBody UpdateIKDto updateIKDto) {

        if (userIdentityKeyService.updateUserIdentityKey(updateIKDto)) {
            return Result.success();
        }
        return Result.fail("更换密钥失败");
    }


    @Operation(summary = "更换SPK")
    @PostMapping("/update_SPK")
    public Result updateSPK(@RequestBody UpdateSPKDto updateSPKDto) {
        if (userSignedPreKeyService.updateUserSignedPreKey(updateSPKDto.getSignedPreKey())) {
            return Result.success();
        }
        return Result.fail("更换密钥失败");
    }


    @Operation(summary = "补充OPK")
    @PostMapping("/add_OPK")
    public Result addOPK(@RequestBody OPKListDto opkListDto) {

        // todo
        // 最好的是先获取有限的OPK数量，避免过多补充
        Integer userId = StpUtil.getLoginIdAsInt();
        List<UserOneTimePreKey> userOneTimePreKeys = new ArrayList<>();
        for (String opk : opkListDto.getOneTimePreKey()) {
            UserOneTimePreKey userOneTimePreKey = new UserOneTimePreKey();
            userOneTimePreKey.setUserId(userId);
            userOneTimePreKey.setPublicKey(opk);
            userOneTimePreKeys.add(userOneTimePreKey);
        }
        userOneTimePreKeyService.saveBatch(userOneTimePreKeys);
        return Result.success();
    }

    @Operation(summary = "获取指定账户的3个密钥")
    @GetMapping("/get_keys/{userId}")
    public Result getPublicKeys(@PathVariable Integer userId) {
        return Result.success(
                new PublicKeysVo(userIdentityKeyService.getIKByUserId(userId),
                        userSignedPreKeyService.getSPKByUserId(userId),
                        userOneTimePreKeyService.getOPKByUserId(userId)
                ));
    }
}
