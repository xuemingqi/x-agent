package com.x.framework.web.feign.conf;

import feign.Logger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeignConfigurationTest {

    @Test
    void configuresFullFeignLogging() {
        FeignConfiguration configuration = new FeignConfiguration();

        Logger.Level loggerLevel = configuration.feignLoggerLevel();

        assertThat(loggerLevel).isEqualTo(Logger.Level.FULL);
    }
}
