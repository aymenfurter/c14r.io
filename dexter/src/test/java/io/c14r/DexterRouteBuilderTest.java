package io.c14r;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DexterRouteBuilderTest extends DexterRouteBuilder {
    @Test
    void testLogStatusTextOutput() throws Exception {
        assertEquals("Test:${headers.imageName}:${headers.imageTag}", constructLogStatusText("Test"));
    }

    @Override
    public void configure() throws Exception {
    }
}
