package com.redhat.repository.validator.impl.remoterepository;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.redhat.repository.validator.Validator;
import com.redhat.repository.validator.ValidatorContext;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.SuffixFileFilter;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.redhat.repository.validator.internal.Utils.relativize;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.filefilter.FileFilterUtils.and;
import static org.apache.commons.io.filefilter.FileFilterUtils.trueFileFilter;

public abstract class RemoteRepositoryAbstractValidator implements Validator {

    private static final String[] ARTIFACT_FILE_EXTENSIONS =
        { "pom", "jar", "war", "ear", "par", "rar", "zip", "aar", "apklib" };

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final int maxConnTotal;
    protected final String remoteRepositoryUrl;
    protected final IOFileFilter fileFilter;

    public RemoteRepositoryAbstractValidator(String remoteRepositoryUrl, IOFileFilter fileFilter, int maxConnTotal) {
        super();
        this.remoteRepositoryUrl = remoteRepositoryUrl;
        this.fileFilter = fileFilter;
        this.maxConnTotal = maxConnTotal;
    }

    @Override
    public final void validate(final ValidatorContext ctx) {
        final ExecutorService executorService = Executors.newFixedThreadPool(1000);
        final CloseableHttpClient httpClient = HttpClients.custom().setMaxConnTotal(75000).build();

        try {
            final List<String> listOfRemoteRepos = getListOfRemoteRepos();
            listOfRemoteRepos.add(remoteRepositoryUrl);
            List<String> listOfRemoteReposToBeRemoved = new ArrayList<>();

            for (String remoteRepo : listOfRemoteRepos) {
                HttpUriRequest repoReq = RequestBuilder.head().setUri(remoteRepo).build();
                HttpResponse repoRes = null;
                try {
                    repoRes = httpClient.execute(repoReq);

                    int repoCode = repoRes.getStatusLine().getStatusCode();
                    if (repoCode == HttpStatus.SC_FORBIDDEN) {
                        listOfRemoteReposToBeRemoved.add(remoteRepo);
                        RemoteRepositoryCollisionException repoExc =
                            new RemoteRepositoryCollisionException(
                                "Remote repository [" + remoteRepo + "] war removed !!!");

                        ctx.addError(this, new File(""), repoExc);
                    }
                } catch (Exception e) {
                    listOfRemoteReposToBeRemoved.add(remoteRepo);
                    RemoteRepositoryCollisionException repoExc =
                        new RemoteRepositoryCollisionException(
                            "Remote repository [" + remoteRepo + "] war removed !!!");

                    ctx.addError(this, new File(""), repoExc);
                }
            }
            listOfRemoteRepos.removeAll(listOfRemoteReposToBeRemoved);

            for (final File file : findFiles(ctx)) {
                try {
                    final URI repoUri = ctx.getValidatedRepository().toURI();
                    String filePath = file.getPath();
                    if (filePath.startsWith("http")) {
                        filePath = filePath.replace("http:/", "http://");
                        filePath = filePath.replace("https:/", "https://");
                    }
                    final String localArtifactPath = filePath;

                    String remoteArtifactPath;
                    if (!localArtifactPath.startsWith("http")) {
                        remoteArtifactPath =
                            remoteRepositoryUrl + repoUri.relativize(new URI(localArtifactPath)).toString();
                    } else {
                        remoteArtifactPath = remoteRepositoryUrl + localArtifactPath
                            .substring("https://maven.repository.redhat.com/ga/".length());
                    }

                    final URI remoteArtifact = new URI(remoteArtifactPath);

                    executorService.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                logger.trace("validating {}",
                                             localArtifactPath.startsWith("http") ? localArtifactPath : relativize(ctx,
                                                                                                                   file));
                                validateArtifact(httpClient, new URI(localArtifactPath), listOfRemoteRepos, ctx);
                            } catch (Exception e) {
                                ctx.addError(RemoteRepositoryAbstractValidator.this, file, e);
                                e.printStackTrace();
                            }
                        }
                    });

                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }

            try {
                executorService.shutdown();
                executorService.awaitTermination(1, TimeUnit.HOURS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        } finally {
            try {
                httpClient.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    protected Collection<File> findFiles(ValidatorContext ctx) {
        IOFileFilter artifactsFilter = new SuffixFileFilter(ARTIFACT_FILE_EXTENSIONS);
        Collection<File> files = null;
        if (ctx.isDirectory()) {
            files =
                listFiles(ctx.getValidatedRepository(), and(fileFilter, artifactsFilter), trueFileFilter());
        } else {
            files = listEntities(ctx.getValidatedRepository(), artifactsFilter);
        }
        return files;
    }

    private Collection<File> listEntities(File validatedRepository, IOFileFilter artifactsFilter) {
        ArrayList<File> files = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(validatedRepository))) {
            String line;
            while ((line = br.readLine()) != null) {

                File file = new File(line);
                if (artifactsFilter.accept(file)) {
                    files.add(file);
                }

            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return files;
    }

    protected abstract void validateArtifact(CloseableHttpClient httpClient, URI localArtifact,
        List<String> listOfRemoteRepos,
        ValidatorContext ctx)
        throws Exception;

    @Override
    public String toString() {
        return new ToStringBuilder(this).append(remoteRepositoryUrl).toString();
    }

    public List<String> getListOfRemoteRepos() {
        List<String> listOfRemoteRepos = new CopyOnWriteArrayList<>();
        listOfRemoteRepos.add("http://google-maven-repository.googlecode.com/svn/repository/");
        listOfRemoteRepos.add("http://download.java.net/maven/2/");
        listOfRemoteRepos.add("http://repository.apache.org/snapshots/");
        listOfRemoteRepos.add("http://people.apache.org/repo/m2-incubating-repository/");
        listOfRemoteRepos.add("http://download.java.net/maven/glassfish/");
        listOfRemoteRepos.add("http://download.java.net/maven/1/");
        listOfRemoteRepos.add("https://oss.sonatype.org/content/groups/scala-tools/");
        listOfRemoteRepos.add("http://jclouds.googlecode.com/svn/repo/");
        listOfRemoteRepos.add("http://files.couchbase.com/maven2/");
        listOfRemoteRepos.add("http://download.java.net/maven/2/");
        listOfRemoteRepos.add("http://repository.codehaus.org/");
        listOfRemoteRepos.add("https://nexus.codehaus.org/content/repositories/snapshots/");
        listOfRemoteRepos.add("http://twdata-m2-repository.googlecode.com/svn/");
        listOfRemoteRepos.add("http://snapshots.repository.codehaus.org/");
        listOfRemoteRepos.add("http://repository-zanata.forge.cloudbees.com/release/");
        listOfRemoteRepos.add("http://repository-zanata.forge.cloudbees.com/snapshot/");
        listOfRemoteRepos.add("http://repository.springsource.com/maven/bundles/release/");
        listOfRemoteRepos.add("http://repository.springsource.com/maven/bundles/external/");
        listOfRemoteRepos.add("http://repo.pentaho.org/artifactory/repo/");
        listOfRemoteRepos.add("http://oauth.googlecode.com/svn/code/maven/");
        listOfRemoteRepos.add("http://google-caja.googlecode.com/svn/maven/");
        listOfRemoteRepos.add("http://maven.springframework.org/milestone/");
        listOfRemoteRepos.add("http://repo.jfrog.org/artifactory/plugins-releases-local/");
        listOfRemoteRepos.add("http://download.eclipse.org/jgit/maven/");
        listOfRemoteRepos.add("http://msgpack.org/maven2/");
        listOfRemoteRepos.add("https://oss.sonatype.org/content/repositories/snapshots/");
        listOfRemoteRepos.add("http://clojars.org/repo/");
        listOfRemoteRepos.add("http://files.couchbase.com/maven2/");
        listOfRemoteRepos.add("http://guiceyfruit.googlecode.com/svn/repo/releases/");
        listOfRemoteRepos.add("http://gwt-maven.googlecode.com/svn/trunk/mavenrepo/");
        listOfRemoteRepos.add("http://hl7api.sourceforge.net/m2/");
        listOfRemoteRepos.add("http://japidiff.googlecode.com/svn/m2-repo/");
        listOfRemoteRepos.add("http://m2.cubeia.com/nexus/content/repositories/thirdparty/");
        listOfRemoteRepos.add("http://mandubian-mvn.googlecode.com/svn/trunk/mandubian-mvn/repository/");
        listOfRemoteRepos.add("http://maven.ow2.org/maven2/");
        listOfRemoteRepos.add("http://maven.ow2.org/maven2-snapshot/");
        listOfRemoteRepos.add("http://maven.restlet.org/");
        listOfRemoteRepos.add("http://maven.springframework.org/release/");
        listOfRemoteRepos.add("http://maven.springframework.org/snapshot/");
        listOfRemoteRepos.add("http://mc-repo.googlecode.com/svn/maven2/releases/");
        listOfRemoteRepos.add("https://nexus.codehaus.org/content/repositories/releases/");
        listOfRemoteRepos.add("http://onejar-maven-plugin.googlecode.com/svn/mavenrepo/");
        listOfRemoteRepos.add("http://oss.sonatype.org/content/repositories/ops4j-snapshots/");
        listOfRemoteRepos.add("http://repo.c24io.net/nexus/content/groups/public/");
        listOfRemoteRepos.add("http://repo.marketcetera.org/maven/");
        listOfRemoteRepos.add("http://repo1.maven.org/eclipse/");
        listOfRemoteRepos.add("http://repos.zeroturnaround.com/nexus/content/groups/zt-public/");
        listOfRemoteRepos.add("https://repository.apache.org/content/repositories/releases/");
        listOfRemoteRepos.add("https://repository.apache.org/content/groups/public/");
        listOfRemoteRepos.add("https://repository.apache.org/content/repositories/snapshots/");
        listOfRemoteRepos.add("http://repository.jetbrains.com/all/");
        listOfRemoteRepos.add("http://repository.jetbrains.com/releases/");
        listOfRemoteRepos.add("http://repository.jetbrains.com/repo/");
        listOfRemoteRepos.add("http://repository.jetbrains.com/snapshots/");
        listOfRemoteRepos.add("http://repository.ops4j.org/maven2/");
        listOfRemoteRepos.add("http://repository.sonatype.org/content/repositories/snapshots/");
        listOfRemoteRepos.add("http://repository.springsource.com/maven/bundles/external/");
        listOfRemoteRepos.add("http://repository.springsource.com/maven/bundles/milestone/");
        listOfRemoteRepos.add("http://repository.springsource.com/maven/bundles/release/");
        listOfRemoteRepos.add("http://repository.springsource.com/maven/bundles/snapshot/");
        listOfRemoteRepos.add("http://s3.amazonaws.com/maven.springframework.org/milestone/");
        listOfRemoteRepos.add("http://oss.sonatype.org/content/groups/scala-tools/");
        listOfRemoteRepos.add("http://scriptengines.googlecode.com/svn/m2-repo/");
        listOfRemoteRepos.add("http://springframework.svn.sourceforge.net/svnroot/springframework/repos/repo-ext/");
        listOfRemoteRepos.add("http://svn.apache.org/repos/asf/camel/m2-repo/");
        listOfRemoteRepos.add("http://svn.apache.org/repos/asf/geronimo/server/tags/2.0.1/repository/");
        listOfRemoteRepos.add("http://svn.apache.org/repos/asf/servicemix/m2-repo/");
        listOfRemoteRepos.add("http://uface.googlecode.com/svn/maven/");
        listOfRemoteRepos.add("http://www.aqute.biz/repo/");
        listOfRemoteRepos.add("http://www.asual.com/maven/content/groups/public/");
        listOfRemoteRepos.add("http://zodiac.springsource.com/maven/bundles/release/");
        listOfRemoteRepos.add("http://download.eclipse.org/releases/indigo/");
        listOfRemoteRepos.add("http://oss.sonatype.org/content/groups/public/");
        listOfRemoteRepos.add("http://download.eclipse.org/rt/eclipselink/maven.repo/");
        listOfRemoteRepos.add("http://oss.sonatype.org/content/groups/staging/");
        listOfRemoteRepos.add("http://people.apache.org/repo/m2-snapshot-repository/");
        listOfRemoteRepos.add("http://nexus.codehaus.org/snapshots/");
        listOfRemoteRepos.add("https://repo.eclipse.org/content/groups/releases/");
        listOfRemoteRepos.add("https://repository.jboss.org/nexus/content/groups/ea/");
        listOfRemoteRepos.add("https://build.shibboleth.net/nexus/content/repositories/thirdparty/");
        listOfRemoteRepos.add("https://m2.neo4j.org/content/groups/public/");
        listOfRemoteRepos.add("https://maven.repository.redhat.com/techpreview/all/");
        listOfRemoteRepos.add("https://maven.repository.redhat.com/earlyaccess/all/");
        return listOfRemoteRepos;
    }
}