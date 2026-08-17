package com.aarushi.qa.config;
import org.springframework.context.annotation.*; import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor; import java.util.concurrent.Executor;
@Configuration @EnableAsync
public class AsyncConfig {
 @Bean(name="ingestionExecutor") Executor ingestionExecutor() {
  var e=new ThreadPoolTaskExecutor(); e.setCorePoolSize(2); e.setMaxPoolSize(4);
  e.setQueueCapacity(50); e.setThreadNamePrefix("document-ingestion-"); e.initialize(); return e;
 }
}