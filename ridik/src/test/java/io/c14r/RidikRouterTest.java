package io.c14r;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RidikRouterTest extends RidikRouter {
    @Test
    void testDockerImageParsing() throws Exception {
        assertEquals("afurter", RidikRouter.getRepoFromImage("afurter/todo.txt:latest"));
        assertEquals("todo.txt", RidikRouter.getNameFromImage("afurter/todo.txt:latest"));
    }
}
