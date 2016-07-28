package com.redhat.repository.validator;

import java.io.IOException;
import java.net.URI;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

/**
 * @author <a href="mailto:mjobanek@redhat.com">Matous Jobanek</a>
 */
public class HttpClient {

    private static CloseableHttpClient httpClient = null;
    private static long lastRequest = System.currentTimeMillis();
    private static long minDistance = 10;

    private HttpClient() {
    }

    public static CloseableHttpClient instance() {
        if (httpClient == null) {
            httpClient = HttpClients.custom().setMaxConnTotal(20).build();
        }
        return httpClient;
    }

    public static CloseableHttpResponse execute(HttpUriRequest httpRequest) throws IOException {
        long distance = System.currentTimeMillis() - lastRequest;
        if (distance < minDistance) {
//            System.err.println("sleeping " + (minDistance - distance));
            try {
                Thread.sleep(minDistance - distance);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        CloseableHttpResponse response = HttpClient.instance().execute(httpRequest);
        lastRequest = System.currentTimeMillis();
        return response;
    }

    public static int getStatusCode(HttpUriRequest httpRequest) throws IOException {
        return HttpClient.execute(httpRequest).getStatusLine().getStatusCode();
    }

    public static int getStatusCode(URI uri) throws IOException {
        return HttpClient.getStatusCode(RequestBuilder.head().setUri(uri).build());
    }

    public static String getEntityString(String uri) throws IOException {
        CloseableHttpResponse response = HttpClient.execute(new HttpGet(uri));
        return EntityUtils.toString(response.getEntity(), "UTF-8");
    }
}
