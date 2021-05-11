package io.c14r;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.component.http.HttpComponent;
import org.apache.camel.component.http.HttpEndpoint;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.apache.commons.codec.digest.DigestUtils;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DockerApi extends DexterRouteBuilder {
    private static final String DOCKERAPI_IMAGE_NAME = "imageName";
    private static final String DOCKERAPI_IMAGE_TAG = "imageTag";
    private static final String DOCKERAPI_REPOSITORY_NAME = "repositoryName";
    private static final String DOCKERAPI_LAYERS = "layers";
    private static final String DOCKERAPI_INSTRUCTION = "instruction";
    private static final String DOCKERAPI_LAST_PUSHED = "last_pushed";
    private static final String DOCKERAPI_DIGEST = "digest";

    public static final String REQUEST_URL = "requestUrl";
    public static final String URI_DIRECT_PROCESS_IMAGES = "direct:processImages";
    public static final String URI_DIRECT_PROCESS_JOB = "direct:processJob";
    public static final String INSTRUCTIONS = "instructions";
    public static final String REPOSITORY_NAME = "repositoryName";
    public static final String ID = "_id";
    public static final String LAST_PUSHED = "last_pushed";
    public static final String DIGEST = "digest";
    public static final String MONGO_DOCUMENT = "MongoDocument";
    public static final String CAMEL_MONGO_OID = "CamelMongoOid";
    private static final String URI_PROCESS_MCR_IMAGE = "direct:processMcrImage";
    private static final String URI_PROCESS_DOCKERHUB_IMAGE = "direct:processDockerhubImage";

    Logger logger = LoggerFactory.getLogger(DockerApi.class);

    @Autowired
    private ProducerTemplate producerTemplate;
    private ObjectMapper mapper;
    JacksonDataFormat jsonFormat;
    HttpComponent component;
    HttpEndpoint http;

    @Override
    public void configure() throws Exception {
        mapper = new ObjectMapper();
        JsonParser.Feature allowUnescaptedCtrl = JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature();
        jsonFormat = new JacksonDataFormat();
        mapper.enable(allowUnescaptedCtrl);
        jsonFormat.setObjectMapper(mapper);
        component = new HttpComponent();
        component.setCamelContext(getContext());
        http = (HttpEndpoint) component.createEndpoint("https://hub.docker.com");
        HttpEndpoint httpInitialRequest = (HttpEndpoint) component.createEndpoint("https://hub.docker.com?okStatusCodeRange=1-500");
        http.setDisableStreamCache(true);
        httpInitialRequest.setDisableStreamCache(true);


        from(URI_PROCESS_MCR_IMAGE)
                .setHeader(Exchange.HTTP_URI, simple("https://${headers.repositoryName}/v2/${headers.imageName}/manifests/${headers.imageTag}"))
                .log("First MCR request ...")
                .to(httpInitialRequest)
                .to(URI_DIRECT_PROCESS_IMAGES);

        from(URI_PROCESS_DOCKERHUB_IMAGE)
                .setHeader(Exchange.HTTP_URI, simple("https://hub.docker.com/v2/repositories/${headers.repositoryName}/${headers.imageName}/tags/${headers.imageTag}/images"))
                .to(http)
                .to(URI_DIRECT_PROCESS_IMAGES);

        from(URI_DIRECT_PROCESS_JOB).routeId("processJob")
                .convertBodyTo(String.class)
                .unmarshal(jsonFormat)
                .process(this::prepareRequest)
                .log(constructLogStatusText("Retrieving Information for"))
                .setHeader("CamelHttpMethod", constant("GET"))
                .setHeader("CamelHttpChunked", constant("false"))
                .choice()
                    .when(simple("${headers.repositoryName} contains 'mcr.microsoft.com'"))
                        .to(URI_PROCESS_MCR_IMAGE)
                    .otherwise()
                        .to(URI_PROCESS_DOCKERHUB_IMAGE);

        from(URI_DIRECT_PROCESS_IMAGES).routeId("processImages")
                .convertBodyTo(String.class)
                .log(constructLogStatusText("Unmarshalling"))
                .unmarshal(jsonFormat)
                .process(this::processImage)
                .log(constructLogStatusText("Done Processing"));
    }

    void prepareRequest(Exchange exchange) {
        Map<String, Object> image = exchange.getMessage().getBody(Map.class);
        exchange.getMessage().setHeader(IMAGE_NAME, image.get(DOCKERAPI_IMAGE_NAME));
        exchange.getMessage().setHeader(IMAGE_TAG, image.get(DOCKERAPI_IMAGE_TAG));
        exchange.getMessage().setHeader(REPOSITORY_NAME, image.get(DOCKERAPI_REPOSITORY_NAME));
    }

    void processImage(Exchange exchange) throws JsonProcessingException {
        List<Map<String, Object>> images = exchange.getMessage().getBody(ArrayList.class);
        if (images != null && !images.isEmpty() && images.get(0).get("history") == null) {
            handleHubSyntax(exchange, images);
        } else {
            handleOCISyntax(exchange);
        }

    }

    private void handleOCISyntax(Exchange exchange) throws JsonProcessingException {
        Map<String, Object> image = exchange.getMessage().getBody(Map.class);
        if (image != null) {
            List<Map<String, String>> history = (List<Map<String, String>>) image.get("history");
            if (history != null && !history.isEmpty()) {
                processHistory(exchange, image, history);
            }
            exchange.getMessage().getBody(ArrayList.class);
        }
    }

    private void processHistory(Exchange exchange, Map<String, Object> image, List<Map<String, String>> history) throws JsonProcessingException {
        StringBuilder instructionsBuffer = new StringBuilder();
        List<Map<String, String>> historyReversed = new ArrayList<>();
        historyReversed.addAll(history);
        Collections.reverse(historyReversed);
        for (Map<String, String> layer : historyReversed) {
            String layerJson = layer.get("v1Compatibility");
            Map<String, Object> map = mapper.readValue(layerJson, Map.class);
            if (map != null && map.containsKey("container_config")) {
                Map<String, Object> containerConfig = (Map<String, Object>) map.get("container_config");
                if (containerConfig != null && containerConfig.containsKey("Cmd")) {
                    ArrayList<String> cmds = (ArrayList<String>) containerConfig.get("Cmd");
                    instructionsBuffer.append(cmds.stream().collect(Collectors.joining(" ")));
                    instructionsBuffer.append("\n");
                }
            }
        }

        if (instructionsBuffer.length() != 0) {
            instructionsBuffer.setLength(instructionsBuffer.length() - 1);
            String instructions = instructionsBuffer.toString();
            analyzeImage(exchange, image, instructions);
        } else {
            log.warn("Could not extract instructions");
        }
    }

    private void handleHubSyntax(Exchange exchange, List<Map<String, Object>> images) {
        for (Map<String, Object> image : images) {
            StringBuilder instructionsBuffer = new StringBuilder();
            List<Map<String, Object>> layers = (List<Map<String, Object>>) image.get(DOCKERAPI_LAYERS);

            if (layers != null) {
                for (Map<String, Object> layer : layers) {
                    instructionsBuffer.append(layer.get(DOCKERAPI_INSTRUCTION));
                    instructionsBuffer.append("\n");
                }

                instructionsBuffer.setLength(instructionsBuffer.length() - 1);

                String instructions = instructionsBuffer.toString();
                analyzeImage(exchange, image, instructions);
            } else {
                log.warn("Could not extract instructions");
            }
        }
    }

    private void analyzeImage(Exchange exchange, Map<String, Object> image, String instructions) {
        String oid = DigestUtils.sha256Hex(instructions);
        Document doc = getDocument(exchange, image, instructions, oid);
        Exchange imageExchange = ExchangeBuilder.anExchange(this.getContext())
                .withBody(doc)
                .withHeader(CAMEL_MONGO_OID, oid)
                .withHeader(IMAGE_NAME, exchange.getMessage().getHeader(IMAGE_NAME))
                .withHeader(REPOSITORY_NAME, exchange.getMessage().getHeader(REPOSITORY_NAME))
                .withHeader(IMAGE_TAG, exchange.getMessage().getHeader(IMAGE_TAG))
                .withHeader(MONGO_DOCUMENT, doc)
                .build();
        producerTemplate.send(ImageAnalysis.URI_DIRECT_PROCESS_IMAGE, imageExchange);
    }

    Document getDocument(Exchange exchange, Map<String, Object> image, String instructions, String oid) {
        Document doc = new Document();
        doc.put(INSTRUCTIONS, instructions);
        doc.put(DIGEST, image.get(DOCKERAPI_DIGEST));
        doc.put(LAST_PUSHED, image.get(DOCKERAPI_LAST_PUSHED));
        doc.put(IMAGE_NAME, exchange.getMessage().getHeader(IMAGE_NAME));
        doc.put(IMAGE_TAG, exchange.getMessage().getHeader(IMAGE_TAG));
        doc.put(REPOSITORY_NAME, exchange.getMessage().getHeader(REPOSITORY_NAME));
        doc.put(ID, oid);
        return doc;
    }
}
