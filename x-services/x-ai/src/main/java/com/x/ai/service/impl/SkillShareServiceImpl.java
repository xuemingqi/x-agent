package com.x.ai.service.impl;

import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.domain.SkillShareDo;
import com.x.ai.common.dto.SkillShareCreateDto;
import com.x.ai.common.model.SkillShareCreateParam;
import com.x.ai.service.SkillShareManagementService;
import com.x.ai.service.SkillShareService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillShareServiceImpl implements SkillShareService {

    private final SkillShareManagementService skillShareManagementService;


    @Override
    public SkillShareDo share(String skillName, SkillShareCreateDto request) {
        SkillShareCreateParam param = request == null ? null
                : new SkillShareCreateParam().setRecipientMobile(request.getRecipientMobile());
        return skillShareManagementService.share(UserThreadLocalUtil.getUserId(), skillName, param);
    }

    @Override
    public List<SkillShareDo> listPending() {
        return skillShareManagementService.listPending(UserThreadLocalUtil.getUserId());
    }

    @Override
    public boolean accept(Long shareId) {
        return skillShareManagementService.accept(UserThreadLocalUtil.getUserId(), shareId);
    }

    @Override
    public boolean reject(Long shareId) {
        return skillShareManagementService.reject(UserThreadLocalUtil.getUserId(), shareId);
    }
}
