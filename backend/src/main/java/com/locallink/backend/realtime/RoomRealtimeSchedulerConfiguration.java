package com.locallink.backend.realtime;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class RoomRealtimeSchedulerConfiguration {

    @Bean(name = "roomWebSocketHeartbeatScheduler")
    TaskScheduler roomWebSocketHeartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("locallink-ws-heartbeat-");
        return scheduler;
    }
}
