package com.redhat.repository.validator.impl.remoterepository;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;

import static com.redhat.repository.validator.internal.Utils.calculateChecksum;

public class ChecksumProviderNexus implements ChecksumProvider {

    @Override
    public String getRemoteArtifactChecksum(URI remoteArtifact, HttpResponse httpResponse, boolean cache,
        Map<String, String> checksumRemoteCache) {
        if (checksumRemoteCache != null && checksumRemoteCache.containsKey(remoteArtifact.toString())) {
            return checksumRemoteCache.get(remoteArtifact.toString());
        }
        Header etagHeader = httpResponse.getFirstHeader("ETag");
        if (etagHeader != null) {
            String etagValue = etagHeader.getValue();
            if (etagValue != null && etagValue.startsWith("\"{SHA1{") && etagValue.endsWith("}}\"")) {
                String sha1 = etagValue.substring(7, etagValue.length() - 3);
                if (cache) {
                    synchronized (checksumRemoteCache) {
                        try {
                            String filePath =
                                "/home/mjobanek/mrrc/remote-repos/repositoryEntities-cache-" + remoteArtifact.getHost()
                                    + ".txt";
                            FileUtils.touch(new File(filePath));
                            Files.write(Paths.get(filePath),
                                        remoteArtifact.toString()
                                            .concat(" ")
                                            .concat(sha1)
                                            .concat("\n")
                                            .getBytes(),
                                        StandardOpenOption.APPEND);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        if (checksumRemoteCache != null) {
                            checksumRemoteCache.put(remoteArtifact.toString(), sha1);
                        }
                    }
                }
                return sha1;
            }
        }
        throw new ChecksumProviderException(
            "Remote repository returned unknown headers, it is not possible to parse artifact hash for "
                + remoteArtifact);
    }

    @Override
    public String getLocalArtifactChecksum(URI localArtifact) {
        return calculateChecksum(new File(localArtifact), "sha1");
    }

    @Override public String getLocalRemoteHash(CloseableHttpClient httpClient, URI localArtifact,
        String remoteRepositoryUrl, Map<String, String> checksumLocalCache)
        throws IOException, RemoteRepositoryCompareException, URISyntaxException {

        if (checksumLocalCache != null && checksumLocalCache.containsKey(localArtifact.toString())) {

            return checksumLocalCache.get(localArtifact.toString());
        }

        //        localArtifact = new URI(localArtifact.getPath().replace("http:/", "http://"));
        HttpUriRequest httpRequestLocal = RequestBuilder.head().setUri(localArtifact).build();
        HttpResponse httpResponseLocal = httpClient.execute(httpRequestLocal);
        int httpStatusCodeLocal = httpResponseLocal.getStatusLine().getStatusCode();

        if (httpStatusCodeLocal == HttpStatus.SC_OK) {

            String remoteLocalArtifactChecksum = getRemoteArtifactChecksum(localArtifact, httpResponseLocal, false,
                                                                           null);
            synchronized (checksumLocalCache) {
                FileUtils.touch(new File("/home/mjobanek/mrrc/repositoryEntities-cache.txt"));
                Files.write(Paths.get("/home/mjobanek/mrrc/repositoryEntities-cache.txt"),
                            localArtifact.toString()
                                .concat(" ")
                                .concat(remoteLocalArtifactChecksum)
                                .concat("\n")
                                .getBytes(),
                            StandardOpenOption.APPEND);
                checksumLocalCache.put(localArtifact.toString(), remoteLocalArtifactChecksum);
            }
            return remoteLocalArtifactChecksum;

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

Date : Mon, 18 Aug 2014 11:46:34 GMT
Server : Nexus/2.7.2-03
Accept-Ranges : bytes
ETag : "{SHA1{e6f1e89880e645c66ef9c30d60a68f7e26f3152d}}"
Content-Type : application/java-archive
Last-Modified : Thu, 17 Jul 2014 04:59:39 GMT
Content-Length : 5254140
X-Content-Type-Options : nosniff
Set-Cookie : rememberMe=deleteMe; Path=/nexus; Max-Age=0; Expires=Sun, 17-Aug-2014 11:46:34 GMT
Set-Cookie : LBROUTE=proxy02; path=/
Keep-Alive : timeout=10, max=128
Connection : Keep-Alive

*/