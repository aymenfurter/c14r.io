package io.c14r;

import org.neo4j.driver.*;
import org.neo4j.driver.exceptions.Neo4jException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.neo4j.driver.Values.parameters;

public class Neo4jFeed implements AutoCloseable {
    private final Driver driver;
    Logger logger = LoggerFactory.getLogger(Neo4jFeed.class);

    public Neo4jFeed(String uri, String user, String password) {
        driver = GraphDatabase.driver(uri, AuthTokens.basic(user, password));

        try {
            driver.session().run("CREATE CONSTRAINT ON (image:Image)\n" +
                    "ASSERT image.name IS UNIQUE;");
        } catch (Neo4jException e) {
            logger.warn("Initializing neo4j DB may have failed", e);
        }
    }

    @Override
    public void close() throws Exception {
        driver.close();
    }

    public void createImage(final String name, String hash) {
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                //MERGE (n {name: '3'})
                tx.run("MERGE (image:Image {name: $name}) MERGE (imagehash:ImageHash {name: $name, hash: $hash}) MERGE (imagehash)-[rel:HASH_OF]->(image)", parameters("name", name, "hash", hash));
                return null;
            });
        }
    }

    public void link(final String nameChild, final String nameParent) {
        try (Session session = driver.session()) {
            session.writeTransaction(tx -> {
                tx.run("MATCH (parent:Image {name: $nameParent}) MATCH (child:Image {name: $nameChild}) MERGE (child)-[rel:CHILD_OF]->(parent)", parameters("nameChild", nameChild, "nameParent", nameParent));
                return null;
            });
        }
    }
}
