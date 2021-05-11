package io.c14r;

import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Main extends RouteBuilder {
    @Autowired
    private ProducerTemplate producerTemplate;

    @Value("${kafka.brokers}")
    private String kafkaBrokers;

    @Override
    public void configure() {
        from("kafka:ingestions?brokers=" + kafkaBrokers).routeId("ingest")
                .log("Received new Job")
                .removeHeaders("*")
                .to(DockerApi.URI_DIRECT_PROCESS_JOB)
                .log("Completed Job")
                .delay(1000)
                .log("Closed Job");
    }
}
