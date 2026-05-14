package com.platform.api;

import com.platform.queue.config.KafkaEnvelopeProducerAutoConfiguration;
import com.platform.queue.config.QueueKafkaAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;


@SpringBootApplication(scanBasePackages = {"com.platform.api", "com.platform.core"})
@Import({KafkaEnvelopeProducerAutoConfiguration.class, QueueKafkaAutoConfiguration.class})
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }
}
