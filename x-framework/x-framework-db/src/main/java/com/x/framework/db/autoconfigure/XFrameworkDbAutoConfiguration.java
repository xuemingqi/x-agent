package com.x.framework.db.autoconfigure;

import com.x.framework.db.mybatis.MybatisPlusConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import(MybatisPlusConfig.class)
public class XFrameworkDbAutoConfiguration {
}
