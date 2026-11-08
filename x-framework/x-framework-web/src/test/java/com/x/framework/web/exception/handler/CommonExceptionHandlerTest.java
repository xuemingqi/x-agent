package com.x.framework.web.exception.handler;

import com.x.framework.common.enums.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommonExceptionHandlerTest {

    private JacksonJsonHttpMessageConverter converter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        converter = spy(new JacksonJsonHttpMessageConverter());
        mockMvc = MockMvcBuilders.standaloneSetup(new CommonExceptionTestController())
                .setControllerAdvice(new CommonExceptionHandler())
                .setMessageConverters(converter)
                .build();
        clearInvocations(converter);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void disconnectedStreamNeverUsesMessageConvertersOrChangesResponseStatus(boolean committed) throws Exception {
        mockMvc.perform(get("/errors/disconnected").param("committed", Boolean.toString(committed)))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""))
                .andExpect(result -> assertThat(result.getResolvedException())
                        .isInstanceOf(AsyncRequestNotUsableException.class));

        verifyNoInteractions(converter);
    }

    @Test
    void ordinaryIoFailureStillReturnsTheExistingJsonErrorContract() throws Exception {
        mockMvc.perform(get("/errors/io"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ResponseCodeEnum.INTERNAL_SERVER_ERROR.getCode()))
                .andExpect(jsonPath("$.msg").value(ResponseCodeEnum.INTERNAL_SERVER_ERROR.getMsg()));
    }

    @Test
    void businessFailureStillReturnsItsOriginalErrorCode() throws Exception {
        mockMvc.perform(get("/errors/business"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ResponseCodeEnum.BAD_REQUEST.getCode()))
                .andExpect(jsonPath("$.msg").value(ResponseCodeEnum.BAD_REQUEST.getMsg()));
    }
}
