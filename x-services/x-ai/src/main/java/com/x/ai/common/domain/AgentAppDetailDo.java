package com.x.ai.common.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
public class AgentAppDetailDo extends AgentAppDo {

    /**
     * 从私有对象存储读取并校验后的当前版本应用包。
     */
    private AgentAppPackageDo content;
}
