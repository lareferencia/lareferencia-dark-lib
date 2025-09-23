package org.lareferencia.contrib.dark.util;

import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.contrib.dark.client.MinterHttpRequest;
import org.lareferencia.contrib.dark.client.MinterHttpResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

public class HttpUtils {

    private static Logger logger = LogManager.getLogger(HttpUtils.class);


    @SneakyThrows
    static public <T extends MinterHttpResponse> T sendMessageToHyperDrive(String minterURL, MinterHttpRequest minterRequest, Class<T> reponseClass) {

        HttpRequest request = HttpRequest.newBuilder(new URI(minterURL + minterRequest.operation()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(minterRequest.asJson()))
                .timeout(Duration.of(90, ChronoUnit.SECONDS))
                .build();

        HttpResponse<String> httpResponse = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        String responseBody = httpResponse.body();
        logger.debug("Got the status [{}] and the following message from hyperdrive [{}] for operation [{}]",
                httpResponse.statusCode(),
                responseBody,
                minterRequest.operation());

        return MinterHttpResponse.fromString(responseBody, reponseClass);
    }
}
