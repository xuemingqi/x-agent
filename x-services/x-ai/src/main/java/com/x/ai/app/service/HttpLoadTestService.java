package com.x.ai.app.service;

import com.x.ai.common.domain.HttpLoadResultDo;
import com.x.ai.common.dto.HttpLoadTestDto;

public interface HttpLoadTestService {

    /**
     * 启动当前用户的一轮压测，限制并发、速率、总次数和持续时间。
     */
    HttpLoadResultDo start(HttpLoadTestDto request);

    /**
     * 读取当前用户的运行快照，终态在当前节点短暂保留。
     */
    HttpLoadResultDo get(String id);

    /**
     * 停止当前用户的压测并取消在途网络请求。
     */
    HttpLoadResultDo stop(String id);
}
