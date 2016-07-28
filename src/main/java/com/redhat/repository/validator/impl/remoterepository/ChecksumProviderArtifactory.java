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

public class ChecksumProviderArtifactory implements ChecksumProvider {

    @Override
    public String getRemoteArtifactChecksum(URI remoteArtifact, HttpResponse httpResponse, boolean cache,
        Map<String, String> checksumRemoteCache) {
        Header xchecksumSh1Header = httpResponse.getFirstHeader("X-Checksum-Sha1");
        if (xchecksumSh1Header != null) {
            return xchecksumSh1Header.getValue();
        }
        throw new ChecksumProviderException("Remote repository returned unknown headers, it is not possible to parse artifact hash for " + remoteArtifact);
    }

    @Override
    public String getLocalArtifactChecksum(URI localArtifact) {
        return calculateChecksum(new File(localArtifact), "sha1");
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

Content-length : 1577553
Content-Type : application/java-archive
Date : Mon, 18 Aug 2014 11:43:52 GMT
ETag : 9362fc3d9d47eedf3e9cda1ebd304f69ca70e7af
Last-Modified : Sun, 31 Aug 2008 11:43:59 GMT
Server : Artifactory/3.3.0.1
X-Artifactory-Filename : wicket-1.3.0-beta2.jar
X-Artifactory-Id : repo.jfrog.org
X-Checksum-Md5 : 8f4e4bc2fbde08cc4b238e5ee114e555
X-Checksum-Sha1 : 9362fc3d9d47eedf3e9cda1ebd304f69ca70e7af
X-Node : nginx1-use-1d
Connection : keep-alive
 
 */