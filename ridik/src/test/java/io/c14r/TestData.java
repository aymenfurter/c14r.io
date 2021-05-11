package io.c14r;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

public class TestData {
    private final RidikIntegrationTest ridikIntegrationTest;
    public TestData(RidikIntegrationTest ridikIntegrationTest) {
        this.ridikIntegrationTest = ridikIntegrationTest;
    }

    void insertTestdata() {
        Neo4jTestFeed testfeed = new Neo4jTestFeed(ridikIntegrationTest.getNeo4jURL(), "neo4j", "bitnami");
        testfeed.createImage("library/ros:noetic-ros-core", "1");
        testfeed.createImage("library/ubuntu:focal", "2");
        testfeed.link("library/ros:noetic-ros-core", "library/ros:noetic-ros-core");

        Document ubuntu = createTestDataDocument("ubuntu", "focal", "library", "1");
        Document ros = createTestDataDocument("ros", "noetic-ros-core", "library", "2", "1");
        MongoClient mongoClient = ridikIntegrationTest.getTestMongoClient();
        MongoCollection<Document> images = mongoClient.getDatabase("imagedb").getCollection("images");
        images.insertOne(ros);
        images.insertOne(ubuntu);
    }

    Document createTestDataDocument(String imageName, String imageTag, String repositoryName, String oid, String parent) {
        Document doc = createTestDataDocument(imageName, imageTag, repositoryName, oid);
        doc.put("parent", parent);
        doc.put("baseImageOID", "2");
        return doc;
    }

    Document createTestDataDocument(String imageName, String imageTag, String repositoryName, String oid) {
        Document doc = new Document();
        doc.put("instructions", "foobar");
        doc.put("digest", "foobar");
        doc.put("lastPushed", "1620675661");
        doc.put("imageName", imageName);
        doc.put("imageTag", imageTag);
        doc.put("repositoryName", repositoryName);
        doc.put("_id", oid);
        return doc;
    }
}