package com.x.ai.service.impl;

import com.x.ai.properties.AgentProperties;
import com.x.framework.common.exception.XException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserWorkspaceIsolationTest {

    @Test
    void knowledgeRootSymlinkCannotRedirectFileAccess(@TempDir Path temporary) throws IOException {
        Path root = Files.createDirectory(temporary.resolve("users"));
        Path user = Files.createDirectory(root.resolve("42"));
        Path outside = Files.createDirectory(temporary.resolve("private"));
        Path document = Files.writeString(outside.resolve("document.txt"), "private");
        Files.createSymbolicLink(user.resolve("knowledge"), outside);

        assertThrows(XException.class, () -> workspaces(root).knowledgeDirectory("42"));
        assertEquals("private", Files.readString(document));
    }

    @Test
    void userRootSymlinkCannotCreateKnowledgeInAnotherDirectory(@TempDir Path temporary) throws IOException {
        Path root = Files.createDirectory(temporary.resolve("users"));
        Path outside = Files.createDirectory(temporary.resolve("private"));
        Files.createSymbolicLink(root.resolve("42"), outside);

        assertThrows(XException.class, () -> workspaces(root).knowledgeDirectory("42"));
        assertFalse(Files.exists(outside.resolve("knowledge")));
    }

    private UserWorkspaceServiceImpl workspaces(Path root) {
        AgentProperties properties = new AgentProperties();
        properties.getUserWorkspace().setDirectory(root);
        return new UserWorkspaceServiceImpl(properties);
    }
}
