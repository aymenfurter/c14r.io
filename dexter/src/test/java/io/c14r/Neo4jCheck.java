package io.c14r;

import org.neo4j.driver.*;

public class Neo4jCheck implements AutoCloseable {
    private final Driver driver;

    public Neo4jCheck(String uri, String user, String password) {
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
}
