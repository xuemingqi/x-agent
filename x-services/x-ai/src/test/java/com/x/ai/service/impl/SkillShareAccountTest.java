package com.x.ai.service.impl;

import cn.hutool.core.lang.Snowflake;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.x.ai.common.model.SkillShareCreateParam;
import com.x.ai.db.entity.AgentSharedSkill;
import com.x.ai.db.entity.AgentSkillShare;
import com.x.ai.db.enums.AgentSkillShareStatus;
import com.x.ai.db.service.AgentSharedSkillIService;
import com.x.ai.db.service.AgentSharedSkillResourceIService;
import com.x.ai.db.service.AgentSkillShareIService;
import com.x.ai.service.UserSkillService;
import com.x.api.contact.api.UserApi;
import com.x.api.contact.domain.UserDo;
import com.x.framework.common.exception.XException;
import com.x.framework.common.response.ResultUtil;
import io.agentscope.core.skill.AgentSkill;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SkillShareAccountTest {

    private final UserApi userApi = mock(UserApi.class);
    private final UserSkillService repositories = mock(UserSkillService.class);
    private final AgentSkillShareIService shares = mock(AgentSkillShareIService.class);
    private final AgentSharedSkillIService snapshots = mock(AgentSharedSkillIService.class);
    private final AgentSharedSkillResourceIService resources = mock(AgentSharedSkillResourceIService.class);
    private final ValidatorFactory validation = Validation.buildDefaultValidatorFactory();
    private final SkillShareManagementServiceImpl service = new SkillShareManagementServiceImpl(userApi,
            repositories, shares, snapshots, resources, new Snowflake(1, 1), validation.getValidator());

    @AfterEach
    void closeValidation() {
        validation.close();
    }

    @Test
    void onlyMobileAccountIsAcceptedAsRecipient() {
        assertThrows(XException.class, () -> service.share("42", "example-skill",
                new SkillShareCreateParam().setRecipientMobile("someone@example.com")));
        verifyNoInteractions(userApi, repositories, shares);
    }

    @Test
    void preventsSharingToOwnAccount() {
        UserDo sender = UserDo.builder().id(42L).mobile("13800000001").name("发送人").build();
        when(userApi.getUserById(42L)).thenReturn(ResultUtil.buildResultSuccess(sender));
        when(userApi.getUserInfo("13800000001")).thenReturn(ResultUtil.buildResultSuccess(sender));

        assertThrows(XException.class, () -> service.share("42", "example-skill",
                new SkillShareCreateParam().setRecipientMobile("13800000001")));
        verifyNoInteractions(repositories, shares);
    }

    @Test
    @SuppressWarnings("unchecked")
    void snapshotsPersonalSkillAndUsesResolvedRecipientIdentity() {
        UserDo sender = UserDo.builder().id(42L).mobile("13800000001").name("发送人").build();
        UserDo recipient = UserDo.builder().id(43L).mobile("13800000002").name("接收人").build();
        when(userApi.getUserById(42L)).thenReturn(ResultUtil.buildResultSuccess(sender));
        when(userApi.getUserInfo("13800000002")).thenReturn(ResultUtil.buildResultSuccess(recipient));
        AgentSkill original = AgentSkill.builder().name("example-skill").description("使用说明")
                .skillContent("请执行通用任务").resources(Map.of()).source("user-workspace").build();
        when(repositories.get("42", "example-skill")).thenReturn(original);
        LambdaQueryChainWrapper<AgentSkillShare> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(shares.lambdaQuery()).thenReturn(query);

        var result = service.share("42", "example-skill",
                new SkillShareCreateParam().setRecipientMobile("13800000002"));

        ArgumentCaptor<AgentSkillShare> invitation = ArgumentCaptor.forClass(AgentSkillShare.class);
        verify(shares).save(invitation.capture());
        assertEquals("42", invitation.getValue().getSenderUserId());
        assertEquals("43", invitation.getValue().getRecipientUserId());
        assertEquals("13800000001", invitation.getValue().getSenderMobile());
        assertEquals("发送人", result.getSenderDisplayName());
        assertEquals(AgentSkillShareStatus.PENDING, result.getStatus());
        ArgumentCaptor<AgentSharedSkill> snapshot = ArgumentCaptor.forClass(AgentSharedSkill.class);
        verify(snapshots).save(snapshot.capture());
        assertEquals("42", snapshot.getValue().getOwnerUserId());
        assertEquals("请执行通用任务", snapshot.getValue().getSkillContent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void invitationResponseRequiresCurrentRecipient() {
        LambdaQueryChainWrapper<AgentSkillShare> query = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        when(query.eq(any(), any())).thenReturn(query);
        when(shares.lambdaQuery()).thenReturn(query);

        assertThrows(XException.class, () -> service.accept("43", 7L));

        verify(query).eq(any(), eq("43"));
        verifyNoInteractions(snapshots, repositories, userApi);
    }
}
