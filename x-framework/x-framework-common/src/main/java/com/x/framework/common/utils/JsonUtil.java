package com.x.framework.common.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalTimeSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * @author xuemingqi
 */
@Slf4j
public class JsonUtil {

    private static final DateTimeFormatter LOCAL_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter LOCAL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter LOCAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public static final ObjectMapper OBJECT_MAPPER = createDefaultMapper();
    private static final ObjectWriter NON_NULL_OBJECT_WRITER = OBJECT_MAPPER.rebuild()
            .changeDefaultPropertyInclusion(ignored -> JsonInclude.Value.construct(
                    JsonInclude.Include.NON_NULL,
                    JsonInclude.Include.NON_NULL))
            .build()
            .writer();

    private static ObjectMapper createDefaultMapper() {
        SimpleModule dateTimeModule = new SimpleModule("x-framework-date-time");
        dateTimeModule.addSerializer(
                LocalDateTime.class,
                new LocalDateTimeSerializer(LOCAL_DATE_TIME_FORMATTER));
        dateTimeModule.addDeserializer(
                LocalDateTime.class,
                new LocalDateTimeDeserializer(LOCAL_DATE_TIME_FORMATTER));
        dateTimeModule.addSerializer(LocalDate.class, new LocalDateSerializer(LOCAL_DATE_FORMATTER));
        dateTimeModule.addDeserializer(LocalDate.class, new LocalDateDeserializer(LOCAL_DATE_FORMATTER));
        dateTimeModule.addSerializer(LocalTime.class, new LocalTimeSerializer(LOCAL_TIME_FORMATTER));
        dateTimeModule.addDeserializer(LocalTime.class, new LocalTimeDeserializer(LOCAL_TIME_FORMATTER));

        return JsonMapper.builder()
                .addModule(dateTimeModule)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    public static String toJsonStr(Object data) {
        try {
            return toJsonStrOrThrow(data);
        } catch (IllegalArgumentException exception) {
            log.error("serialize json failed", exception.getCause());
            return null;
        }
    }

    /**
     * 序列化对象，失败时由调用边界处理异常。
     */
    public static String toJsonStrOrThrow(Object data) {
        try {
            return OBJECT_MAPPER.writeValueAsString(data);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("serialize json failed", exception);
        }
    }

    public static String toJsonStrNonNull(Object data) {
        try {
            return NON_NULL_OBJECT_WRITER.writeValueAsString(data);
        } catch (JacksonException e) {
            log.error("serialize non-null json failed", e);
            return null;
        }
    }

    public static <T> T jsonToBean(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            log.error("deserialize json failed, targetType={}", type.getName(), e);
            return null;
        }
    }

    public static JsonNode readTree(String json) throws IOException {
        return OBJECT_MAPPER.readTree(json);
    }

    public static <T> T objectToObject(Object object, TypeReference<T> type) {
        return OBJECT_MAPPER.convertValue(object, type);
    }

    public static <T> T strToObject(String object, TypeReference<T> type) {
        try {
            return strToObjectOrThrow(object, type);
        } catch (IllegalArgumentException exception) {
            log.error("deserialize json by type reference failed", exception.getCause());
            return null;
        }
    }

    /**
     * 按泛型类型反序列化 JSON，失败时由调用边界处理异常。
     */
    public static <T> T strToObjectOrThrow(String object, TypeReference<T> type) {
        try {
            return OBJECT_MAPPER.readValue(object, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("deserialize json failed", exception);
        }
    }

    public static boolean isJsonString(String jsonString) {
        try {
            OBJECT_MAPPER.readTree(jsonString);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String toJsonStrIfNotAlready(Object object) {
        if (object instanceof String value && isJsonString(value)) {
            return value;
        }
        return toJsonStr(object);
    }
}
