package io.c14r;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static net.bytebuddy.matcher.ElementMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@CamelSpringBootTest
@ContextConfiguration(initializers = JobbieIntegrationTest.class)
class JobbieIntegrationTest implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));

    static {
        kafka.start();
    }

    @Test
    void test() throws Exception {
        Exchange ex = ExchangeBuilder.anExchange(camelContext)
                .withBody("{\"imageName\":\"debian\", \"imageTag\": \"stretch-20191014\", \"repositoryName\": \"library\"}")
                .build();
        producerTemplate.send("direct:startIngest", ex);
        assertNull(ex.getException());
        assertEquals(202, ex.getMessage().getHeader("CamelHttpResponseCode"));
        assertNotNull(ex.getMessage().getHeader("org.apache.kafka.clients.producer.RecordMetadata"));
    }

    @Test
    void testIndexUpdate() throws Exception {
        Exchange ex = ExchangeBuilder.anExchange(camelContext)
                .withBody("foobar")
                .build();
        producerTemplate.send("direct:startIndexUpdate", ex);
        assertNull(ex.getException());
        assertEquals(200, ex.getMessage().getHeader("CamelHttpResponseCode"));
        TimeUnit.SECONDS.sleep(10);
    }

    @Test
    void testIndexUpdateMcr() throws Exception {
        Exchange ex = ExchangeBuilder.anExchange(camelContext)
                .withBody("foobar")
                .build();
        producerTemplate.send("direct:startMcrIndexUpdate", ex);
        assertNull(ex.getException());
        assertEquals(200, ex.getMessage().getHeader("CamelHttpResponseCode"));
        TimeUnit.SECONDS.sleep(10);
    }

    void validateKafka() {
        Map<String, List<PartitionInfo>> topics;

        Properties props = new Properties();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("group.id", "test-consumer-group");
        props.put("auto.offset.reset", "latest");
        props.put("auto.commit.enable", "false");
        props.put("consumer.timeout.ms", "5000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        KafkaConsumer<String, String> consumer = new KafkaConsumer<String, String>(props);
        topics = consumer.listTopics();
        assertTrue(topics.containsKey("ingestions"));

        String topic = "ingestions";
        consumer.subscribe(Collections.singletonList(topic));
        consumer.poll(Duration.ofSeconds(10));
        consumer.assignment().forEach(System.out::println);
        AtomicLong maxTimestamp = new AtomicLong();
        AtomicReference<ConsumerRecord<String, String>> latestRecord = new AtomicReference<>();
        consumer.endOffsets(consumer.assignment()).forEach((topicPartition, offset) -> {
            consumer.seek(topicPartition, (offset==0) ? offset:offset - 1);
            consumer.poll(Duration.ofSeconds(10)).forEach(record -> {
                if (record.timestamp() > maxTimestamp.get()) {
                    maxTimestamp.set(record.timestamp());
                    latestRecord.set(record);
                }
            });
        });

        String jsonData = latestRecord.get().value();
        assertThat(jsonData, CoreMatchers.containsString("imageName"));
        assertThat(jsonData, CoreMatchers.containsString("repositoryName"));
        assertThat(jsonData, CoreMatchers.containsString("imageTag"));
        consumer.close();
    }

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
                "kafka.brokers=" + kafka.getBootstrapServers());
    }
}
