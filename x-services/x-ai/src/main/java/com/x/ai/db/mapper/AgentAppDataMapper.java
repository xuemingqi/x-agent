package com.x.ai.db.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.x.ai.db.entity.AgentAppData;
import org.apache.ibatis.annotations.Insert;

public interface AgentAppDataMapper extends BaseMapper<AgentAppData> {

    @Insert("""
            INSERT INTO agent_app_data (id, owner_user_id, app_id, data_key, value_json)
            VALUES (#{id}, #{ownerUserId}, #{appId}, #{dataKey}, #{valueJson})
            ON DUPLICATE KEY UPDATE value_json = VALUES(value_json)
            """)
    int upsert(AgentAppData data);
}
