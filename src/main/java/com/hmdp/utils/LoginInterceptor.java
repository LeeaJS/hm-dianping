package com.hmdp.utils;


import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author ljs
 * @version 1.0
 */

public class LoginInterceptor implements HandlerInterceptor {


    /**
     *
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     * 这里只是判断有没有用户
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 判断是否需要拦截（threadLocal中是否有当前用户）
        if(UserHolder.getUser() == null){
            // 没有，则需要拦截，设置状态码
            response.setStatus(401);
            return false;
        }

        return true;
    }


}
