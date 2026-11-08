package com.x.ai.service;

import com.x.ai.common.domain.SkillShareDo;
import com.x.ai.common.dto.SkillShareCreateDto;

import java.util.List;

public interface SkillShareService {

    /**
     * 将当前用户的个人 Skill 作为不可变快照分享给指定手机号对应的平台用户。
     *
     * @param skillName 个人 Skill 名称
     * @param request 分享接收人
     * @return 待处理的分享邀请
     */
    SkillShareDo share(String skillName, SkillShareCreateDto request);

    /**
     * 查询当前用户尚未处理的 Skill 分享邀请。
     *
     * @return 待处理邀请列表
     */
    List<SkillShareDo> listPending();

    /**
     * 接受指定 Skill 分享邀请，接受后该 Skill 才进入运行时仓库。
     *
     * @param shareId 分享邀请 ID
     * @return 是否接受成功
     */
    boolean accept(Long shareId);

    /**
     * 拒绝指定 Skill 分享邀请。
     *
     * @param shareId 分享邀请 ID
     * @return 是否拒绝成功
     */
    boolean reject(Long shareId);

}
