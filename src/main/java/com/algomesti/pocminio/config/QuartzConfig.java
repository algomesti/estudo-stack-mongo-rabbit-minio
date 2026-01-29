package com.algomesti.pocminio.config;

import com.algomesti.pocminio.job.CloudSyncJob;
import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class QuartzConfig {

    @Bean
    public JobDetail syncJobDetail() {
        return JobBuilder.newJob(CloudSyncJob.class)
                .withIdentity("cloudSyncJob")
                .storeDurably()
                .build();
    }

    @Bean
    public Trigger syncJobTrigger() {
        return TriggerBuilder.newTrigger()
                .forJob(syncJobDetail())
                .withIdentity("cloudSyncTrigger")
                .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(30) // Tenta a cada 30s
                        .repeatForever())
                .build();
    }
}