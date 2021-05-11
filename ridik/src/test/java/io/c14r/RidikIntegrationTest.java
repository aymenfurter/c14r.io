package io.c14r;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.bson.Document;
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

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@CamelSpringBootTest
@ContextConfiguration(initializers = RidikIntegrationTest.class)
class RidikIntegrationTest implements ApplicationContextInitializer<ConfigurableApplicationContext> {
	private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));
	private static final GenericContainer neo4jContainer = new GenericContainer(DockerImageName.parse("docker.io/bitnami/neo4j:4-debian-10")).withExposedPorts(7687);
	private final TestData testData = new TestData(this);
	Logger logger = LoggerFactory.getLogger(RidikIntegrationTest.class);

	String getNeo4jURL() {
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
	void testDetail() throws Exception {
		Exchange ex = ExchangeBuilder.anExchange(camelContext)
				.withHeader("imageName", "library/ros:noetic-ros-core")
				.build();
		producerTemplate.send("direct:constructInstructions", ex);
		Map<String, Object> response = ex.getIn().getBody(Map.class);
		assertTrue(response.containsKey("variants"));
		assertTrue(response.containsKey("tags"));
		assertNotNull(ex.getIn().getBody());

		List variants = (List) response.get("variants");
		assertEquals(1, variants.size());
		assertEquals("1", ((HashMap) variants.get(0)).get("id"));
		assertEquals("noetic-ros-core", ((HashSet)response.get("tags")).iterator().next());
	}

	void testSearchImage(int expectedResult, String imageName) {
		Exchange ex = ExchangeBuilder.anExchange(camelContext)
				.withHeader("imageName", imageName)
				.build();

		producerTemplate.send("direct:search", ex);
		assertNotNull(ex.getIn().getBody());
		List response = ex.getIn().getBody(List.class);
		if (response.size() == 0) return;
		HashMap result = (HashMap) response.get(0);
		HashSet resp = (HashSet) result.get("queries");
		assertEquals(expectedResult, resp.size());
	}

	@Test
	void testSearch() throws Exception {
		testSearchImage(1, "ros");
		testSearchImage(1, "ubuntu");
		testSearchImage(0, "ubu");
	}

	@Test
	void testAutoCompletion() throws Exception {
		testAutocompleteImage("ubuntu", 1);
		testAutocompleteImage("ubu", 1);
		testAutocompleteImage("debian", 0);
	}

	private void testAutocompleteImage(String imageName, int expectedResult) {
		Exchange ex = ExchangeBuilder.anExchange(camelContext)
				.withHeader("imageName", imageName)
				.build();

		producerTemplate.send("direct:autocomplete", ex);
		assertNotNull(ex.getIn().getBody());
		List response = ex.getIn().getBody(List.class);
		HashMap result = (HashMap) response.get(0);
		HashSet resp = (HashSet) result.get("queries");

		assertEquals(expectedResult, resp.size());
	}


	MongoClient getTestMongoClient() {
		ConnectionString connString = new ConnectionString(mongoDBContainer.getReplicaSetUrl());
		MongoClientSettings settings = MongoClientSettings.builder()
				.applyConnectionString(connString)
				.retryWrites(true)
				.build();

		return MongoClients.create(settings);
	}

	private void insertTestdata() {

		testData.insertTestdata();
	}
	private Document createTestDataDocument (String imageName, String imageTag, String repositoryName, String oid, String parent) {
		return testData.createTestDataDocument(imageName, imageTag, repositoryName, oid, parent);
	}
	private Document createTestDataDocument (String imageName, String imageTag, String repositoryName, String oid) {
		return testData.createTestDataDocument(imageName, imageTag, repositoryName, oid);
	}

	@Override
	public void initialize(ConfigurableApplicationContext configurableApplicationContext) {
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(configurableApplicationContext,
				"mongo.connection=" + mongoDBContainer.getReplicaSetUrl(),
				"neo4j.connection=" + getNeo4jURL(),
				"neo4j.username=" + "neo4j",
				"neo4j.password=" + "bitnami");
		testData.insertTestdata();
	}
}
