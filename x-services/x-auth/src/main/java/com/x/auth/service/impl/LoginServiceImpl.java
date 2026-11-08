package com.x.auth.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import cn.hutool.core.lang.Snowflake;
import com.x.auth.common.jwt.JwtConfig;
import com.x.auth.common.jwt.JwtUtil;
import com.x.auth.common.property.LoginProperty;
import com.x.api.auth.domain.VerificationCodeDo;
import com.x.auth.service.LoginService;
import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.framework.common.utils.ServerUtil;
import com.x.framework.common.exception.XException;
import com.x.framework.redis.util.RedisUtil;
import com.x.api.contact.api.UserApi;
import com.x.api.contact.dto.UserAuthDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * @author : xuemingqi
 * @since : 2023/1/10 14:04
 */
@Slf4j
@Service
@RequiredArgsConstructor(onConstructor = @__({@Autowired}))
public class LoginServiceImpl implements LoginService {

    private final Snowflake snowflake;

    private final RedisUtil redisUtil;

    private final UserApi userApi;

    private final JwtUtil jwtUtil;

    private final LoginProperty loginProperty;

    @Override
    public BaseResult<String> login(String mobile, String password) {
        //用户密码是否正确
        BaseResult<Long> checkResult = userApi.userAuth(UserAuthDto.builder()
                .mobile(mobile)
                .password(password)
                .build());
        if (!ResponseCodeEnum.SUCCESS.getCode().equals(checkResult.getCode())) {
            return fail(mobile);
        }

        //生成token
        String token = jwtUtil.createToken(UserInfo.builder()
                .userId(checkResult.getData().toString())
                .mobile(mobile)
                .build());

        String redisKey = CommonConstant.TOKEN_KEY + token;
        //redis记录
        redisUtil.set(redisKey, mobile, JwtConfig.expiresTime, TimeUnit.MINUTES);
        //清除之前错误记录
        redisUtil.del(CommonConstant.LOGIN_FAIL + mobile);
        return ResultUtil.buildResultSuccess(token);
    }

    @Override
    public BaseResult<VerificationCodeDo> getVerificationCode() {
        //画验证码
        LineCaptcha lineCaptcha = CaptchaUtil.createLineCaptcha(160, 60, 4, 80);
        //验证码id
        Long id = snowflake.nextId();
        //验证码code
        String code = lineCaptcha.getCode();
        //redis记录
        redisUtil.set(CommonConstant.VERIFICATION_CODE_ID + id, code, 1, TimeUnit.MINUTES);
        return ResultUtil.buildResultSuccess(VerificationCodeDo.builder()
                .id(id)
                .imgBase64(lineCaptcha.getImageBase64Data())
                .build());
    }

    @Override
    public void checkVerificationCode(Long id, String code) {
        String key = CommonConstant.VERIFICATION_CODE_ID + id;
        String redisCode = redisUtil.get(key);
        if (!code.equalsIgnoreCase(redisCode)) {
            throw new XException(ResponseCodeEnum.VERIFICATION_ERROR);
        }
        redisUtil.del(key);
    }

    @Override
    public void checkLoginFailTimes(String mobile) {
        boolean check = redisUtil.get(CommonConstant.LOCK_MOBILE + mobile) == null;
        if (!check) {
            throw new XException(ResponseCodeEnum.MOBILE_LOCK_ERROR);
        }
    }

    @Override
    public BaseResult<Void> logout() {
        String token = ServerUtil.getTokenByHeaderStr();
        if (token != null) {
            redisUtil.del(CommonConstant.TOKEN_KEY + token);
        }
        return ResultUtil.buildResultSuccess();
    }

    @Override
    public BaseResult<UserInfo> auth() {
        String token = ServerUtil.getTokenByHeaderStr();
        if (null == token) {
            throw new XException(ResponseCodeEnum.PERMISSION_ERROR);
        }

        //token redis key value
        String redisKey = CommonConstant.TOKEN_KEY + token;
        String mobile = redisUtil.get(redisKey);

        // token失效
        if (mobile == null) {
            throw new XException(ResponseCodeEnum.PERMISSION_ERROR);
        }

        // 获取token中的userId
        UserInfo user = jwtUtil.getUser(token);
        if (user == null) {
            throw new XException(ResponseCodeEnum.PERMISSION_ERROR);
        }

        //续期
        redisUtil.set(redisKey, mobile, JwtConfig.expiresTime, TimeUnit.MINUTES);

        return ResultUtil.buildResultSuccess(user);
    }

    private Integer recordFail(String mobile) {
        //记录失败次数
        Integer fail = redisUtil.get(CommonConstant.LOGIN_FAIL + mobile);
        if (fail == null) {
            redisUtil.set(CommonConstant.LOGIN_FAIL + mobile, 1, loginProperty.getCycle(), TimeUnit.MINUTES);
        } else {
            redisUtil.incr(CommonConstant.LOGIN_FAIL + mobile, 1);
        }

        int failNum = (fail == null ? 1 : fail + 1);
        //超过五次锁定30分钟
        if (failNum == Integer.parseInt(loginProperty.getFailTimes())) {
            redisUtil.set(CommonConstant.LOCK_MOBILE + mobile, mobile, loginProperty.getLockTime(), TimeUnit.MINUTES);
        }
        return failNum;
    }

    private BaseResult<String> fail(String mobile) {
        int fail = recordFail(mobile);
        if (fail == Integer.parseInt(loginProperty.getFailTimes())) {
            throw new XException(ResponseCodeEnum.MOBILE_LOCK_ERROR);
        }
        return ResultUtil.buildParseResult(ResponseCodeEnum.CONTACT_TOKEN_ERROR,
                Integer.toString(Integer.parseInt(loginProperty.getFailTimes()) - fail));
    }
}
