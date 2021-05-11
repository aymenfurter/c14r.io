package io.c14r;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@CamelSpringBootTest
@ContextConfiguration(initializers = DexterIntegrationTest.class)
class DexterIntegrationTest implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));
    private static final GenericContainer neo4jContainer = new GenericContainer(DockerImageName.parse("docker.io/bitnami/neo4j:4-debian-10")).withExposedPorts(7687);
    Logger logger = LoggerFactory.getLogger(DexterIntegrationTest.class);

    private String getNeo4jURL() {
        String neo4jURL = "neo4j://" + neo4jContainer.getHost()+ ":" + neo4jContainer.getMappedPort(7687);
        return neo4jURL;
    }

    static {
        mongoDBContainer.start();
        neo4jContainer.start();
    }

    @Autowired
    private CamelContext camelContext;

    @Autowired
    private ProducerTemplate producerTemplate;

    @Test
    void testImageScans() throws Exception {
        Neo4jCheck feed = new Neo4jCheck(getNeo4jURL(), "neo4j", "bitnami");
        scanImage("{\"imageName\":\"windows/servercore\", \"imageTag\": \"ltsc2019-amd64\", \"repositoryName\": \"mcr.microsoft.com\"}");
        assertEquals(1, feed.count());
        scanImage("{\"imageName\":\"windows/servercore\", \"imageTag\": \"ltsc2016-amd64\", \"repositoryName\": \"mcr.microsoft.com\"}");
        assertEquals(2, feed.count());
        scanImage("{\"imageName\":\"dotnet/aspnet\", \"imageTag\": \"5.0\", \"repositoryName\": \"mcr.microsoft.com\"}");
        assertEquals(3, feed.count());
        scanImage("{\"imageName\":\"ubuntu\", \"imageTag\": \"latest\", \"repositoryName\": \"library\"}");
        scanImage("{\"imageName\":\"tomcat\", \"imageTag\": \"8.5.47-jdk8-openjdk\", \"repositoryName\": \"library\"}");
        assertEquals(5, feed.count());
    }

    private void scanImage(String windows2019) {
        Exchange windows2019Exchange = getExchange(windows2019);
        String imageScanResult = windows2019Exchange.getMessage().getBody(String.class);
        assertNotNull(imageScanResult);
        logger.info("Body is " + windows2019Exchange.getMessage().getBody(String.class));
    }

    private Exchange getExchange(String tomcat) {
        Exchange ex = ExchangeBuilder.anExchange(camelContext)
                .withBody(tomcat)
                .build();

        producerTemplate.send("direct:processJob", ex);
        return ex;
    }

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
        TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
                "mongo.connection=" + mongoDBContainer.getReplicaSetUrl(),
                "neo4j.connection=" + getNeo4jURL(),
                "neo4j.username=" + "neo4j",
                "neo4j.password=" + "bitnami");
    }
}
