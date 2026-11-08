package com.x.ai.service;

import com.x.ai.common.domain.SkillShareDo;
import com.x.ai.common.model.SkillShareCreateParam;

import java.util.List;

public interface SkillShareManagementService {

    /**
     * 分享指定用户本人的个人 Skill 快照，接收人须为已注册平台用户。
     *
     * @param userId 入口认证取得的操作人身份
     * @param skillName 个人 Skill 名称
     * @param request 分享接收人业务参数
     * @return 待处理分享邀请
     */
    SkillShareDo share(String userId, String skillName, SkillShareCreateParam request);

    /**
     * 查询指定用户作为接收人尚未处理的邀请。
     *
     * @param userId 入口认证取得的接收人身份
     * @return 待处理邀请列表
     */
    List<SkillShareDo> listPending(String userId);

    /**
     * 接受仍待处理且属于指定接收人的邀请，并阻止同名共享 Skill 冲突。
     *
     * @param userId 入口认证取得的接收人身份
     * @param shareId 分享邀请 ID
     * @return 是否接受成功
     */
    boolean accept(String userId, Long shareId);

    /**
     * 拒绝仍待处理且属于指定接收人的邀请。
     *
     * @param userId 入口认证取得的接收人身份
     * @param shareId 分享邀请 ID
     * @return 是否拒绝成功
     */
    boolean reject(String userId, Long shareId);
}
