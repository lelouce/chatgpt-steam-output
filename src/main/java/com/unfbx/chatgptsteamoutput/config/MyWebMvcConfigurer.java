package com.unfbx.chatgptsteamoutput.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 *
 * @author qinchuan
 * @date 2023/4/10 18:27.
 */
@Configuration
public class MyWebMvcConfigurer implements WebMvcConfigurer {

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(getAsyncExecutor());
    }

    @Bean
    public ThreadPoolTaskExecutor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(20); //设置核心线程数
        executor.setMaxPoolSize(50); //设置最大线程数
        executor.setQueueCapacity(100); //设置队列容量
        executor.setKeepAliveSeconds(60); //设置线程存活时间
        executor.setThreadNamePrefix("MyExecutor-"); //设置线程名前缀
        executor.setWaitForTasksToCompleteOnShutdown(true); //设置是否等待所有任务完成后再关闭线程池
        return executor;
    }
}
