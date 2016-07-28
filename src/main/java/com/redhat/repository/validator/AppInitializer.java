package com.redhat.repository.validator;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Named
public class AppInitializer {

    private static final Logger logger = LoggerFactory.getLogger(AppInitializer.class);

    @Inject
    private LocalRepository localRepository;

    public void initialize(ValidatorContext ctx) {
        localRepositoryShouldExist();
        localRepositoryShouldBeEmpty();
        validatedRepositoryShouldExist(ctx);
        validatedRepositoryShouldNotBeEmpty(ctx);
        remoteRepositoriesShouldNotBeEmpty(ctx);
        logInformation(ctx);
    }

    private void localRepositoryShouldExist() {
        File dir = localRepository.getBasedir();
        if (dir.exists() && dir.isFile()) {
            throw new RuntimeException("Local repository " + dir + " isn't directory");
        }
        if (!dir.exists()) {
            logger.info("Local repository {} doesn't exist", dir);
            if (dir.mkdirs()) {
                logger.info("Local repository {} created", dir);
            } else {
                logger.error("Failed to create local repository " + dir);
                throw new RuntimeException("Failed to create local repository " + dir);
            }
        }
    }

    private void localRepositoryShouldBeEmpty() {
        File dir = localRepository.getBasedir();
        if (dir.list().length != 0) {
            logger.warn("Local repository should be empty");
        }
    }

    private void validatedRepositoryShouldExist(ValidatorContext ctx) {
        File dir = ctx.getValidatedRepository();
        if (!dir.exists()) {
            try {
                URI validatedRepoUri = new URI(ctx.getValidatedRepo());

                if (HttpClient.getStatusCode(validatedRepoUri) == HttpStatus.SC_OK) {
                    OnlineRepositoryDiscovery onlineRepositoryDiscovery = new OnlineRepositoryDiscovery(ctx);
                    onlineRepositoryDiscovery.discover();
                }
            } catch (URISyntaxException e) {
                e.printStackTrace();
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            logger.error("Validated repository " + dir + " doesn't exist");
            throw new RuntimeException("Validated repository " + dir + " doesn't exist");
        }
        if (!dir.isDirectory() && dir.isFile()) {
            File cacheFile = new File("/home/mjobanek/mrrc/repositoryEntities-cache.txt");
            if (cacheFile.exists()){

                putIntoCache(cacheFile, ctx.getChecksumLocalCache());
                System.err.println("============================================== " + ctx.getChecksumLocalCache().size());
            }

            File cacheDir = new File("/home/mjobanek/mrrc/remote-repos");
            File[] cacheFiles = cacheDir.listFiles();
            if (cacheDir.exists() && cacheDir.isDirectory() && cacheFiles.length != 0){

                for (File file : cacheFiles) {
                    putIntoCache(file, ctx.getChecksumRemoteCache());
                }
            }
//            logger.error("Validated repository " + dir + " isn't directory");
//            throw new RuntimeException("Validated repository " + dir + " isn't directory");
        }
        ctx.setIsDirectory(dir.isDirectory());
    }

    private void putIntoCache(File cacheFile, Map<String,String> cacheMap){
        try (BufferedReader br = new BufferedReader(new FileReader(cacheFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] artifactAndChecksum = line.split(" ");
                cacheMap.put(artifactAndChecksum[0], artifactAndChecksum[1]);
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void validatedRepositoryShouldNotBeEmpty(ValidatorContext ctx) {
        File dir = ctx.getValidatedRepository();
        if (dir.isDirectory() && dir.list().length == 0) {
            logger.error("Validated repository " + dir + " is empty");
            throw new RuntimeException("Validated repository " + dir + " is empty");
        }
    }

    private void remoteRepositoriesShouldNotBeEmpty(ValidatorContext ctx) {
        List<RemoteRepository> remoteRepositories = ctx.getRemoteRepositories();
        if (remoteRepositories.isEmpty()) {
            logger.warn("Remote repositories should not be empty");
        }
    }

    private void logInformation(ValidatorContext ctx) {
        StringBuilder log = new StringBuilder();
        log.append("Used configuration \n");
        log.append("    local repository       : ").append(localRepository.getBasedir()).append("\n");
        log.append("    validated repository   : ").append(ctx.getValidatedRepository()).append("\n");
        log.append("    validated distribution : ").append(ctx.getValidatedDistribution()).append("\n");
        log.append("    remote repositories    : \n");
        for (RemoteRepository remoteRepository : ctx.getRemoteRepositories()) {
            log.append("        ").append(remoteRepository.getUrl()).append("\n");
        }
        logger.info(log.toString());
    }

}