package com.yeyuchunlan.yupicturebackend.service;

import com.yeyuchunlan.yupicturebackend.model.dto.aiimageedit.AiImageEditCreateRequest;
import com.yeyuchunlan.yupicturebackend.model.dto.aiimageedit.AiImageEditSaveRequest;
import com.yeyuchunlan.yupicturebackend.model.entity.User;
import com.yeyuchunlan.yupicturebackend.model.vo.AiImageEditBaseImageVO;
import com.yeyuchunlan.yupicturebackend.model.vo.AiImageEditQuotaVO;
import com.yeyuchunlan.yupicturebackend.model.vo.AiImageEditStyleVO;
import com.yeyuchunlan.yupicturebackend.model.vo.AiImageEditTaskVO;
import com.yeyuchunlan.yupicturebackend.model.vo.PictureVO;

import java.util.List;

/**
 * AI 图片编辑服务
 */
public interface AiImageEditService {

    /**
     * 创建 AI 图片编辑任务（异步下发，不等待出图）
     *
     * @param aiImageEditCreateRequest 编辑请求
     * @param loginUser                登录用户
     * @return 任务信息，前端据此轮询
     */
    AiImageEditTaskVO createTask(AiImageEditCreateRequest aiImageEditCreateRequest, User loginUser);

    /**
     * 查询 AI 图片编辑任务，仅任务提交人可查
     *
     * @param taskId    任务 id
     * @param loginUser 登录用户
     * @return 任务状态与结果预览地址
     */
    AiImageEditTaskVO getTask(String taskId, User loginUser);

    /**
     * 保存 AI 图片编辑结果到平台
     *
     * @param aiImageEditSaveRequest 保存请求
     * @param loginUser              登录用户
     * @return 保存后的图片信息
     */
    PictureVO saveTaskResult(AiImageEditSaveRequest aiImageEditSaveRequest, User loginUser);

    /**
     * 查询风格化功能的预设风格清单，仅返回已开放功能对应的风格
     *
     * @param function 功能，留空返回全部
     * @return 风格清单
     */
    List<AiImageEditStyleVO> listStyles(String function);

    /**
     * 查询当前用户的 AI 编辑额度余量，供前端在打开弹窗时展示
     *
     * @param loginUser 登录用户
     * @return 剩余额度（user.aiQuota，用完需管理员发放）
     */
    AiImageEditQuotaVO getQuota(User loginUser);

    /**
     * 取原图的 base64 底图，供前端本地画布绘制涂抹蒙版（避免跨域图片污染 canvas）。
     * 只读，不消耗额度。
     *
     * @param pictureId 图片 id
     * @param loginUser 登录用户
     * @return base64 数据与原图宽高
     */
    AiImageEditBaseImageVO getBaseImage(Long pictureId, User loginUser);
}
