package com.x.ai.scheduler.config;

import org.jspecify.annotations.NonNull;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class QuartzSchedulerConfiguration {

    @Bean
    public SchedulerFactoryBeanCustomizer quartzJobFactoryCustomizer(AutowireCapableBeanFactory beanFactory) {
        return factory -> factory.setJobFactory(new SpringBeanJobFactory() {
            @Override
            protected @NonNull Object createJobInstance(@NonNull TriggerFiredBundle bundle) throws Exception {
                Object job = super.createJobInstance(bundle);
                beanFactory.autowireBean(job);
                return job;
            }
        });
    }
}
