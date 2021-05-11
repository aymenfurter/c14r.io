package io.c14r;

import org.apache.camel.ProducerTemplate;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ImageRelationship extends DexterRouteBuilder {
    public static final String URI_DIRECT_CREATE_RELATIPNSHIPS = "direct:createRelatipnships";
    Logger logger = LoggerFactory.getLogger(ImageRelationship.class);

    @Autowired
    private ProducerTemplate producerTemplate;

    @Value("${neo4j.username}")
    private String neo4jUsername;

    @Value("${neo4j.password}")
    private String neo4jPassword;

    @Value("${neo4j.connection}")
    private String neo4jConnection;

    Neo4jFeed feed;

    @Override
    public void configure() {
        feed = new Neo4jFeed(neo4jConnection, neo4jUsername, neo4jPassword);

        from(URI_DIRECT_CREATE_RELATIPNSHIPS).routeId("createRelationships")
                .process(x -> {
                    String baseImageName = x.getMessage().getHeader(ImageAnalysis.DEXTER_BASE_IMAGE, String.class);
                    String imageOID = x.getMessage().getHeader(DockerApi.CAMEL_MONGO_OID, String.class);
                    String name = x.getMessage().getHeader(DockerApi.REPOSITORY_NAME) + "/" + x.getMessage().getHeader(IMAGE_NAME) + ":" + x.getMessage().getHeader(IMAGE_TAG);
                    try {
                        feed.createImage(name, imageOID);
                    } catch (Neo4jException ex) {
                        logger.warn("Error during writing to Neo4j for Image {}", name);
                    }
                    try {
                        feed.link(name, baseImageName);
                    } catch (Neo4jException ex) {
                        logger.warn("Error during writing to Neo4j linking {}", name);
                    }
                    logger.info("Completed: {}", name);
                });
    }
}
