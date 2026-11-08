package com.x.ai.converter;

import com.x.ai.common.constants.AgentConstant;
import com.x.framework.common.utils.JsonUtil;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.UserMessage;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import io.agentscope.extensions.model.openai.dto.OpenAIResponse;
import io.agentscope.extensions.model.openai.formatter.OpenAIChatFormatter;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 保留聊天接口返回的图片内容，同时复用 SDK 的思考、工具调用和用量解析。
 */
@RequiredArgsConstructor
public class MultimodalOpenAIFormatter extends OpenAIChatFormatter {

    private final boolean imageInput;


    @Override
    protected List<OpenAIMessage> doFormat(List<Msg> messages) {
        List<Msg> inputs = new ArrayList<>();
        List<ContentBlock> screenshots = new ArrayList<>();
        for (Msg message : messages) {
            if (message.getRole() != MsgRole.TOOL && !screenshots.isEmpty()) {
                inputs.add(new UserMessage(List.copyOf(screenshots)));
                screenshots.clear();
            }
            if (message.getRole() != MsgRole.TOOL || message.getContentBlocks(ToolResultBlock.class).stream()
                    .noneMatch(result -> AgentConstant.BROWSER_SCREENSHOT_TOOL_NAME.equals(result.getName()))) {
                inputs.add(message);
                continue;
            }
            for (ToolResultBlock result : message.getContentBlocks(ToolResultBlock.class)) {
                if (!AgentConstant.BROWSER_SCREENSHOT_TOOL_NAME.equals(result.getName())) {
                    inputs.add(new ToolResultMessage(result));
                    continue;
                }
                List<ContentBlock> text = result.getOutput().stream().filter(TextBlock.class::isInstance).toList();
                inputs.add(new ToolResultMessage(new ToolResultBlock(result.getId(), result.getName(), text,
                        result.getMetadata(), result.getState())));
                List<ContentBlock> images = result.getOutput().stream()
                        .filter(block -> block instanceof ImageBlock || block instanceof DataBlock).toList();
                if (imageInput && !images.isEmpty()) {
                    screenshots.add(TextBlock.builder().text("工具 " + result.getId() + " 返回的用户授权截图：").build());
                    screenshots.addAll(images);
                }
            }
        }
        if (!screenshots.isEmpty()) {
            inputs.add(new UserMessage(screenshots));
        }
        return super.doFormat(inputs);
    }

    @Override
    protected OpenAIMessage convertMessage(Msg message, boolean hasMediaContent) {
        OpenAIMessage converted = super.convertMessage(message, hasMediaContent);
        if (imageInput && hasMediaContent && message.getRole() == MsgRole.ASSISTANT) {
            // SDK 只为 user 转换图片；复用内容转换，保留 assistant 的角色、思考和工具调用。
            List<ContentBlock> content = message.getContent().stream()
                    .filter(block -> block instanceof TextBlock || block instanceof DataBlock || block instanceof ImageBlock)
                    .toList();
            converted.setContent(super.convertMessage(new UserMessage(content), true).getContent());
        }
        if (!imageInput && converted.isMultimodal()) {
            converted.setContent(converted.getContentAsString());
        }
        return converted;
    }

    @Override
    public ChatResponse parseResponse(OpenAIResponse response, Instant startTime) {
        OpenAIMessage message = response.getFirstChoice() == null
                ? null : response.getFirstChoice().getEffectiveMessage();
        if (message == null || !(message.getContent() instanceof List<?>)) {
            return super.parseResponse(response, startTime);
        }
        Object originalContent = message.getContent();
        List<ContentBlock> content = parseContent(JsonUtil.OBJECT_MAPPER.valueToTree(originalContent));
        // SDK 2.0.1 只解析文本，且反序列化后的 content 元素可能是 Map。
        message.setContent(null);
        try {
            ChatResponse parsed = super.parseResponse(response, startTime);
            List<ContentBlock> blocks = new ArrayList<>(parsed.getContent());
            blocks.addAll(content);
            return ChatResponse.builder()
                    .id(parsed.getId())
                    .content(blocks)
                    .usage(parsed.getUsage())
                    .metadata(parsed.getMetadata())
                    .finishReason(parsed.getFinishReason())
                    .build();
        } finally {
            message.setContent(originalContent);
        }
    }

    /**
     * 图片保留为数据块，交由会话文件服务验证和保存，避免把 Base64 当作回复文字。
     */
    private List<ContentBlock> parseContent(JsonNode parts) {
        List<ContentBlock> blocks = new ArrayList<>();
        for (JsonNode part : parts) {
            String type = part.path("type").asText();
            if ("text".equals(type) && !part.path("text").asText().isEmpty()) {
                blocks.add(TextBlock.builder().text(part.path("text").asText()).build());
            } else if ("image_url".equals(type)) {
                JsonNode imageUrl = part.has("image_url") ? part.get("image_url") : part.path("imageUrl");
                String url = imageUrl.isTextual() ? imageUrl.asText() : imageUrl.path("url").asText();
                if (!url.isBlank()) {
                    blocks.add(DataBlock.builder().source(URLSource.builder()
                            .url(url).mimeType("image/*").build()).build());
                }
            }
        }
        return blocks;
    }
}
