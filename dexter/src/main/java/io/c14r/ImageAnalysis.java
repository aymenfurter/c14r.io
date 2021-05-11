package io.c14r;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mongodb.MongoDbEndpoint;
import org.apache.commons.codec.digest.DigestUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ImageAnalysis extends DexterRouteBuilder {
    public static final String URI_DIRECT_PROCESS_IMAGE = "direct:processImage";
    public static final String URI_DIRECT_SEARCH_BASE_IMAGES = "direct:searchBaseImages";
    public static final String DEXTER_BASE_IMAGE = "DexterBaseImage";
    public static final String DEXTER_BASE_IMAGE_OID = "baseImageOID";
    private static final String DB = "imagedb";
    public static final String OPERATION = "save";

    Logger logger = LoggerFactory.getLogger(ImageAnalysis.class);

    @Autowired
    private ProducerTemplate producerTemplate;

    @Value("${mongo.connection}")
    private String mongoConnection;

    private MongoClient mongoClient;

    @Override
    public void configure() {
        mongoClient = getMongoClient();
        MongoDbEndpoint mongo = getMongoDbEndpoint(OPERATION, DB, mongoClient);

        from(URI_DIRECT_PROCESS_IMAGE).routeId("processImage")
                .log(constructLogStatusText("Start Analyzing"))
                .to(URI_DIRECT_SEARCH_BASE_IMAGES)
                .log(constructLogStatusText("Analyzing done."))
                .log("Base Image is: ${headers." + DEXTER_BASE_IMAGE + "}")
                .log("Storing in MongoDB ${headers." + IMAGE_NAME + "}:${headers." + IMAGE_TAG + "}")
                .to(mongo)
                .log(constructLogStatusText("Storing in MongoDB done"))
                .to(ImageRelationship.URI_DIRECT_CREATE_RELATIPNSHIPS);

        from(URI_DIRECT_SEARCH_BASE_IMAGES).routeId("searchBaseImages")
                .process(this::processSearch);
    }

    private void processSearch(Exchange exchange) {
        Document doc = exchange.getMessage().getHeader("MongoDocument", Document.class);
        String instructions = (String) doc.get("instructions");
        Document baseImage = lookupBaseImage(instructions, mongoClient.getDatabase(DB));
        if (baseImage != null) {
            String baseimg = baseImage.get(DockerApi.REPOSITORY_NAME) + "/" + baseImage.get(IMAGE_NAME) + ":" + baseImage.get(IMAGE_TAG);
            exchange.getMessage().setHeader(DEXTER_BASE_IMAGE, baseimg);
            String baseInstructions = (String) baseImage.get("instructions");
            if (!instructions.equals(baseInstructions)) {
                doc.put(DEXTER_BASE_IMAGE_OID, baseImage.get("_id"));
            } else {
                log.warn("Skipping linking base image as instructions are identical");
            }
        }
    }

    private MongoDbEndpoint getMongoDbEndpoint(String operation, String db, MongoClient mongoClient) {
        MongoDbEndpoint mongo = getContext().getEndpoint("mongodb:" + db + "?operation=" + operation, MongoDbEndpoint.class);
        mongo.setCollection("images");
        mongo.setDatabase(db);
        mongo.setOperation(operation);
        mongo.setMongoConnection(mongoClient);
        return mongo;
    }

    private MongoClient getMongoClient() {
        ConnectionString connString = new ConnectionString(mongoConnection);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .retryWrites(true)
                .build();

        return MongoClients.create(settings);
    }

    private Document lookupBaseImage(String instructions, MongoDatabase db) {
        boolean completedLookup = false;
        String instr = instructions;

        do {
            String stripped = stripLast(instr);
            String oid = DigestUtils.sha256Hex(stripped);
            logger.debug("Checking for OID ... {}", oid);
            logger.debug("Instructions .... {}", stripped);

            FindIterable<Document> iterable = db.getCollection("images").find(new Document("_id", oid));

            if (iterable.first() != null) {
                Document baseImage = iterable.first();
                logger.debug("Found Base Image! {}", oid);
                return baseImage;
            }

            if (stripped.equals(instr)) {
                completedLookup = true;
            } else {
                instr = stripped;
            }
        } while (!completedLookup);

        return null;
    }

    String stripLast(String x) {
        if (x.lastIndexOf("\n") > 0) {
            return x.substring(0, x.lastIndexOf("\n"));
        } else {
            return x;
        }
    }

}
