package io.c14r;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class DockerApiTest extends DockerApi {
    @Test
    void testDocumentTransformation() throws Exception {
        Map<String, Object> input = new HashMap<String, Object>();
        Document doc = getDocument(mockExchange(), input, "test-instructions", "42");
        assertEquals("test-instructions", doc.get("instructions"));
        assertEquals("42", doc.get("_id"));
    }

    @Test
    void testPrepareDocument() throws Exception {
        Exchange mocked = mockExchange();
        prepareRequest(mocked);
        verify(mocked.getMessage(), times(1)).setHeader("imageName", null);
    }

    @Test
    void testProcessImage() throws Exception {
        Exchange mocked = mockExchange();
        processImage(mocked);
        verify(mocked.getMessage(), times(2)).getBody(ArrayList.class);
    }
    private Exchange mockExchange() {
        Exchange e = mock(Exchange.class);
        Map<String, String> dummyBody = new HashMap<String, String>();
        dummyBody.put("imageTag", "testtag");
        Message m = mock(Message.class);
        when(m.getBody(Map.class)).thenReturn(dummyBody);
        when(m.getBody(ArrayList.class)).thenReturn(new ArrayList<String>());
        when(e.getMessage()).thenReturn(m);
        return e;
    }
}
