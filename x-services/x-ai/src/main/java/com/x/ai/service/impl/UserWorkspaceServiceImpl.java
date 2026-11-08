package com.x.ai.service.impl;

import cn.hutool.core.text.StrPool;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.StringUtil;
import com.x.ai.properties.AgentProperties;
import com.x.ai.service.UserWorkspaceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserWorkspaceServiceImpl implements UserWorkspaceService {

    private final AgentProperties agentProperties;

    @Override
    public Path knowledgeDirectory(String userId) {
        return ensureDirectory(userDirectory(userId).resolve("knowledge"));
    }

    /**
     * 使用可信用户 ID 定位共享目录，并阻止路径分隔符越过用户根目录。
     */
    private Path userDirectory(String userId) {
        if (StringUtil.isEmpty(userId) || StrPool.DOT.equals(userId) || StrPool.DOUBLE_DOT.equals(userId)
                || !userId.matches("[A-Za-z0-9._@-]{1,128}")) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        Path root = agentProperties.getUserWorkspace().getDirectory().toAbsolutePath().normalize();
        Path directory = root.resolve(userId).normalize();
        if (!directory.startsWith(root) || Files.isSymbolicLink(directory)) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        return directory;
    }

    /**
     * 延迟创建 NFS 子目录，使运行期间新增用户无需重启服务。
     */
    private Path ensureDirectory(Path directory) {
        if (Files.isSymbolicLink(directory)) {
            throw new XException(ResponseCodeEnum.AGENT_RUNTIME_INVALID);
        }
        try {
            return Files.createDirectories(directory).toAbsolutePath().normalize();
        } catch (IOException error) {
            log.error("Failed to create user workspace directory: {}", directory, error);
            throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
        }
    }
}
