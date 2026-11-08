package com.x.ai.db.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.x.ai.db.entity.AgentConversationFile;
import com.x.ai.db.enums.AgentConversationFileKind;
import com.x.ai.db.mapper.AgentConversationFileMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentConversationFileIServiceImplTest {

    @BeforeAll
    static void initializeEntityMappings() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "attachment-binding-test"),
                AgentConversationFile.class);
    }

    @Test
    void atomicallyBindsOnlyPendingInputsOwnedByTheUserAndSession() {
        AgentConversationFileMapper mapper = mock(AgentConversationFileMapper.class);
        AgentConversationFileIServiceImpl service = new AgentConversationFileIServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.update(isNull(), any())).thenReturn(2);

        int affected = service.bindPendingInputs("user-1", "session-1", List.of(11L, 12L), 101L);

        ArgumentCaptor<LambdaUpdateWrapper<AgentConversationFile>> update = ArgumentCaptor.captor();
        verify(mapper).update(isNull(), update.capture());
        LambdaUpdateWrapper<AgentConversationFile> condition = update.getValue();
        assertThat(condition.getSqlSegment()).contains("user_id =", "session_id =", "kind =", "id IN",
                "message_id IS NULL");
        assertThat(condition.getSqlSet()).contains("message_id=");
        assertThat(condition.getParamNameValuePairs().values())
                .contains("user-1", "session-1", AgentConversationFileKind.INPUT, 11L, 12L, 101L);
        assertThat(affected).isEqualTo(2);
    }

    @Test
    void returnsTheActualAffectedRowCountWhenAnAttachmentIsAlreadyClaimed() {
        AgentConversationFileMapper mapper = mock(AgentConversationFileMapper.class);
        AgentConversationFileIServiceImpl service = new AgentConversationFileIServiceImpl();
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.update(isNull(), any())).thenReturn(1);

        assertThat(service.bindPendingInputs("user-1", "session-1", List.of(11L, 12L), 101L)).isEqualTo(1);
    }
}
