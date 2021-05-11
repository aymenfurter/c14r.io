package io.c14r;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.junit.jupiter.api.Disabled;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@CamelSpringBootTest
@Disabled
@ContextConfiguration(initializers = MCRIntegrationTests.class)
class MCRIntegrationTests implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));
    private static final GenericContainer neo4jContainer = new GenericContainer(DockerImageName.parse("docker.io/bitnami/neo4j:4-debian-10")).withExposedPorts(7687);
    Logger logger = LoggerFactory.getLogger(MCRIntegrationTests.class);

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
        // These images currenntly do not work, to be fixed (e.g. checking if layer information might be available through application/vnd.docker.distribution.manifest.list.v2+json.)
        Neo4jCheck feed = new Neo4jCheck(getNeo4jURL(), "neo4j", "bitnami");
        scan("{\"imageName\":\"windows\", \"imageTag\": \"20H2-KB4586781\", \"repositoryName\": \"mcr.microsoft.com\"}");
        scan("{\"imageName\":\"windows/nanoserver\", \"imageTag\": \"20H2-KB4592438-amd64\", \"repositoryName\": \"mcr.microsoft.com\"}");
        assertEquals(0, feed.count());
    }

    private void scan(String windows2019) {
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
