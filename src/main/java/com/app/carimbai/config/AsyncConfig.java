package com.app.carimbai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Habilita @Async no projeto, usado hoje para envio de push notifications.
 * Pool pequeno e fila bounded: push e fire-and-forget; se acumular muito,
 * preferimos descartar (caller threads NUNCA bloqueiam num service de business).
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "pushExecutor")
    public TaskExecutor pushExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(50);
        exec.setThreadNamePrefix("push-");
        exec.setRejectedExecutionHandler((r, e) -> {
            // Push descartado se a fila lotar — nao queremos bloquear quem chamou.
            // Em prod, idealmente logar via Logger; deixamos sem log aqui pra nao depender.
        });
        exec.initialize();
        return exec;
    }
}
