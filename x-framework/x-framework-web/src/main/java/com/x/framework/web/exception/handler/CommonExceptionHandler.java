package com.x.framework.web.exception.handler;

import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.response.BaseResult;
import com.x.framework.common.response.ResultUtil;
import com.x.framework.common.exception.XBaseException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

/**
 * 通用异常处理类
 *
 * @author xuemingqi
 */
@Slf4j
@ControllerAdvice
public class CommonExceptionHandler {

    /**
     * 异步响应已不可用时不再写入；响应参数让 MVC 将请求标记为已处理，跳过消息转换。
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleAsyncRequestNotUsableException(AsyncRequestNotUsableException ex,
                                                    HttpServletResponse response) {
        log.debug("异步响应已关闭，停止写入响应: {}", ex.getMessage());
    }

    @ExceptionHandler(XBaseException.class)
    @ResponseBody
    @ResponseStatus(code = HttpStatus.OK)
    public BaseResult<Void> handlerXException(XBaseException ex) {
        return ResultUtil.buildResultError(ex.getResponseCodeEnum());
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    @ResponseStatus(code = HttpStatus.OK)
    public BaseResult<Void> handlerException(Exception ex) {
        log.error("throw Exception, {}", ExceptionUtils.getStackTrace(ex));
        return ResultUtil.buildResultError(ResponseCodeEnum.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseBody
    @ResponseStatus(code = HttpStatus.OK)
    public BaseResult<Void> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.error("throw MethodArgumentNotValidException, {}", ExceptionUtils.getStackTrace(ex));
        String msg;
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (!ObjectUtils.isEmpty(fieldError)) {
            msg = fieldError.getDefaultMessage();
        } else {
            msg = ex.getBindingResult().getAllErrors().getFirst().getDefaultMessage();
        }
        return ResultUtil.buildResultParamError(msg);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            HttpMediaTypeNotSupportedException.class,
            BindException.class
    })
    @ResponseBody
    @ResponseStatus(code = HttpStatus.OK)
    public BaseResult<Void> handleBadRequestException(Exception ex) {
        log.error("throw BadRequestException, {}", ExceptionUtils.getStackTrace(ex));
        return ResultUtil.buildResultError(ResponseCodeEnum.BAD_REQUEST);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseBody
    @ResponseStatus(code = HttpStatus.OK)
    public BaseResult<Void> handleHttpRequestMethodNotSupportedException(HttpRequestMethodNotSupportedException ex) {
        log.error("throw HttpRequestMethodNotSupportedException, {}", ExceptionUtils.getStackTrace(ex));
        return ResultUtil.buildResultError(ResponseCodeEnum.METHOD_NOT_ALLOWED);
    }

}
