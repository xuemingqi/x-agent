package com.x.ai.service;

import com.x.ai.common.enums.SkillArchiveFormat;
import io.agentscope.core.skill.AgentSkill;
import org.springframework.web.multipart.MultipartFile;

public interface SkillArchiveService {

    /**
     * 校验单个 Skill 压缩包，使用当前用户默认模型整理指令并保留附属资源，不写入仓库。
     */
    AgentSkill read(MultipartFile file);

    /**
     * 将已授权读取的 Skill 连同资源原样打包，不调用模型。
     */
    byte[] write(AgentSkill skill, SkillArchiveFormat format);
}
