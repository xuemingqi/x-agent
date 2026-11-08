package com.x.framework.common.utils;

import com.x.framework.common.constants.CommonConstant;
import com.x.framework.common.dto.UserInfo;
import org.springframework.util.ObjectUtils;


/**
 * @author : xuemingqi
 * @since : 2023/1/10 16:50
 */
public class ServerUtil {

    /**
     * 获取token
     */
    public static String getTokenByHeaderStr() {
        String jwtStr = ServletUtil.getHeader(CommonConstant.TOKEN);
        int jwtTokenPreLength = CommonConstant.TOKEN_PRE.length();
        if (ObjectUtils.isEmpty(jwtStr) || !jwtStr.startsWith(CommonConstant.TOKEN_PRE) || jwtStr.length() == jwtTokenPreLength) {
            return null;
        }
        return jwtStr.substring(jwtTokenPreLength);
    }

    /**
     * 设置用户上下文
     *
     * @param userInfo 用户上下文对象
     */
    public static void setAuthenticatedUser(UserInfo userInfo) {
        UserThreadLocalUtil.set(userInfo);
    }

    /**
     * 获取用户上下文
     *
     * @return 当前用户上下文，可能为null
     */
    public static UserInfo getAuthenticatedUser() {
        return UserThreadLocalUtil.get();
    }

    /**
     * 获取UserId
     */
    public static Long getAuthenticatedUserId() {
        return UserThreadLocalUtil.getOptional()
                .map(userInfo -> Long.valueOf(userInfo.getUserId()))
                .orElse(null);
    }

}
