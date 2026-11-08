package com.x.ai.converter;

import com.x.ai.common.constants.AgentConstant;
import io.agentscope.core.message.Base64Source;
import io.agentscope.core.message.DataBlock;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.message.ToolResultMessage;
import io.agentscope.extensions.model.openai.dto.OpenAIMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrowserScreenshotFormatterTest {

    @Test
    void appendsScreenshotAfterAllParallelToolResultsAndKeepsOriginalImage() {
        ToolResultBlock screenshot = screenshot();
        List<OpenAIMessage> messages = new MultimodalOpenAIFormatter(true).format(List.of(
                new ToolResultMessage(screenshot), new ToolResultMessage("other", "inspect", "checked")));

        assertThat(messages).hasSize(3);
        assertThat(messages.stream().map(OpenAIMessage::getRole)).containsExactly("tool", "tool", "user");
        assertThat(messages.get(0).getContentAsString()).isEqualTo("用户截图");
        assertThat(messages.getLast().isMultimodal()).isTrue();
        assertThat(screenshot.getOutput()).hasSize(2);
    }

    @Test
    void switchingToTextOnlyModelDoesNotSendHistoricalScreenshotBytes() {
        List<OpenAIMessage> messages = new MultimodalOpenAIFormatter(false).format(List.of(new ToolResultMessage(screenshot())));
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getContentAsString()).isEqualTo("用户截图");
        assertThat(messages.getFirst().isMultimodal()).isFalse();
    }

    @Test
    void declinedCaptureDoesNotInventAUserImageMessage() {
        List<OpenAIMessage> messages = new MultimodalOpenAIFormatter(true).format(List.of(
                new ToolResultMessage("capture-1", AgentConstant.BROWSER_SCREENSHOT_TOOL_NAME, "用户拒绝截图")));
        assertThat(messages).hasSize(1);
        assertThat(messages.getFirst().getRole()).isEqualTo("tool");
        assertThat(messages.getFirst().getContentAsString()).isEqualTo("用户拒绝截图");
    }

    private ToolResultBlock screenshot() {
        return ToolResultBlock.of("capture-1", AgentConstant.BROWSER_SCREENSHOT_TOOL_NAME, List.of(
                TextBlock.builder().text("用户截图").build(), DataBlock.builder()
                        .source(Base64Source.builder().mediaType("image/png").data("aW1hZ2U=").build()).build()));
    }
}
