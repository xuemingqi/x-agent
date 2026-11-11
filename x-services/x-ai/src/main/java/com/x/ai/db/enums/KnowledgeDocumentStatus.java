package com.x.ai.db.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum KnowledgeDocumentStatus {

    INDEXING("INDEXING", "索引中"),

    READY("READY", "可检索"),

    FAILED("FAILED", "索引失败");

    /**
     * 数据库存储的稳定状态编码。
     */
    @EnumValue
    private final String code;

    /**
     * 状态的业务含义。
     */
    private final String desc;

    public static KnowledgeDocumentStatus fromCode(String code) {
        return Arrays.stream(values()).filter(status -> status.code.equals(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown knowledge document status"));
    }
}
