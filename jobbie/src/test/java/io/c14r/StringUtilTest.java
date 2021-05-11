package io.c14r;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class StringUtilTest {
    @Test
    void testDockerParse() throws Exception {
        String dockerImage = "library/ubuntu:latest";
        String json = JobbieStringUtils.getJsonFromImage(dockerImage);
        assertNotNull(json);
        assertEquals("{\"imageName\":\"ubuntu\", \"imageTag\": \"latest\", \"repositoryName\": \"library\"}", json);
    }


    @Test
    void testDockerParseMCR() throws Exception {
        String dockerImage = "mcr.microsoft.com/dotnet/aspnet:5";
        String json = JobbieStringUtils.getJsonFromImage(dockerImage);
        assertNotNull(json);
        assertEquals("{\"imageName\":\"dotnet/aspnet\", \"imageTag\": \"5\", \"repositoryName\": \"mcr.microsoft.com\"}", json);
    }
}
