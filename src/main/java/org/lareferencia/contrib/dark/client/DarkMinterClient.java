package org.lareferencia.contrib.dark.client;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lareferencia.contrib.dark.domain.DarkRecordContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Client for communicating with the DARK Minter service (HyperDrive).
 * Handles PID registration and URL updates.
 */
@Component
public class DarkMinterClient {

    private static final Logger logger = LogManager.getLogger(DarkMinterClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.of(90, ChronoUnit.SECONDS);

    @Setter
    @Getter
    @Value("${dark.minter.url:http://minter.dark-pid.net/}")
    private String minterUrl;

    private final HttpClient httpClient;

    public DarkMinterClient() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Registers new PIDs for the given records.
     *
     * @param records    List of record contexts to register
     * @param privateKey The DNAM private key for authentication
     * @return Response containing the ingested PIDs
     */
    public MinterResponse registerPids(List<DarkRecordContext> records, String privateKey) {
        List<String> oaiIds = records.stream()
                .map(DarkRecordContext::oaiId)
                .collect(Collectors.toList());
        List<String> urls = records.stream()
                .map(DarkRecordContext::url)
                .collect(Collectors.toList());

        MinterRequest request = MinterRequest.forRegistration(oaiIds, urls, privateKey);
        return sendRequest(request);
    }

    /**
     * Updates URLs for existing PIDs.
     *
     * @param records    List of record contexts with updated URLs
     * @param privateKey The DNAM private key for authentication
     * @return Response containing the update results
     */
    public MinterResponse updateUrls(List<DarkRecordContext> records, String privateKey) {
        List<String> darkIds = records.stream()
                .map(DarkRecordContext::getDarkId)
                .collect(Collectors.toList());
        List<String> urls = records.stream()
                .map(DarkRecordContext::url)
                .collect(Collectors.toList());

        MinterRequest request = MinterRequest.forUpdate(darkIds, urls, privateKey);
        return sendRequest(request);
    }

    @SneakyThrows
    private MinterResponse sendRequest(MinterRequest minterRequest) {
        HttpRequest request = HttpRequest.newBuilder(new URI(minterUrl + minterRequest.getEndpoint()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(minterRequest.toJson()))
                .timeout(REQUEST_TIMEOUT)
                .build();

        logger.debug("Sending request to HyperDrive [{}] for operation [{}]", 
                request.uri(), minterRequest.getOperation());
        logger.trace("Request body: {}", minterRequest.toJson());

        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        String responseBody = httpResponse.body();
        logger.debug("Received status [{}] from HyperDrive: {}",
                httpResponse.statusCode(), responseBody);

        return MinterResponse.fromJson(responseBody);
    }
}
