package me.paulbares;

import me.paulbares.spring.conf.CamundaProcessEngineConfiguration;
import me.paulbares.spring.conf.NotificationConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
        NotificationConfiguration.class,
        CamundaProcessEngineConfiguration.class
})
public class Application {}
