package com.redhat.repository.validator.impl.remoterepository;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;

import static com.redhat.repository.validator.internal.Utils.calculateChecksum;

public class ChecksumProviderAkamai implements ChecksumProvider {

    @Override
    public String getRemoteArtifactChecksum(URI remoteArtifact, HttpResponse httpResponse, boolean cache,
        Map<String, String> checksumRemoteCache) {
        Header etagHeader = httpResponse.getFirstHeader("ETag");
        if (etagHeader != null) {
            String etagValue = etagHeader.getValue();
            if (etagValue != null) {
                int index = etagValue.indexOf(":");
                if (index != -1) {
                    return etagValue.substring(1, index);
                }
            }
        }
        throw new ChecksumProviderException("Remote repository returned unknown headers, it is not possible to parse artifact hash for " + remoteArtifact);
    }

    @Override
    public String getLocalArtifactChecksum(URI localArtifact) {
        return calculateChecksum(new File(localArtifact), "md5");
    }


    @Override public String getLocalRemoteHash(CloseableHttpClient httpClient, URI localArtifact,
        String remoteRepositoryUrl, Map<String, String> checksumLocalCache)
        throws IOException, RemoteRepositoryCompareException, URISyntaxException {

        //        localArtifact = new URI(localArtifact.getPath().replace("http:/", "http://"));
        HttpUriRequest httpRequestLocal = RequestBuilder.head().setUri(localArtifact).build();
        HttpResponse httpResponseLocal = httpClient.execute(httpRequestLocal);
        int httpStatusCodeLocal = httpResponseLocal.getStatusLine().getStatusCode();

        if (httpStatusCodeLocal == HttpStatus.SC_OK) {
            return getRemoteArtifactChecksum(localArtifact, httpResponseLocal, true, null);

        } else if (httpStatusCodeLocal == HttpStatus.SC_NOT_FOUND) {
            throw new RemoteRepositoryCompareException(
                "Remote repository [" + remoteRepositoryUrl + "] doesn't contain artifact " + localArtifact);
        } else {
            throw new RemoteRepositoryCompareException(
                "Remote repository [" + remoteRepositoryUrl + "] returned " + httpResponseLocal.getStatusLine()
                    .toString() + " for artifact " + localArtifact);
        }
    }

}

/*

SAMPLE HTTP RESPONSE ...
 
Server : Apache
ETag : "7368fd4e4d4b437d895d6c650084b9b0:1372794403"
Last-Modified : Fri, 26 Apr 2013 15:24:38 GMT
Accept-Ranges : bytes
Content-Length : 4601483
Content-Type : text/plain
Date : Mon, 18 Aug 2014 11:45:26 GMT
Connection : keep-alive
 
*/