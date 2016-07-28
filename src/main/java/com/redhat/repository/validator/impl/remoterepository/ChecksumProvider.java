package com.redhat.repository.validator.impl.remoterepository;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;

public interface ChecksumProvider {

    String getRemoteArtifactChecksum(URI remoteArtifact, HttpResponse httpResponse, boolean cache,
        Map<String, String> checksumRemoteCache);

    String getLocalArtifactChecksum(URI localArtifact);

    String getLocalRemoteHash(CloseableHttpClient httpClient, URI localArtifact, String remoteRepositoryUrl,
        Map<String, String> checksumLocalCache) throws IOException,
        RemoteRepositoryCompareException, URISyntaxException;
}