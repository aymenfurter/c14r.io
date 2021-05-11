package io.c14r;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.ExchangeBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class JobbieRouter extends RouteBuilder {
    private static final String URI_DIRECT_INDEX_PAGE = "direct:indexPage";
    private static final String URI_DIRECT_INDEX_REPO = "direct:indexRepo";
    private static final String URI_DIRECT_INDEX_UPDATE = "direct:indexUpdate";
    private static final String URI_DIRECT_START_INDEX_UPDATE = "direct:startIndexUpdate";
    private static final String URI_DIRECT_START_INDEX_UPDATE_MCR = "direct:startMcrIndexUpdate";
    private static final String URI_DIRECT_START_INGEST = "direct:startIngest";

    @Value("${kafka.brokers}")
    private String kafkaBrokers;

    Logger logger = LoggerFactory.getLogger(JobbieRouter.class);
    private static final String DOCKERAPI_IMAGE_NAME = "imageName";
    private static final String DOCKERAPI_IMAGE_TAG = "imageTag";
    private static final String DOCKERAPI_REPOSITORY_NAME = "repositoryName";

    private boolean isIndexing = false;
    private int page = 0;
    String lastImage = "";

    @Autowired
    private ProducerTemplate producerTemplate;

    @Override
    public void configure() {
        rest("/images/")
                .post("request").to(URI_DIRECT_START_INGEST)
                .post("batch").to(URI_DIRECT_START_INDEX_UPDATE)
                .post("batchMCR").to(URI_DIRECT_START_INDEX_UPDATE_MCR);

        // 1) Trigger new Indexing Job (response is returned instantly but the job can take many hour to complete)
        from(URI_DIRECT_START_INDEX_UPDATE).routeId("startIndexUpdate")
                .wireTap(URI_DIRECT_INDEX_UPDATE)
                .setBody().simple("${null}")
                .process(this::constructIndexReport)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

        // 2) will prepare the request to the most popular docker repositories
        from(URI_DIRECT_INDEX_UPDATE).routeId("indexUpdate")
                .process(this::updatePopularImages);

        // 3) will execute previously constructred target URL, e.g. https://hub.docker.com/api/content/v1/products/search and call indexRepo
        from(URI_DIRECT_INDEX_PAGE).routeId("indexPage")
                .to("https://hub.docker.com")
                .convertBodyTo(String.class)
                .unmarshal().json()
                .process(this::extractRepoFromPage);

        // 4) will query all tags for the given image and call startIndest for each unique repo/image:tag
        from(URI_DIRECT_INDEX_REPO).routeId("indexRepo")
                .delay(1000)
                .process(this::constructTargetURL)
                .to("https://hub.docker.com")
                .convertBodyTo(String.class)
                .unmarshal().json()
                .process(this::extractIndexRequestFromRepo);

        // 4) Index a single Image (triggered through the UI or through the batch job)
        from(URI_DIRECT_START_INGEST).routeId("ingest")
                .convertBodyTo(String.class)
                .unmarshal().json()
                .process(this::validateIngestionRequest)
                .marshal().json()
                .throttle(3).timePeriodMillis(10000)
                .to("kafka:ingestions?brokers=" + kafkaBrokers)
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(202))
                .setHeader("Access-Control-Allow-Origin", constant("*"))
                .setBody().simple("${null}");

        // 1) Get all Repositories from MCR

        from("direct:crawlMcrRepo").routeId("mcrCrawlRepo")
                .to("https://mcr.microsoft.com/v2/?httpMethod=GET")
                .unmarshal().json()
                .process(x -> {
                    Map<String, Object> response = x.getMessage().getBody(Map.class);
                    List<String> tagsFull = (List<String>) response.get("tags");

                    List<String> tail = tagsFull.subList(Math.max(tagsFull.size() - 100, 0), tagsFull.size());
                    for (String tag : tail) {
                        String imageName = (String) x.getMessage().getHeader("DockerRepository");
                        sendImage("mcr.microsoft.com/" + imageName + ":" + tag);
                    }
                });

        from(URI_DIRECT_START_INDEX_UPDATE_MCR).routeId("mcrCrawlWrapper")
                .wireTap("direct:mcrIndexUpdate")
                .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(200));

        from("direct:mcrIndexUpdate").routeId("mcrCrawl")
                .setBody().simple("${null}")
                .removeHeaders("*")
                .to("https://mcr.microsoft.com/v2/_catalog?httpMethod=GET")
                .unmarshal().json()
                .process(x -> {
                    Map<String, Object> response = x.getMessage().getBody(Map.class);
                    List<String> repositories = (List<String>) response.get("repositories");
                    Collections.reverse(repositories);
                    for (String repo : repositories) {
                        String qry = "https://mcr.microsoft.com/v2/"+repo+"/tags/list";
                        Exchange ex = ExchangeBuilder.anExchange(getContext())
                                .withHeader(Exchange.HTTP_URI, qry)
                                .withHeader("DockerRepository", repo)
                                .build();
                        producerTemplate.send("direct:crawlMcrRepo", ex);
                    }
                });
    }

    private Map<String, String> stripUnknownFields(Map<String, String> image) {
        Map<String, String> img = new HashMap<>();
        img.put(DOCKERAPI_IMAGE_NAME, JobbieStringUtils.strip(image.get(DOCKERAPI_IMAGE_NAME), 255));
        img.put(DOCKERAPI_IMAGE_TAG, JobbieStringUtils.strip(image.get(DOCKERAPI_IMAGE_TAG), 255));
        img.put(DOCKERAPI_REPOSITORY_NAME, JobbieStringUtils.strip(image.get(DOCKERAPI_REPOSITORY_NAME), 255));
        return img;
    }

    private void sendImage(String image) {
        if (image.contains("/") && image.contains(":")) {
            String json = JobbieStringUtils.getJsonFromImage(image);
            Exchange ex = ExchangeBuilder.anExchange(getContext())
                    .withBody(json)
                    .build();
            producerTemplate.send(URI_DIRECT_START_INGEST, ex);
        } else {
            throw new IllegalArgumentException("Unexpected Image Syntax");
        }
    }

    private void constructTargetURL(Exchange x) {
        String logEntry = String.format("Indexing ... %s", x.getMessage().getBody(String.class));
        logger.info(logEntry);
        String imageName = x.getMessage().getBody(String.class);
        if (!imageName.contains("/")) {
            imageName = "library/" + imageName;
        }
        lastImage = imageName;
        x.getMessage().setHeader("repoName", imageName);
        String qry = "https://hub.docker.com/v2/repositories/" + imageName + "/tags/?page_size=100&page=1&ordering=last_updated";
        x.getMessage().setHeader(Exchange.HTTP_URI, qry);
    }

    private void validateIngestionRequest(Exchange x) {
        Map<String, String> image = x.getMessage().getBody(Map.class);

        if (image != null) {
            x.getMessage().setBody(stripUnknownFields(image));
        } else {
            throw new IllegalArgumentException("Unexpected Input");
        }
    }

    private void extractRepoFromPage(Exchange x) {
        Map<String, Object> result = x.getMessage().getBody(LinkedHashMap.class);
        List<Object> summaries = (List) result.get("summaries");
        for (Object imageObject : summaries) {
            Map<String, Object> imageEntry = (Map) imageObject;
            String repoName = (String) imageEntry.get("slug");
            if (!repoName.contains("microsoft-")) {
                Exchange ex = ExchangeBuilder.anExchange(getContext())
                        .withBody(repoName)
                        .build();
                producerTemplate.send(URI_DIRECT_INDEX_REPO, ex);
            }
        }
    }

    private void updatePopularImages(Exchange x) throws InterruptedException {
        if (!isIndexing) {
            isIndexing = true;
            for (int i = 1; i < 100; ++i) {
                page = i;
                String qryString = "";
                if (x.getMessage().getHeader("qryString", String.class) != null)
                    qryString = x.getMessage().getHeader("qryString", String.class);

                crawlPage(i, qryString, "linux");
                crawlPage(i, qryString, "windows");
                Thread.sleep(1000);
            }
        }
    }

    private void crawlPage(int page, String qryString, String operatingSystem) {
        String qry = "https://hub.docker.com/api/content/v1/products/search?operating_system="+operatingSystem+"&page=" + page + "&page_size=&q=" + qryString + "&type=image";
        Exchange ex = ExchangeBuilder.anExchange(getContext())
                .withHeader(Exchange.HTTP_URI, qry)
                .build();
        producerTemplate.send(URI_DIRECT_INDEX_PAGE, ex);
    }

    private void extractIndexRequestFromRepo(Exchange x) {
        Map<String, Object> result = x.getMessage().getBody(LinkedHashMap.class);
        List<Object> entries = (List) result.get("results");
        String repoName = x.getMessage().getHeader("repoName", String.class);
        for (Object entryObj : entries) {
            Map<String, Object> entry = (Map) entryObj;
            String imgName = (String) entry.get("name");
            sendImage(repoName + ":" + imgName);
        }
    }

    private void constructIndexReport(Exchange x) {
        if (isIndexing) {
            x.getMessage().setBody("{lastImage: \"" + lastImage + "\", page:\"" + page + "\"}");
        }
    }
}
