package com.x.ai.service.impl;

import com.x.ai.properties.AgentProperties;
import com.x.ai.service.HarnessAgentService;
import com.x.ai.service.UserWorkspaceService;
import com.x.framework.common.dto.UserInfo;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.UserThreadLocalUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KnowledgeFileIsolationTest {

    @AfterEach
    void clearUser() {
        UserThreadLocalUtil.clear();
    }

    @Test
    void parentSymlinkCannotExposeOrDeleteAnotherUsersDocument(@TempDir Path directory) throws IOException {
        Path currentKnowledge = Files.createDirectory(directory.resolve("current-user"));
        Path otherKnowledge = Files.createDirectory(directory.resolve("other-user"));
        Path privateDocument = Files.writeString(otherKnowledge.resolve("private.txt"), "其他用户的内容");
        Files.createSymbolicLink(currentKnowledge.resolve("linked"), otherKnowledge);
        UserWorkspaceService workspaces = mock(UserWorkspaceService.class);
        when(workspaces.knowledgeDirectory("42")).thenReturn(currentKnowledge);
        UserThreadLocalUtil.set(UserInfo.builder().userId("42").build());
        KnowledgeServiceImpl service = new KnowledgeServiceImpl(workspaces, new AgentProperties(),
                mock(HarnessAgentService.class));

        assertThrows(XException.class, () -> service.download("linked/private.txt"));
        assertThrows(XException.class, () -> service.delete("linked/private.txt"));
        assertEquals("其他用户的内容", Files.readString(privateDocument));
    }
}
