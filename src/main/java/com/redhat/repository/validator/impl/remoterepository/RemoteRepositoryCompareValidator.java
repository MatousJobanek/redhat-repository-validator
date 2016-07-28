package com.redhat.repository.validator.impl.remoterepository;

import java.io.File;
import java.net.URI;
import java.util.List;

import com.redhat.repository.validator.ValidatorContext;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;

import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

public class RemoteRepositoryCompareValidator extends RemoteRepositoryAbstractValidator {

    private final ChecksumProvider checksumProvider;

    public RemoteRepositoryCompareValidator(String remoteRepositoryUrl, ChecksumProvider checksumProvider) {
        this(remoteRepositoryUrl, checksumProvider, FileFilterUtils.trueFileFilter(), 20);
    }

    public RemoteRepositoryCompareValidator(String remoteRepositoryUrl, ChecksumProvider checksumProvider,
        IOFileFilter fileFilter, int maxConnTotal) {
        super(remoteRepositoryUrl, fileFilter, maxConnTotal);
        this.checksumProvider = checksumProvider;
    }

    @Override
    protected void validateArtifact(CloseableHttpClient httpClient, URI localArtifact, List<String> listOfRemoteRepos,
        ValidatorContext ctx)
        throws Exception {

        String localArtifactHash = null;
        if (localArtifact.toString().startsWith("http")) {
            localArtifactHash = checksumProvider.getLocalRemoteHash(httpClient, localArtifact,
                                                                    "https://maven.repository.redhat.com/ga/", ctx.getChecksumLocalCache());
        } else {
            localArtifactHash = checksumProvider.getLocalArtifactChecksum(localArtifact);
        }

        for (String remoteRepo : listOfRemoteRepos) {

            try {
                URI remoteArtifactUri = new URI(remoteRepo + localArtifact.toString()
                    .substring("https://maven.repository.redhat.com/ga/".length()));
            HttpUriRequest httpRequest = RequestBuilder.head().setUri(remoteArtifactUri).build();
            HttpResponse httpResponse = httpClient.execute(httpRequest);
            int httpStatusCode = httpResponse.getStatusLine().getStatusCode();
            if (httpStatusCode == HttpStatus.SC_OK) {
                String remoteArtifactHash = checksumProvider.getRemoteArtifactChecksum(remoteArtifactUri, httpResponse, true,
                                                                                       ctx.getChecksumRemoteCache());



                if (!equalsIgnoreCase(remoteArtifactHash, localArtifactHash)) {

                    RemoteRepositoryCollisionException exc =
                        new RemoteRepositoryCollisionException(
                            "Remote repository [" + remoteRepo
                                + "] contains different binary data for artifact "
                                + remoteArtifactUri);
                    ctx.addError(RemoteRepositoryCompareValidator.this, new File(localArtifact.toString()), exc);
                }

            } else if (httpStatusCode == HttpStatus.SC_NOT_FOUND) {
                RemoteRepositoryCompareException exc =
                    new RemoteRepositoryCompareException(
                        "Remote repository [" + remoteRepo + "] doesn't contain artifact "
                            + remoteArtifactUri);
                ctx.addError(RemoteRepositoryCompareValidator.this, new File(localArtifact.toString()), exc);

            } else {
                RemoteRepositoryCompareException exc =
                    new RemoteRepositoryCompareException(
                        "Remote repository [" + remoteRepo + "] returned " + httpResponse.getStatusLine()
                            .toString() + " for artifact " + remoteArtifactUri);
                ctx.addError(RemoteRepositoryCompareValidator.this, new File(localArtifact.toString()), exc);

            }
        } catch (Exception e) {
            e.printStackTrace();
                RemoteRepositoryCompareException exc =
                    new RemoteRepositoryCompareException(
                        "Remote repository [" + remoteRepo + "] request failed for artifact "
                            + new URI(remoteRepo + localArtifact.toString()
                            .substring("https://maven.repository.redhat.com/ga/".length())), e);
                ctx.addError(RemoteRepositoryCompareValidator.this, new File(localArtifact.toString()), exc);


            }}
    }

}