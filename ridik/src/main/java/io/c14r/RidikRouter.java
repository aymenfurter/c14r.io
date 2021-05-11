package io.c14r;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.*;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestParamType;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

import static com.mongodb.client.model.Filters.regex;


@Component
public class RidikRouter extends RouteBuilder {

    private static final String IMAGE_NAME = "imageName";
    private static final String REPOSITORY_NAME = "repositoryName";
    private static final String IMAGE_TAG = "imageTag";
    private static final String URI_DIRECT_CONSTRUCT_INSTRUCTIONS = "direct:constructInstructions";
    private static final String URI_DIRECT_SEARCH = "direct:search";
    private static final String URI_DIRECT_AUTOCOMPLETE = "direct:autocomplete";
    private static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    @Value("${neo4j.username}")
    private String neo4jUsername;

    @Value("${neo4j.password}")
    private String neo4jPassword;

    @Value("${neo4j.connection}")
    private String neo4jConnection;

    @Value("${mongo.connection}")
    private String mongoConnection;

    Neo4jFeed feed;

    @Override
    public void configure() {
        feed = new Neo4jFeed(neo4jConnection, neo4jUsername, neo4jPassword);
        MongoClient mongoClient = getMongoClient();
        MongoCollection<Document> images = mongoClient.getDatabase("imagedb").getCollection("images");

        rest("/images/details")
                .get()
                .param().name(IMAGE_NAME).type(RestParamType.query).endParam()
                .to(URI_DIRECT_CONSTRUCT_INSTRUCTIONS);

        rest("/images/search")
                .get()
                .param().name(IMAGE_NAME).type(RestParamType.query).endParam()
                .to(URI_DIRECT_SEARCH);

        rest("/images/autocomplete")
                .get()
                .param().name(IMAGE_NAME).type(RestParamType.query).endParam()
                .to(URI_DIRECT_AUTOCOMPLETE);

        from(URI_DIRECT_CONSTRUCT_INSTRUCTIONS).routeId("constructInstructions")
                .convertBodyTo(String.class)
                .process(x -> buildInstructions(images, x))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, constant("*"))
                .marshal().json();

        from(URI_DIRECT_AUTOCOMPLETE).routeId("autocomplete")
                .convertBodyTo(String.class)
                .process(x -> performAutcompletion(images, x))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, constant("*"))
                .marshal().json();

        from(URI_DIRECT_SEARCH).routeId("search")
                .convertBodyTo(String.class)
                .process(x -> x.getMessage().setBody(feed.search(x.getMessage().getHeader(IMAGE_NAME, String.class))))
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200))
                .setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, constant("*"))
                .marshal().json();
    }

    private void buildInstructions(MongoCollection<Document> images, Exchange x) {
        String fullImageName = x.getMessage().getHeader(IMAGE_NAME, String.class);
        List<String> hashes = feed.getHashes(fullImageName);

        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> variants = new ArrayList<>();
        for (String hash : hashes) {
            Map<String, Object> imageVariant = getImageDetails(hash, images, 0);
            variants.add(imageVariant);
        }

        Set<String> alternativeTags = new HashSet<>();
        String repoName = getRepoFromImage(fullImageName);
        String imageName = getNameFromImage(fullImageName);
        Document searchDoc = new Document(REPOSITORY_NAME, repoName);
        searchDoc.append(IMAGE_NAME, imageName);
        FindIterable<Document> iterable = images.find(searchDoc);

        for (Document doc : iterable) {
            alternativeTags.add((String) doc.get(IMAGE_TAG));
        }

        response.put("variants", variants);
        response.put("tags", alternativeTags);
        x.getMessage().setBody(response);
    }

    private void performAutcompletion(MongoCollection<Document> images, Exchange x) {
        String fullImageName = x.getMessage().getHeader(IMAGE_NAME, String.class);
        Set<String> queries = new HashSet<>();
        if (fullImageName.indexOf("/") == -1) fullImageName = "library/"+fullImageName;
        if (fullImageName.indexOf(":") == -1) fullImageName = fullImageName + ":latest";
        String imageName = getNameFromImage(fullImageName);
        Pattern pattern = Pattern.compile(".*"+Pattern.quote(imageName)+".*", Pattern.CASE_INSENSITIVE);
        MongoCursor<Document> iterable = images.find(regex(IMAGE_NAME, pattern)).limit(100).iterator();

        for (MongoCursor<Document> it = iterable; it.hasNext(); ) {
            Document doc = it.next();
            String tag = (String) doc.get(IMAGE_TAG);
            String name = (String) doc.get(IMAGE_NAME);
            String imageRepo = (String) doc.get(REPOSITORY_NAME);
            queries.add(imageRepo + "/" + name + ":" + tag);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("queries", queries);
        x.getMessage().setBody(response);
    }

    private Map<String, Object> getImageDetails(String hash, MongoCollection<Document> docs, int depth) {
        Map<String, Object> entry = new HashMap<>();
        FindIterable<Document> iterable = docs.find(new Document("_id", hash));
        Document doc = iterable.first();

        entry.put("id", hash);

        if (doc != null) {
            entry.put("last_pushed", doc.get("last_pushed"));
            String instructions = (String) doc.get("instructions");
            String calculatedInstructions = instructions;
            String baseOid = (String) doc.get("baseImageOID");
            String oid = (String) doc.get("_id");
            if (baseOid != null && !baseOid.equals(oid) && depth <= 10) {
                Map<String, Object> parent = getImageDetails(baseOid, docs, depth + 1);
                String parentInstructions = (String) parent.get("full_instructions");
                calculatedInstructions = StringUtils.substringAfter(instructions , parentInstructions);
                entry.put("parent", parent);
            }
            entry.put("instructions", calculatedInstructions);
            entry.put(IMAGE_NAME, doc.get(IMAGE_NAME));
            entry.put(IMAGE_TAG, doc.get(IMAGE_TAG));
            entry.put(REPOSITORY_NAME, doc.get(REPOSITORY_NAME));
            entry.put("full_instructions", instructions);
        }
        return entry;
    }

    private MongoClient getMongoClient() {
        ConnectionString connString = new ConnectionString(mongoConnection);
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(connString)
                .retryWrites(true)
                .build();

        return MongoClients.create(settings);
    }


    static String getRepoFromImage(String image) {
        return getUntil('/', image);
    }

    static String getNameFromImage(String image) {
        return getBetween('/', ':', image);
    }

    static String getBetween(char from, char to, String s) {
        return s.substring(s.indexOf(from) + 1, s.indexOf(to));
    }

    static String getUntil(char c, String s) {
        int iend = s.indexOf(c);
        String subString = null;
        if (iend != -1) {
            subString = s.substring(0, iend);
        }
        return subString;
    }
}