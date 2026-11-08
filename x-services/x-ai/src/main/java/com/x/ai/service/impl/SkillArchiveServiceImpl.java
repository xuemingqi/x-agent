package com.x.ai.service.impl;

import cn.hutool.core.text.StrPool;
import com.x.framework.common.utils.UserThreadLocalUtil;
import com.x.ai.common.constants.AgentConstant;
import com.x.ai.common.constants.PromptConstant;
import com.x.ai.common.constants.SkillArchiveConstant;
import com.x.ai.common.dto.UserSkillCreateDto;
import com.x.ai.common.enums.SkillArchiveFormat;
import com.x.ai.common.model.AiModelRuntimeConfig;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import com.x.ai.service.SkillArchiveService;
import com.x.ai.service.AiModelRuntimeService;
import com.x.ai.skill.SkillArchiveCodec;
import com.x.ai.skill.SkillResourceCodec;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.skill.AgentSkill;
import io.agentscope.core.skill.util.MarkdownSkillParser;
import io.agentscope.extensions.model.openai.OpenAIChatModel;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class SkillArchiveServiceImpl implements SkillArchiveService {

    private final AiModelRuntimeService aiModelRuntimeService;

    private final Validator validator;


    @Override
    public AgentSkill read(MultipartFile file) {
        Map<String, byte[]> files = selectSkillFiles(readArchive(file));
        UserSkillCreateDto definition = normalize(buildImportMessages(files));
        return AgentSkill.builder()
                .name(definition.getName())
                .description(definition.getDescription())
                .skillContent(definition.getContent())
                .resources(encodeResources(files))
                .source(AgentConstant.USER_SKILL_SOURCE)
                .build();
    }

    @Override
    public byte[] write(AgentSkill skill, SkillArchiveFormat format) {
        try {
            Map<String, byte[]> files = readSkillFiles(skill).entrySet().stream()
                    .collect(Collectors.toMap(entry -> skill.getName() + StrPool.SLASH + entry.getKey(), Map.Entry::getValue));
            return SkillArchiveCodec.write(files, format);
        } catch (IOException | UncheckedIOException error) {
            log.warn("Failed to export Skill archive: {}", skill.getName(), error);
            throw new XException(ResponseCodeEnum.AGENT_WORKSPACE_STORAGE_ERROR);
        } catch (IllegalArgumentException error) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
    }

    /**
     * 在调用模型前完成上传大小和归档格式校验。
     */
    private Map<String, byte[]> readArchive(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > SkillArchiveConstant.MAX_UPLOAD_BYTES) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
        SkillArchiveFormat format = SkillArchiveFormat.fromFilename(file.getOriginalFilename());
        try (InputStream input = file.getInputStream()) {
            return SkillArchiveCodec.read(input.readNBytes(SkillArchiveConstant.MAX_UPLOAD_BYTES + 1), format);
        } catch (IOException | IllegalArgumentException error) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
    }

    /**
     * 移除系统打包元文件，保留单个 Skill 目录内的全部业务资源。
     */
    private Map<String, byte[]> selectSkillFiles(Map<String, byte[]> archiveFiles) {
        Map<String, byte[]> files = archiveFiles.entrySet().stream()
                .filter(entry -> !isSystemMetadata(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        String root = findSkillRoot(files);
        return files.entrySet().stream().collect(Collectors.toMap(entry -> relativeResourcePath(entry.getKey(), root, files), Map.Entry::getValue));
    }

    /**
     * macOS 的辅助文件不属于 Skill 内容。
     */
    private boolean isSystemMetadata(String path) {
        return path.startsWith(SkillArchiveConstant.MACOS_RESOURCE_DIRECTORY)
                || path.equals(SkillArchiveConstant.MACOS_METADATA_FILE)
                || path.endsWith(StrPool.SLASH + SkillArchiveConstant.MACOS_METADATA_FILE);
    }

    /**
     * 以唯一的 SKILL.md 确定包装目录，拒绝混入多个 Skill。
     */
    private String findSkillRoot(Map<String, byte[]> files) {
        List<String> instructions = files.keySet().stream()
                .filter(path -> path.equals(SkillArchiveConstant.SKILL_FILE)
                        || path.endsWith(StrPool.SLASH + SkillArchiveConstant.SKILL_FILE))
                .toList();
        if (instructions.size() != 1) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
        String instructionPath = instructions.getFirst();
        return instructionPath.substring(0, instructionPath.length() - SkillArchiveConstant.SKILL_FILE.length());
    }

    /**
     * 拒绝包装目录以外的文件及文件占用父目录的冲突，避免写入时丢失资源。
     */
    private String relativeResourcePath(String path, String root, Map<String, byte[]> files) {
        boolean parentIsFile = Stream.iterate(Path.of(path).getParent(), Objects::nonNull, Path::getParent)
                .map(parent -> parent.toString().replace('\\', '/'))
                .anyMatch(files::containsKey);
        if (!path.startsWith(root) || parentIsFile) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
        return SkillArchiveCodec.validatePath(path.substring(root.length()));
    }

    /**
     * 使用 AgentScope 的原生资源编码保存原始字节，二进制和文本走同一条转换链。
     */
    private Map<String, String> encodeResources(Map<String, byte[]> files) {
        return files.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(SkillArchiveConstant.SKILL_FILE))
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> SkillResourceCodec.encode(entry.getValue())));
    }

    /**
     * 仅提交指令和资源路径；包内资料作为数据，不获得工具或工作区访问能力。
     */
    private List<Msg> buildImportMessages(Map<String, byte[]> files) {
        String instructions = decodeInstructions(files.get(SkillArchiveConstant.SKILL_FILE));
        String input = JsonUtil.toJsonStrOrThrow(Map.of(SkillArchiveConstant.SKILL_FILE, instructions, "resourcePaths", files.keySet().stream().sorted().toList()));
        return List.of(Msg.builder()
                .role(MsgRole.SYSTEM)
                .textContent(PromptConstant.SKILL_IMPORT_PROMPT)
                .build(), Msg.builder()
                .role(MsgRole.USER)
                .textContent(input)
                .build());
    }

    /**
     * 指令必须为完整 UTF-8 文本，超长或损坏的内容不能通过截断进入模型。
     */
    private String decodeInstructions(byte[] bytes) {
        try {
            String content = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
            if (content.isBlank() || content.length() > SkillArchiveConstant.MAX_INSTRUCTION_CHARACTERS
                    || content.indexOf('\0') >= 0) {
                throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
            }
            return content;
        } catch (CharacterCodingException error) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_ARCHIVE_INVALID);
        }
    }

    /**
     * 缺失用户模型设置的错误直接返回，模型调用和输出异常统一映射为整理失败。
     */
    private UserSkillCreateDto normalize(List<Msg> messages) {
        String userId = UserThreadLocalUtil.getUserId();
        AiModelRuntimeConfig configuration = aiModelRuntimeService.requireRuntimeModel(userId, null);
        try {
            return parseDefinition(generateResponse(createModel(configuration), messages));
        } catch (RuntimeException error) {
            log.warn("Skill import model failed: modelId={}, errorType={}",
                    configuration.getModelId(), error.getClass().getSimpleName());
            throw new XException(ResponseCodeEnum.AGENT_SKILL_IMPORT_MODEL_ERROR);
        }
    }

    /**
     * 有界收集纯文本响应，模型思考块和其他非文本内容不参与解析。
     */
    private String generateResponse(Model model, List<Msg> messages) {
        return model.stream(messages, List.of(), GenerateOptions.builder()
                        .stream(false)
                        .build())
                .flatMapIterable(ChatResponse::getContent).ofType(TextBlock.class).map(TextBlock::getText)
                .collect(StringBuilder::new, this::appendResponse)
                .map(StringBuilder::toString).block(SkillArchiveConstant.MODEL_TIMEOUT);
    }

    /**
     * 在追加前限制完整响应大小，避免模型异常输出耗尽内存。
     */
    private void appendResponse(StringBuilder response, String chunk) {
        if (chunk.length() > SkillArchiveConstant.MAX_MODEL_RESPONSE_CHARACTERS - response.length()) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_IMPORT_MODEL_ERROR);
        }
        response.append(chunk);
    }

    /**
     * 模型返回值使用创建 Skill 相同的字段约束，正文不得包含 frontmatter。
     */
    private UserSkillCreateDto parseDefinition(String response) {
        UserSkillCreateDto definition = JsonUtil.OBJECT_MAPPER.readValue(unwrapJson(response), UserSkillCreateDto.class);
        if (definition == null || !validator.validate(definition).isEmpty()
                || definition.getContent().stripLeading().startsWith(SkillArchiveConstant.YAML_FRONTMATTER_DELIMITER)) {
            throw new XException(ResponseCodeEnum.AGENT_SKILL_IMPORT_MODEL_ERROR);
        }
        return definition;
    }

    /**
     * 兼容模型额外包裹 JSON 代码围栏，不改动 JSON 正文。
     */
    private String unwrapJson(String response) {
        String json = Objects.requireNonNullElse(response, "").strip();
        String fence = SkillArchiveConstant.MARKDOWN_FENCE;
        int firstNewline = json.indexOf('\n');
        return json.startsWith(fence) && json.endsWith(fence) && firstNewline >= 0
                ? json.substring(firstNewline + 1, json.length() - fence.length()).strip() : json;
    }

    /**
     * 保留文件和资源中的原始指令；仅在缺少 SKILL.md 时根据数据库字段生成。
     */
    private Map<String, byte[]> readSkillFiles(AgentSkill skill) throws IOException {
        if (skill.getOriginDir().isPresent()) {
            return SkillArchiveCodec.readDirectory(skill.getOriginDir().orElseThrow());
        }
        Map<String, byte[]> files = skill.getResources().entrySet().stream()
                .collect(Collectors.toMap(entry -> SkillArchiveCodec.validatePath(entry.getKey()),
                        entry -> SkillResourceCodec.decode(entry.getValue()), (previous, replacement) -> replacement));
        files.computeIfAbsent(SkillArchiveConstant.SKILL_FILE,
                path -> MarkdownSkillParser.generate(skill.getMetadata(), skill.getSkillContent()).getBytes(StandardCharsets.UTF_8));
        return files;
    }

    /**
     * 使用用户自己的模型端点、Key 和参数创建一次性模型。
     */
    private Model createModel(AiModelRuntimeConfig configuration) {
        return OpenAIChatModel.builder()
                .apiKey(configuration.getApiKey())
                .baseUrl(configuration.getBaseUrl())
                .modelName(configuration.getModelName())
                .contextWindowSize(configuration.getContextWindowSize())
                .stream(false)
                .generateOptions(GenerateOptions.builder()
                        .temperature(configuration.getTemperature())
                        .build())
                .build();
    }
}
