package io.c14r;

import org.neo4j.driver.*;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRelationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.neo4j.driver.Values.parameters;

public class Neo4jFeed implements AutoCloseable {
    private static final String IMAGE_NAME = "imageName";
    private final Driver driver;
    Logger logger = LoggerFactory.getLogger(Neo4jFeed.class);

    public Neo4jFeed(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }

    public Integer count() {
        Result result = driver.session().run("MATCH (n:Image) RETURN count(n) as count");
        Value val = result.next().get(0);
        return val.asInt();
    }

    public List<String> getHashes (String image) {
        logger.info("Starting search for {} ", image);
        Result result = driver.session().run("MATCH (searchImage:Image {name:$imageName})-[:HASH_OF]-(hashes:ImageHash) RETURN hashes", parameters(IMAGE_NAME, image));

        List<String> resultList = new ArrayList<>();
        while (result.hasNext()) {
            Record resultAsList = result.next();
            Map<String, Object> entry = resultAsList.asMap();
            Map<String, Object> hash = (Map<String, Object>) prepareMap(entry).get("hashes");
            resultList.add((String) hash.get("hash"));
        }

        logger.info("Found {}", resultList.size());
        return resultList;
    }
    public List<Map<String, Object>> search(String image) {
        logger.info("Starting search for {}", image);
        Result result = driver.session().run("MATCH (searchImage:Image {name:$imageName})-[childOf:CHILD_OF]-(relatedImages:Image)\n" +
                "MATCH (searchImage:Image {name:$imageName})-[:HASH_OF]-(hashes:ImageHash)\n" +
                "RETURN searchImage, childOf, relatedImages, hashes;", parameters(IMAGE_NAME, image));
        List<Map<String, Object>> resultList = new ArrayList<>();
        while (result.hasNext()) {
            processEntry(result, resultList);
        }

        if (resultList.isEmpty()) {
            result = driver.session().run("MATCH (searchImage:Image {name:$imageName})-[:HASH_OF]-(hashes:ImageHash)\n" +
                    "RETURN searchImage, hashes;", parameters(IMAGE_NAME, image));
            resultList = new ArrayList<>();
        }

        while (result.hasNext()) {
            processEntry(result, resultList);
        }

        return resultList;
    }

    private void processEntry(Result result, List<Map<String, Object>> resultList) {
        Record recordAsList = result.next();
        Map<String, Object> entry = recordAsList.asMap();
        resultList.add(prepareMap(entry));
    }

    public Map<String, Object> prepareMap(Map<String, Object> mp) {
        Map<String, Object> newMap = new HashMap<>();
        Iterator<Map.Entry<String, Object>> it = mp.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Object> pair = it.next();
            Map<String, String> nodeAsMap = new HashMap<>();
            if (pair.getValue() instanceof InternalNode) {
                InternalNode node = (InternalNode) pair.getValue();
                String id = Long.toString(node.id());
                nodeAsMap.put("id", id);
                if (node.labels().contains("Image")) {
                    nodeAsMap.put("type", "Image");
                    String name = (String) node.asMap().get("name");
                    nodeAsMap.put("name", name);
                } else if (node.labels().contains("ImageHash")) {
                    String hash = (String) node.asMap().get("hash");
                    nodeAsMap.put("hash", hash);
                    nodeAsMap.put("type", "ImageHash");
                    String name = (String) node.asMap().get("name");
                    nodeAsMap.put("name", name);
                }
            }

            if (pair.getValue() instanceof org.neo4j.driver.internal.InternalRelationship) {
                InternalRelationship node = (org.neo4j.driver.internal.InternalRelationship) pair.getValue();
                String identity = (String) node.asMap().get("identity");
                nodeAsMap.put("identity", identity);
                String start = Long.toString(node.startNodeId());
                String end = Long.toString(node.endNodeId());
                nodeAsMap.put("start", start);
                nodeAsMap.put("end", end);
                nodeAsMap.put("type", "Relationship");
            }

            newMap.put(pair.getKey(), nodeAsMap);
        }

        return newMap;
    }
}
