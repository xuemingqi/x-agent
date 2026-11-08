package com.x.ai.properties;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.nio.file.Path;
import java.time.Duration;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    /**
     * Agent 名称。
     */
    @NotBlank
    private String name = "agent";

    /**
     * Agent 本地工作目录。
     */
    @NotNull
    private Path workspace = Path.of(".agentscope/workspace");

    /**
     * 沙箱配置。
     */
    @Valid
    private Sandbox sandbox = new Sandbox();

    /**
     * 上下文压缩配置。
     */
    @Valid
    private Compaction compaction = new Compaction();

    /**
     * 长期记忆配置。
     */
    @Valid
    private Memory memory = new Memory();

    /**
     * 工具审批配置。
     */
    @Valid
    private Approval approval = new Approval();

    /**
     * 子 Agent 执行配置。
     */
    @Valid
    private Subagent subagent = new Subagent();

    /**
     * 流式响应配置。
     */
    @Valid
    private Streaming streaming = new Streaming();

    /**
     * 技能加载配置。
     */
    @Valid
    private Skill skill = new Skill();

    /**
     * 公共知识库配置。
     */
    @Valid
    private PublicKnowledge publicKnowledge = new PublicKnowledge();

    /**
     * 用户工作区共享目录配置。
     */
    @Valid
    private UserWorkspace userWorkspace = new UserWorkspace();

    /**
     * 会话文件配置。
     */
    @Valid
    private ConversationFile conversationFile = new ConversationFile();

    /**
     * 定时任务配置。
     */
    @Valid
    private Schedule schedule = new Schedule();

    /**
     * 任务中断配置。
     */
    @Valid
    private Interrupt interrupt = new Interrupt();

    /**
     * Agent 对外接入配置，由 Nacos 的 x-ai.yaml 提供环境值。
     */
    @Valid
    private ExternalAccess externalAccess = new ExternalAccess();

    @Data
    public static class Sandbox {
        /**
         * 沙箱容器镜像。
         */
        @NotBlank
        private String image = "ubuntu:24.04";

        /**
         * 沙箱容器网络模式。
         */
        @NotBlank
        private String network = "bridge";

        /**
         * 沙箱内工作目录。
         */
        @NotBlank
        private String workspaceRoot = "/workspace";

        /**
         * 沙箱 CPU 核数。
         */
        @Min(1)
        private long cpuCount = 2;

        /**
         * 沙箱内存限制字节数。
         */
        @Min(134_217_728)
        private long memorySizeBytes = 2_147_483_648L;

        /**
         * Docker 原生文件传输超时时间。
         */
        @NotNull
        private Duration fileTransferTimeout = Duration.ofMinutes(2);

        /**
         * 单次 Docker 原生文件传输允许的最大字节数。
         */
        @Min(1)
        private long maxFileTransferSizeBytes = 104_857_600L;
    }

    @Data
    public static class Compaction {
        /**
         * 触发上下文压缩的窗口占比。
         */
        private double triggerRatio = 0.90D;

        /**
         * 压缩后保留内容的窗口占比。
         */
        private double keepRatio = 0.20D;
    }

    @Data
    public static class Memory {
        /**
         * 是否启用 AgentScope 自动记忆提取和长期记忆整理。
         */
        private boolean enabled;

        /**
         * 同一用户两次对话记忆提取的最小间隔。
         */
        @NotNull
        private Duration flushMinGap = Duration.ofMinutes(10);

        /**
         * 同一用户两次长期记忆整理的最小间隔。
         */
        @NotNull
        private Duration consolidationMinGap = Duration.ofMinutes(30);
    }

    @Data
    public static class Approval {
        /**
         * 单次任务允许连续自动审批的最大次数。
         */
        @Min(1)
        private int maxConsecutiveApprovals = 10;
    }

    @Data
    public static class Subagent {

        /**
         * 是否等待子 Agent 完成后再继续主 Agent 推理。
         */
        private boolean forceSync = true;

        /**
         * 强制同步等待子 Agent 的超时时间。
         */
        @Min(1)
        @Max(600)
        private int syncTimeoutSeconds = 600;
    }

    @Data
    public static class Streaming {
        /**
         * 流式响应阻塞超时时间。
         */
        @NotNull
        private Duration blockingTimeout = Duration.ofMinutes(10);
    }

    @Data
    public static class Skill {
        /**
         * 公共 Skill 所在数据库。
         */
        @NotBlank
        private String databaseName = "x_agent";

        /**
         * 公共 Skill 主表。
         */
        @NotBlank
        private String tableName = "agent_public_skill";

        /**
         * 公共 Skill 资源表。
         */
        @NotBlank
        private String resourceTableName = "agent_public_skill_resource";
    }

    @Data
    public static class PublicKnowledge {
        /**
         * Docker 宿主机上的公共知识库目录。
         */
        @NotNull
        private Path directory = Path.of(".agentscope/public-knowledge");
    }

    @Data
    public static class UserWorkspace {
        /**
         * Docker 宿主机上的用户共享目录，生产环境应指向 NFS 等共享文件系统挂载点。
         */
        @NotNull
        private Path directory = Path.of(".agentscope/user-workspaces");

        /**
         * 单个知识库文件允许上传的最大字节数。
         */
        @Min(1)
        private long maxUploadSizeBytes = 104_857_600L;
    }

    @Data
    public static class ConversationFile {
        /**
         * 单个会话文件允许的最大字节数。
         */
        @Min(1)
        private long maxFileSizeBytes = 20_971_520L;

        /**
         * 单条消息允许携带的最大文件数。
         */
        @Min(1)
        private int maxFilesPerMessage = 5;

        /**
         * 单条消息全部文件允许的最大字节数。
         */
        @Min(1)
        private long maxTotalSizeBytes = 52_428_800L;
    }

    @Data
    public static class Schedule {
        /**
         * 定时任务默认时区。
         */
        @NotBlank
        private String defaultZoneId = "Asia/Shanghai";

        /**
         * 定时任务执行语言区域。
         */
        @NotBlank
        private String executionLocale = "zh-CN";

        /**
         * Quartz 调度配置。
         */
        @Valid
        private Quartz quartz = new Quartz();

        @Data
        public static class Quartz {
            /**
             * AgentScope 调度器类型标识。
             */
            @NotBlank
            private String schedulerType = "quartz";
        }
    }

    @Data
    public static class Interrupt {
        /**
         * 跨实例任务中断消息主题。
         */
        @NotBlank
        private String topic = "x-agent:interrupt";
    }

    @Data
    public static class ExternalAccess {

        /**
         * 对外 HTTP Agent 接口地址。
         */
        @NotBlank
        private String httpApiUrl = "http://127.0.0.1:18081/ai/open/chat/run";

        /**
         * 对外 MCP Gateway 地址。
         */
        @NotBlank
        private String mcpServerUrl = "http://127.0.0.1:18081/mcp";
    }
}
