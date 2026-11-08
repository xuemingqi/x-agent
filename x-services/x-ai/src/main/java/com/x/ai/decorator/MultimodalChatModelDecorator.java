package com.x.ai.decorator;

import com.x.ai.common.model.ModelImageResponse;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.ImageBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 为模型调用补充图片响应暂存能力。
 * SDK 2.0.1 的 ReasoningContext 只汇总文字和工具调用，图片暂存在当前订阅的上下文中。
 */
@RequiredArgsConstructor
public class MultimodalChatModelDecorator implements Model {

    private final Model delegate;

    private final ModelImageResponse images;


    @Override
    public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
        return Flux.defer(() -> {
            images.getImages().clear();
            images.setLastResponse(null);
            images.setReasoningContent(false);
            images.setTextPlaceholder(false);
            Flux<ChatResponse> responses = delegate.stream(messages, tools, options);
            return responses.doOnNext(response -> captureResponse(images, response))
                    .concatWith(Flux.defer(() -> {
                        if (images.getImages().isEmpty() || images.isReasoningContent()) {
                            return Flux.empty();
                        }
                        images.setTextPlaceholder(true);
                        return Flux.just(ChatResponse.builder().id(images.getLastResponse().getId())
                                .content(List.of(TextBlock.builder().text(" ").build())).build());
                    }));
        });
    }

    /**
     * 保存图片分片，并区分 SDK 已支持的内容，避免给正常文字回复添加占位。
     */
    private void captureResponse(ModelImageResponse images, ChatResponse response) {
        images.setLastResponse(response);
        for (ContentBlock block : response.getContent()) {
            if (block instanceof DataBlock || block instanceof ImageBlock) {
                images.getImages().add(block);
            } else if (block instanceof TextBlock text && text.getText() != null && !text.getText().isEmpty()
                    || block instanceof ThinkingBlock thinking && thinking.getThinking() != null && !thinking.getThinking().isEmpty()
                    || block instanceof ToolUseBlock) {
                images.setReasoningContent(true);
            }
        }
    }

    @Override
    public String getModelName() {
        return delegate.getModelName();
    }

    @Override
    public int getContextWindowSize() {
        return delegate.getContextWindowSize();
    }

    @Override
    public boolean supportsNativeStructuredOutput() {
        return delegate.supportsNativeStructuredOutput();
    }

    @Override
    public boolean supportsNativeStructuredOutputWithTools() {
        return delegate.supportsNativeStructuredOutputWithTools();
    }
}
