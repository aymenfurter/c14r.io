package io.c14r;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class E2ETest {
    Network network = Network.newNetwork();
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer(DockerImageName.parse("mongo:4.0.10"));
    private static final GenericContainer neo4jContainer = new GenericContainer(DockerImageName.parse("docker.io/bitnami/neo4j:4-debian-10")).withExposedPorts(7687);
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"));
    private static final GenericContainer dexterContainer = new GenericContainer(DockerImageName.parse("docker.io/library/dexter:1.0")).withNetworkMode("host");
    private static final GenericContainer ridikContainer = new GenericContainer(DockerImageName.parse("docker.io/library/ridik:1.0")).withNetworkMode("host");
    private static final GenericContainer jobbieContainer = new GenericContainer(DockerImageName.parse("docker.io/library/jobbie:1.0")).withNetworkMode("host");
    Logger logger = LoggerFactory.getLogger(E2ETest.class);

    private static String getNeo4jURL() {
        String neo4jURL = "neo4j://" + neo4jContainer.getHost()+ ":" + neo4jContainer.getMappedPort(7687);
        return neo4jURL;
    }

    static {

        mongoDBContainer.start();
        neo4jContainer.start();
        kafka.start();

        addEnv("KAFKA_BROKERS", kafka.getBootstrapServers());
        addEnv("MONGO_CONNECTION", mongoDBContainer.getReplicaSetUrl());
        addEnv("NEO4J_CONNECTION", getNeo4jURL());
        addEnv("NEO4J_USERNAME", "neo4j");
        addEnv("NEO4J_PASSWORD", "bitnami");

        jobbieContainer.start();
        dexterContainer.start();
        ridikContainer.start();
    }

    private static void addEnv(String env, String value) {
        dexterContainer.addEnv(env, value);
        jobbieContainer.addEnv(env, value);
        ridikContainer.addEnv(env, value);
    }
    @Test
    void testE2EScenario() throws Exception {

        TimeUnit.SECONDS.sleep(30);

        HttpResponse indexRequestBase = indexImage("{\"imageName\":\"debian\", \"imageTag\": \"stretch-20191014\", \"repositoryName\": \"library\"}");
        HttpResponse indexRequest = indexImage("{\"imageName\":\"tomcat\", \"imageTag\": \"8.5.47-jdk8-openjdk\", \"repositoryName\": \"library\"}");
        HttpResponse indexRequestMcr = indexImage("{\"imageName\":\"windows/servercore\", \"imageTag\": \"ltsc2019-amd64\", \"repositoryName\": \"mcr.microsoft.com\"}");
        assertEquals(202, indexRequestBase.statusCode());
        assertEquals(202, indexRequest.statusCode());
        assertEquals(202, indexRequestMcr.statusCode());

        TimeUnit.SECONDS.sleep(30);

        HttpResponse indexedBaseMcr = getDetailForImageImage("mcr.microsoft.com/windows/servercore:ltsc2019-amd64");
        String responseBaseMcr = indexedBaseMcr.body().toString();
        assertEquals(200, indexedBaseMcr.statusCode());
        assertNotEquals("{\"variants\":[],\"tags\":[]}", responseBaseMcr);
        assertTrue(isJSONValid(responseBaseMcr));

        HttpResponse notIndex = getDetailForImageImage("library/foobar:notindex");
        assertEquals(200, notIndex.statusCode());
        assertEquals("{\"variants\":[],\"tags\":[]}", notIndex.body().toString());

        HttpResponse indexed = getDetailForImageImage("library/tomcat:8.5.47-jdk8-openjdk");
        assertEquals(200, indexed.statusCode());
        String response = indexed.body().toString();
        assertNotEquals("{\"variants\":[],\"tags\":[]}", indexed.body().toString());
        assertTrue(response.contains("\"parent\":")); // Check if parent was recognized in mongodb
        assertTrue(isJSONValid(response));

        HttpResponse indexedSearch = doSearchForImageImage("library/tomcat:8.5.47-jdk8-openjdk");
        assertEquals(200, indexedSearch.statusCode());
        String responseSearch = indexedSearch.body().toString();
        assertTrue(isJSONValid(responseSearch));
        assertTrue(responseSearch.contains("tomcat:8.5.47-jdk8-openjdk"));
        assertTrue(responseSearch.contains("debian:stretch-20191014")); // Check if Base Image was recognized in neo4j

        HttpResponse indexedBase = getDetailForImageImage("library/debian:stretch-20191014");
        String responseBase = indexedBase.body().toString();
        assertEquals(200, indexedBase.statusCode());
        assertNotEquals("{\"variants\":[],\"tags\":[]}", responseBase);
        assertTrue(isJSONValid(responseBase));
        assertTrue(responseBase.contains("debian"));
    }
    boolean isJSONValid(String test) {
        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            // edited, to include @Arthur's comment
            // e.g. in case JSONArray is valid as well...
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

    private HttpResponse doSearchForImageImage(String imageName) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        int targetPort = 7081; //jobbieContainer.getMappedPort(8080);
        String targetUrl = "http://" + ridikContainer.getContainerIpAddress() + ":" + targetPort + "/api/images/search?imageName=" + imageName;
        HttpRequest req =  HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse getDetailForImageImage(String imageName) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        int targetPort = 7081; //jobbieContainer.getMappedPort(8080);
        String targetUrl = "http://" + ridikContainer.getContainerIpAddress() + ":" + targetPort + "/api/images/details?imageName=" + imageName;
        HttpRequest req =  HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse indexImage(String imageRequestJSON) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();
        int targetPort = 8080; //jobbieContainer.getMappedPort(8080);
        String targetUrl = "http://" + jobbieContainer.getContainerIpAddress() + ":" + targetPort + "/api/images/request";
        HttpRequest req =  HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .timeout(Duration.ofMinutes(1))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(imageRequestJSON))
                .build();

        return client.send(req, HttpResponse.BodyHandlers.discarding());
    }
}
