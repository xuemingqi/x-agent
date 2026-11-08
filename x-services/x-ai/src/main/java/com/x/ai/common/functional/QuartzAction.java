package com.x.ai.common.functional;

import org.quartz.SchedulerException;

@FunctionalInterface
public interface QuartzAction {

    void run() throws SchedulerException;
}
