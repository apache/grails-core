/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.cli.compiler.grape;

import java.io.File;
import java.util.Arrays;

import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.ProxySelector;
import org.eclipse.aether.util.repository.JreProxySelector;

import org.springframework.util.StringUtils;

/**
 * A {@link RepositorySystemSessionAutoConfiguration} that, in the absence of any
 * configuration, applies sensible defaults.
 *
 * @author Andy Wilkinson
 * @since 1.0.0
 */
public class DefaultRepositorySystemSessionAutoConfiguration implements RepositorySystemSessionAutoConfiguration {

    /**
     * System property that sets the number of threads Maven Resolver uses to collect the dependency
     * graph and to transfer metadata and artifacts in parallel. When unset (or not a positive
     * integer) it defaults to {@code max(8, availableProcessors * 2)}.
     */
    public static final String RESOLUTION_THREADS_PROPERTY = "grails.dependency.resolution.threads";

    @Override
    public void apply(DefaultRepositorySystemSession session, RepositorySystem repositorySystem) {

        applyParallelResolution(session);

        if (session.getLocalRepositoryManager() == null) {
            LocalRepository localRepository = new LocalRepository(getM2RepoDirectory());
            LocalRepositoryManager localRepositoryManager = repositorySystem.newLocalRepositoryManager(session,
                    localRepository);
            session.setLocalRepositoryManager(localRepositoryManager);
        }

        ProxySelector existing = session.getProxySelector();
        if (!(existing instanceof CompositeProxySelector)) {
            JreProxySelector fallback = new JreProxySelector();
            ProxySelector selector = (existing != null) ? new CompositeProxySelector(Arrays.asList(existing, fallback)) :
                    fallback;
            session.setProxySelector(selector);
        }
    }

    /**
     * Speed up profile/dependency resolution by letting Maven Resolver collect the dependency graph
     * breadth-first (in parallel) and by widening the thread pools used for metadata and artifact
     * transfers. This is what turns the long serial "Resolving dependencies......" wait into a set of
     * concurrent requests. Each property is only applied when it has not already been configured, so
     * an explicit user/system-property override always wins.
     */
    private void applyParallelResolution(DefaultRepositorySystemSession session) {
        int threads = resolveThreadCount();
        // Breadth-first collector resolves sibling dependencies concurrently (vs. the serial
        // depth-first default), which is the dominant win against high-latency remote repositories.
        setConfigPropertyIfAbsent(session, "aether.dependencyCollector.impl", "bf");
        setConfigPropertyIfAbsent(session, "aether.dependencyCollector.bf.threads", String.valueOf(threads));
        // Parallelise the maven-metadata.xml lookups performed while collecting the graph.
        setConfigPropertyIfAbsent(session, "aether.metadataResolver.threads", String.valueOf(threads));
        // Parallelise the actual artifact (jar/pom) downloads within a single resolve request.
        setConfigPropertyIfAbsent(session, "aether.connector.basic.threads", String.valueOf(threads));
    }

    /**
     * The configured thread count from {@link #RESOLUTION_THREADS_PROPERTY}, or the computed default
     * of {@code max(8, availableProcessors * 2)} when the property is unset or not a positive integer.
     */
    private int resolveThreadCount() {
        int defaultThreads = Math.max(8, Runtime.getRuntime().availableProcessors() * 2);
        String configured = System.getProperty(RESOLUTION_THREADS_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            try {
                int value = Integer.parseInt(configured.trim());
                if (value > 0) {
                    return value;
                }
            }
            catch (NumberFormatException ignored) {
                // fall through to the computed default
            }
        }
        return defaultThreads;
    }

    private void setConfigPropertyIfAbsent(DefaultRepositorySystemSession session, String key, String value) {
        if (!session.getConfigProperties().containsKey(key)) {
            session.setConfigProperty(key, value);
        }
    }

    private File getM2RepoDirectory() {
        return new File(getDefaultM2HomeDirectory(), "repository");
    }

    private File getDefaultM2HomeDirectory() {
        String mavenRoot = System.getProperty("maven.home");
        if (StringUtils.hasLength(mavenRoot)) {
            return new File(mavenRoot);
        }
        return new File(System.getProperty("user.home"), ".m2");
    }

}
