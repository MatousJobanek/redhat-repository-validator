package org.jboss.wolf.validator.impl;

import static org.apache.commons.lang3.ObjectUtils.notEqual;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static org.jboss.wolf.validator.internal.Utils.findCause;
import static org.jboss.wolf.validator.internal.Utils.findPathToDependency;
import static org.jboss.wolf.validator.internal.Utils.sortArtifacts;
import static org.jboss.wolf.validator.internal.Utils.sortDependencyNodes;

import java.io.PrintStream;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.DefaultDependencyNode;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.jboss.wolf.validator.Reporter;
import org.jboss.wolf.validator.ValidatorContext;
import org.springframework.core.annotation.Order;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;

@Named
@Order(100)
public class DependencyNotFoundReporter implements Reporter {

    @Inject
    @Named("dependencyNotFoundReporterStream")
    private PrintStream out;

    @Override
    public final void report(ValidatorContext ctx) {
        ListMultimap<Artifact, DependencyNode> artifactNotFoundMap = ArrayListMultimap.create();
        collectMissingDependencies(ctx, artifactNotFoundMap);
        if (!artifactNotFoundMap.isEmpty()) {
            printHeader(artifactNotFoundMap);
            printMissingDependencies(artifactNotFoundMap);
            out.println();
            out.flush();
        }
    }

    protected void collectMissingDependencies(ValidatorContext ctx, ListMultimap<Artifact, DependencyNode> artifactNotFoundMap) {
        List<DependencyNotFoundException> dependencyNotFoundExceptions = ctx.getExceptions(DependencyNotFoundException.class);
        for (DependencyNotFoundException e : dependencyNotFoundExceptions) {
            DependencyNode dependencyNode = e.getDependencyNode();
            Artifact missingArtifact = e.getMissingArtifact();
            artifactNotFoundMap.put(missingArtifact, dependencyNode);
            ctx.addProcessedException(e);
        }
    }

    protected void printHeader(ListMultimap<Artifact, DependencyNode> artifactNotFoundMap) {
        out.println("--- DEPENDENCY NOT FOUND REPORT ---");
        out.println("Found " + artifactNotFoundMap.keySet().size() + " missing dependencies.");
    }

    protected void printMissingDependencies(ListMultimap<Artifact, DependencyNode> artifactNotFoundMap) {
        for (Artifact artifact : sortArtifacts(artifactNotFoundMap.keySet())) {
            out.println("miss: " + artifact);
            List<DependencyNode> roots = sortDependencyNodes(artifactNotFoundMap.get(artifact));
            for (DependencyNode root : roots) {
                // the dependency node might be null if the original exception did not contain any such additional info
                if (root != null) {
                    out.println("    from: " + root.getArtifact());
                    String path = findPathToDependency(artifact, root);
                    String simplePath = root.getArtifact() + " > " + artifact;
                    if (isNotEmpty(path) && notEqual(path, simplePath)) {
                        out.print("        path: ");
                        out.print(path);
                        out.println();
                    }
                }
            }
        }
    }

}