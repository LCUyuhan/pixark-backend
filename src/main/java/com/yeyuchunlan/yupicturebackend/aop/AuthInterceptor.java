package com.yeyuchunlan.yupicturebackend.aop;

import com.yeyuchunlan.yupicturebackend.annotation.AuthCheck;
import com.yeyuchunlan.yupicturebackend.exception.BusinessException;
import com.yeyuchunlan.yupicturebackend.exception.ErrorCode;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.enums.UserRoleEnum;
import com.yeyuchunlan.yupicturebackend.service.UserService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
/**
 * 权限校验 AOP
 */
@Aspect// 表示这是一个切面
@Component// 表示这是一个组件
public class AuthInterceptor {

    @Resource
    private UserService userService;

    /**
     * 执行拦截
     *
     * @param joinPoint 切入点
     * @param authCheck 权限校验注解
     */
    @Around("@annotation(authCheck)")// 表示拦截带有该注解的方法
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        // 第 1 步：取出注解要求的角色（如 "admin"），没写就是 ""
        String mustRole = authCheck.mustRole();

        // 第 2 步：把角色字符串转成枚举，"admin"→ADMIN，"user"→USER，""→null
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);

        // 第 3 步：如果注解没要求任何角色 → 谁都能访问，直接放行
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }

        // 第 4 步：走到这说明必须要有指定角色 → 才去拿当前请求和登录用户
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        User loginUser = userService.getLoginUser(request);//如果未登录，getLoginUser方法会抛出异常

        // 第 5 步：用户角色转成枚举；查不到有效角色 → 拒绝
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(loginUser.getUserRole());
        if (userRoleEnum == null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 第 6 步：用户角色 != 要求的角色 → 拒绝
        // 要求必须有管理员权限，但用户没有管理员权限，拒绝
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }

        // 第 7 步：通过校验，放行执行真正的方法
        return joinPoint.proceed();
    }

}

