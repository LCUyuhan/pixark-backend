package com.yeyuchunlan.pixarkbackend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yeyuchunlan.pixarkbackend.config.LoginSecurityProperties;
import com.yeyuchunlan.pixarkbackend.exception.BusinessException;
import com.yeyuchunlan.pixarkbackend.exception.ErrorCode;
import com.yeyuchunlan.pixarkbackend.exception.ThrowUtils;
import com.yeyuchunlan.pixarkbackend.model.dto.user.UserQueryRequest;
import com.yeyuchunlan.pixarkbackend.model.entity.User;
import com.yeyuchunlan.pixarkbackend.model.enums.UserRoleEnum;
import com.yeyuchunlan.pixarkbackend.model.vo.user.LoginUserVO;
import com.yeyuchunlan.pixarkbackend.model.vo.user.UserVO;
import com.yeyuchunlan.pixarkbackend.service.UserService;
import com.yeyuchunlan.pixarkbackend.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.DigestUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.yeyuchunlan.pixarkbackend.constant.UserConstant.USER_LOGIN_STATE;

/**
 * @author him
 * @description 针对表【user(用户)】的数据库操作Service实现
 * @createDate 2026-09-01 19:30:27
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    /**
     * 允许排序的字段白名单（请求参数值 -> 数据库列名），防止 sortField 直接拼入 SQL
     */
    private static final Map<String, String> ALLOW_SORT_FIELD_MAP = Map.of(
            "createTime", "createTime",
            "editTime", "editTime",
            "updateTime", "updateTime",
            "id", "id",
            "userAccount", "userAccount",
            "userName", "userName"
    );

    /**
     * 登录失败计数 key 前缀
     */
    private static final String LOGIN_FAIL_ACCOUNT_PREFIX = "pixark:loginFail:account:";

    private static final String LOGIN_FAIL_IP_PREFIX = "pixark:loginFail:ip:";

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private LoginSecurityProperties loginSecurityProperties;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 用户注册
     *
     * @param userAccount   用户账户
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @return 新用户 id
     */
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        //1.校验
        ThrowUtils.throwIf(ObjUtil.hasNull(userAccount, userPassword, checkPassword), ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "用户账号过短");
        ThrowUtils.throwIf(userPassword.length() < 8 || checkPassword.length() < 8, ErrorCode.PARAMS_ERROR, "用户密码过短");
        ThrowUtils.throwIf(!userPassword.equals(checkPassword), ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        //2.检查是否重复
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        long count = this.baseMapper.selectCount(queryWrapper);
        ThrowUtils.throwIf(count > 0, ErrorCode.OPERATION_ERROR, "账号重复");
        //3.加密
        String encryptPassword = getEncryptPassword(userPassword);
        //4.插入数据库
        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(encryptPassword);
        user.setUserRole(UserRoleEnum.USER.getValue());
        user.setUserName("无名");
        boolean saveResult;
        try {
            saveResult = this.save(user);
        } catch (DuplicateKeyException e) {
            // 上面按 isDelete=0 预检查，查不到历史上已逻辑删除的同名账号；
            // 唯一索引是跨删除生效的，这里兜住这种冲突
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "该账号已被注册");
        }
        ThrowUtils.throwIf(!saveResult, ErrorCode.SYSTEM_ERROR, "注册失败,数据库错误");
        return user.getId();
    }

    /**
     * 删除账号行：账号墓碑化 + 逻辑删除。
     * 空间/图片的级联清理由 UserDeletionManager 编排，这里只保证账号本身能删干净
     *
     * @param userId 用户 id
     */
    @Override
    public void deleteUserAccount(long userId) {
        User oldUser = this.getById(userId);
        ThrowUtils.throwIf(oldUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        transactionTemplate.execute(status -> {
            boolean accountReleased = this.lambdaUpdate()
                    .eq(User::getId, userId)
                    .set(User::getUserAccount, buildDeletedAccount(oldUser))
                    .update();
            ThrowUtils.throwIf(!accountReleased, ErrorCode.OPERATION_ERROR, "账号释放失败");
            boolean removed = this.removeById(userId);
            ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "用户删除失败");
            return true;
        });
    }

    /**
     * 生成墓碑账号：原账号 + #del# + id，原账号超长时截断（userAccount 列宽 256）
     */
    private String buildDeletedAccount(User user) {
        String suffix = "#del#" + user.getId();
        String account = StrUtil.emptyToDefault(user.getUserAccount(), "");
        int maxKeep = 256 - suffix.length();
        if (account.length() > maxKeep) {
            account = account.substring(0, maxKeep);
        }
        return account + suffix;
    }

    /**
     * 获取加密密码
     *
     * @param userPassword 密码
     * @return 加密密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 盐值，混淆密码
        final String SALT = "yeyuchunlan";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request      请求
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        //1.校验
        ThrowUtils.throwIf(ObjUtil.hasNull(userAccount, userPassword), ErrorCode.PARAMS_ERROR, "参数为空");
        ThrowUtils.throwIf(userAccount.length() < 4, ErrorCode.PARAMS_ERROR, "用户账号错误");
        ThrowUtils.throwIf(userPassword.length() < 8, ErrorCode.PARAMS_ERROR, "用户密码错误");
        //2.限流：先挡住已经连续失败的来源，避免继续打库
        String clientIp = resolveClientIp(request);
        checkLoginFailLimit(userAccount, clientIp);
        //3.加密
        String encryptPassword = getEncryptPassword(userPassword);
        //4.查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        //如果用户不存在
        if(user == null){
            recordLoginFailure(userAccount, clientIp);
            log.info("user login failed,userAccount can not match user Password");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        //5.登录成功，清掉该账号的失败计数
        clearLoginFailures(userAccount);
        //6.记录登录态
        request.getSession().setAttribute(USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    private void checkLoginFailLimit(String userAccount, String clientIp) {
        int accountLimit = loginSecurityProperties.getAccountFailLimit();
        if (accountLimit > 0 && getFailCount(LOGIN_FAIL_ACCOUNT_PREFIX + userAccount) >= accountLimit) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR,
                    "该账号登录失败次数过多，请 " + loginSecurityProperties.getFailWindowMinutes() + " 分钟后再试");
        }
        int ipLimit = loginSecurityProperties.getIpFailLimit();
        if (ipLimit > 0 && getFailCount(LOGIN_FAIL_IP_PREFIX + clientIp) >= ipLimit) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "登录尝试过于频繁，请稍后再试");
        }
    }

    private long getFailCount(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(value)) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void recordLoginFailure(String userAccount, String clientIp) {
        if (loginSecurityProperties.getAccountFailLimit() > 0) {
            increaseFailCount(LOGIN_FAIL_ACCOUNT_PREFIX + userAccount);
        }
        if (loginSecurityProperties.getIpFailLimit() > 0) {
            increaseFailCount(LOGIN_FAIL_IP_PREFIX + clientIp);
        }
    }

    private void increaseFailCount(String key) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            stringRedisTemplate.expire(key,
                    Duration.ofMinutes(Math.max(1, loginSecurityProperties.getFailWindowMinutes())));
        }
    }

    private void clearLoginFailures(String userAccount) {
        stringRedisTemplate.delete(LOGIN_FAIL_ACCOUNT_PREFIX + userAccount);
    }

    private String resolveClientIp(HttpServletRequest request) {
        // 生产环境由 Nginx 覆盖写入真实客户端地址；本机直连时没有该请求头，退回 remoteAddr
        String realIp = StrUtil.trim(request.getHeader("X-Real-IP"));
        return StrUtil.isBlank(realIp) ? request.getRemoteAddr() : realIp;
    }

    /**
     * 获取脱敏后的登录用户信息
     *
     * @param user 用户信息
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }


    /**
     * 获取当前登录用户
     *
     * @param request 请求
     * @return 当前登录用户
     */
    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库查询（追求性能的话可以注释，直接返回上述结果）
        long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 用户注销
     *
     * @param request 请求
     * @return 是否注销成功
     */
    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 先判断是否已登录
        Object userObj = request.getSession().getAttribute(USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(USER_LOGIN_STATE);
        return true;
    }


    /**
     * 获取脱敏的用户信息
     *
     * @param user 用户信息
     * @return 脱敏后的用户信息
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏的用户列表
     * @param  userList 用户列表
     * @return 脱敏后的用户列表
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }


    /**
     * 获取queryWrapper形式的查询条件
     * @param  userQueryRequest 用户查询条件
     * @return queryWrapper形式的查询条件
     */
    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        // 排序：字段经白名单校验，非法字段忽略；Map.of 不允许 null 键查询，须先判空再查白名单；常量在前调用 equals 避免 sortOrder 为 null 时 NPE
        String sortColumn = StrUtil.isNotBlank(sortField) ? ALLOW_SORT_FIELD_MAP.get(sortField) : null;
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortColumn), "ascend".equals(sortOrder), sortColumn);
        return queryWrapper;
    }


    /**
     * 是否为管理员
     *
     * @param user  用户
     * @return 是否为管理员
     */
    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }






}




