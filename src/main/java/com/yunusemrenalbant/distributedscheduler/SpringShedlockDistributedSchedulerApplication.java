package com.yunusemrenalbant.distributedscheduler;

import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableSchedulerLock(defaultLockAtMostFor = "PT60S")
@EnableScheduling
public class SpringShedlockDistributedSchedulerApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringShedlockDistributedSchedulerApplication.class, args);
	}

}
