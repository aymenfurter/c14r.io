package io.c14r;

import org.apache.camel.builder.RouteBuilder;

public abstract class DexterRouteBuilder extends RouteBuilder {
    public static final String IMAGE_NAME = "imageName";
    public static final String IMAGE_TAG = "imageTag";

    private String imageInfoExp() {
        return headerExp(IMAGE_NAME) + ":" + headerExp(IMAGE_TAG);
    }

    private String headerExp(String headerName) {
        return "${headers." + headerName + "}";
    }

    String constructLogStatusText(String status) {
        return status + ":" + imageInfoExp();
    }
}
