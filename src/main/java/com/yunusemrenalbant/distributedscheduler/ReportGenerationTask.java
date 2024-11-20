package com.yunusemrenalbant.distributedscheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ReportGenerationTask {
    @Value("${spring.application.name}")
    private String instanceName;

    @Scheduled(cron = "*/15 * * * * *")
    @SchedulerLock(name = "ReportGenerationTask.generateDailyReport", lockAtLeastFor = "PT10S", lockAtMostFor = "PT15S")
    public void scheduledTask() {
        System.out.println("Report sent by: " + instanceName + " at " + LocalDateTime.now());
    }
}
