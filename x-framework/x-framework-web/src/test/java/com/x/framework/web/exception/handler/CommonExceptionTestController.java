package com.x.framework.web.exception.handler;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

/**
 * 用真实 MVC 异常分派验证通用错误处理和 SSE 断连处理的边界。
 */
@RestController
class CommonExceptionTestController {

    @GetMapping("/errors/disconnected")
    public void disconnected(@RequestParam("committed") boolean committed, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.ACCEPTED.value());
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        if (committed) {
            response.flushBuffer();
        }
        throw new AsyncRequestNotUsableException("ServletOutputStream failed to write", new IOException("Broken pipe"));
    }

    @GetMapping("/errors/io")
    public void ioFailure() throws IOException {
        throw new IOException("Workspace file could not be read");
    }

    @GetMapping("/errors/business")
    public void businessFailure() {
        throw new XException(ResponseCodeEnum.BAD_REQUEST);
    }
}
