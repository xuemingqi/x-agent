package com.x.ai.common.domain;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.springframework.core.io.Resource;

@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class KnowledgeDownloadDo {

    private String fileName;

    private String contentType;

    private Resource resource;
}
