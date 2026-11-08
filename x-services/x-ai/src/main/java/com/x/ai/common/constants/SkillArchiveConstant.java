package com.x.ai.common.constants;

import java.time.Duration;

/**
 * Skill 导入导出的格式约定与处理上限。
 */
public interface SkillArchiveConstant {

    int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
    int MAX_EXPANDED_BYTES = 50 * 1024 * 1024;
    int MAX_ENTRIES = 500;
    int MAX_PATH_CHARACTERS = 1024;

    /**
     * TAR 还需要容纳条目头、PAX 元数据与块对齐填充。
     */
    int MAX_TAR_BYTES = MAX_EXPANDED_BYTES + MAX_ENTRIES * 4096;

    int MAX_INSTRUCTION_CHARACTERS = 100_000;
    int MAX_MODEL_RESPONSE_CHARACTERS = MAX_INSTRUCTION_CHARACTERS * 6;
    Duration MODEL_TIMEOUT = Duration.ofMinutes(3);

    String SKILL_FILE = "SKILL.md";
    String BASE64_PREFIX = "base64:";
    String MARKDOWN_FENCE = "```";
    String YAML_FRONTMATTER_DELIMITER = "---";
    String MACOS_RESOURCE_DIRECTORY = "__MACOSX/";
    String MACOS_METADATA_FILE = ".DS_Store";
}
