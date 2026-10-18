package com.yeyuchunlan.pixarkbackend.manager;

import cn.hutool.core.util.StrUtil;
import com.yeyuchunlan.pixarkbackend.config.CosClientConfig;
import com.yeyuchunlan.pixarkbackend.exception.ErrorCode;
import com.yeyuchunlan.pixarkbackend.exception.ThrowUtils;
import com.yeyuchunlan.pixarkbackend.model.entity.Picture;
import com.yeyuchunlan.pixarkbackend.model.entity.Space;
import com.yeyuchunlan.pixarkbackend.model.entity.User;
import com.yeyuchunlan.pixarkbackend.service.PictureService;
import com.yeyuchunlan.pixarkbackend.service.SpaceService;
import com.yeyuchunlan.pixarkbackend.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * 管理员删除用户时的级联编排。
 * 空间和图片各自带事务、并在事务提交后异步清 COS 文件，所以这里只做循环调用，
 * 不再包一层大事务（否则会出现"异步清理已发出、外层事务却回滚"的错位）。
 * 每一步都只查 isDelete=0 的数据，中途失败后重新执行会跳过已删部分，天然幂等。
 */
@Slf4j
@Service
public class UserDeletionManager {

    @Resource
    private UserService userService;

    @Resource
    private SpaceService spaceService;

    @Resource
    private PictureService pictureService;

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    /**
     * 删除用户及其全部内容：空间（含空间内图片）→ 公共图库图片 → 头像 → 账号行
     *
     * @param userId   被删用户 id
     * @param operator 执行删除的管理员
     */
    public void deleteUser(long userId, User operator) {
        ThrowUtils.throwIf(userId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(operator == null, ErrorCode.NO_AUTH_ERROR);
        ThrowUtils.throwIf(operator.getId().equals(userId),
                ErrorCode.OPERATION_ERROR, "不能删除自己的账号");
        User oldUser = userService.getById(userId);
        ThrowUtils.throwIf(oldUser == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 1. 他的空间：deleteSpace 内部级联删空间内图片并清理 COS
        List<Space> spaces = spaceService.lambdaQuery()
                .eq(Space::getUserId, userId)
                .list();
        for (Space space : spaces) {
            spaceService.deleteSpace(space.getId(), operator);
        }

        // 2. 他在公共图库的图片（spaceId 为空）
        List<Picture> publicPictures = pictureService.lambdaQuery()
                .eq(Picture::getUserId, userId)
                .isNull(Picture::getSpaceId)
                .list();
        for (Picture picture : publicPictures) {
            pictureService.deletePicture(picture.getId(), operator);
        }

        // 3. 头像对象（只清本桶内的）
        clearAvatar(oldUser.getUserAvatar());

        // 4. 最后删账号行，前面任何一步失败时账号还在，管理员可以重试
        userService.deleteUserAccount(userId);
        log.info("级联删除用户完成, userId = {}, spaces = {}, publicPictures = {}",
                userId, spaces.size(), publicPictures.size());
    }

    private void clearAvatar(String avatarUrl) {
        if (StrUtil.isBlank(avatarUrl)) {
            return;
        }
        String host = cosClientConfig.getHost();
        if (!avatarUrl.startsWith(host + "/")) {
            log.warn("头像不在当前存储桶内，跳过清理, avatarUrl = {}", avatarUrl);
            return;
        }
        try {
            cosManager.deleteObject(avatarUrl.substring(host.length() + 1));
        } catch (Exception e) {
            log.error("清理头像文件失败, avatarUrl = {}", avatarUrl, e);
        }
    }
}
