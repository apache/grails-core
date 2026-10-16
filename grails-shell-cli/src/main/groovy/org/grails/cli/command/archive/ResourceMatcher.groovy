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
package org.grails.cli.command.archive

import groovy.transform.CompileStatic
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.util.AntPathMatcher
import org.springframework.util.StringUtils

/**
 * Used to match resources for inclusion in a CLI application's jar file.
 *
 * @author Andy Wilkinson
 */
@CompileStatic
class ResourceMatcher {

    private static final String[] DEFAULT_INCLUDES = [
        'public/**',
        'resources/**',
        'static/**',
        'templates/**',
        'META-INF/**',
        '*'
    ] as String[]

    private static final String[] DEFAULT_EXCLUDES = [
        '.*',
        'repository/**',
        'build/**',
        'target/**',
        '**/*.jar',
        '**/*.groovy'
    ] as String[]

    private final AntPathMatcher pathMatcher = new AntPathMatcher()

    private final List<String> includes

    private final List<String> excludes

    ResourceMatcher(List<String> includes, List<String> excludes) {
        this.includes = getOptions(includes, DEFAULT_INCLUDES)
        this.excludes = getOptions(excludes, DEFAULT_EXCLUDES)
    }

    List<MatchedResource> find(List<File> roots) throws IOException {
        List<MatchedResource> matchedResources = new ArrayList<>()
        for (File root in roots) {
            if (root.isFile()) {
                matchedResources.add(new MatchedResource(root))
            }
            else {
                matchedResources.addAll(findInDirectory(root))
            }
        }
        return matchedResources
    }

    private List<MatchedResource> findInDirectory(File directory) throws IOException {
        List<MatchedResource> matchedResources = new ArrayList<>()

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(
                new DirectoryResourceLoader(directory))

        for (String include in this.includes) {
            for (Resource candidate in resolver.getResources(include)) {
                File file = candidate.getFile()
                if (file.isFile()) {
                    MatchedResource matchedResource = new MatchedResource(directory, file)
                    if (!isExcluded(matchedResource)) {
                        matchedResources.add(matchedResource)
                    }
                }
            }
        }

        return matchedResources
    }

    private boolean isExcluded(MatchedResource matchedResource) {
        for (String exclude in this.excludes) {
            if (this.pathMatcher.match(exclude, matchedResource.getName())) {
                return true
            }
        }
        return false
    }

    private List<String> getOptions(List<String> values, String[] defaults) {
        Set<String> result = new LinkedHashSet<>()
        Set<String> minus = new LinkedHashSet<>()
        boolean deltasFound = false
        for (String value in values) {
            if (value.startsWith('+')) {
                deltasFound = true
                value = value.substring(1)
                result.add(value)
            }
            else if (value.startsWith('-')) {
                deltasFound = true
                value = value.substring(1)
                minus.add(value)
            }
            else if (!value.trim().isEmpty()) {
                result.add(value)
            }
        }
        for (String value in defaults) {
            if (!minus.contains(value) || !deltasFound) {
                result.add(value)
            }
        }
        return new ArrayList<>(result)
    }

    /**
     * {@link org.springframework.core.io.ResourceLoader} to get load resource from a directory.
     */
    private static class DirectoryResourceLoader extends DefaultResourceLoader {

        private final File rootDirectory

        DirectoryResourceLoader(File root) throws MalformedURLException {
            super(new DirectoryClassLoader(root))
            this.rootDirectory = root
        }

        @Override
        protected Resource getResourceByPath(String path) {
            return new FileSystemResource(new File(this.rootDirectory, path))
        }

    }

    /**
     * {@link ClassLoader} backed by a directory.
     */
    private static class DirectoryClassLoader extends URLClassLoader {

        DirectoryClassLoader(File rootDirectory) throws MalformedURLException {
            super([rootDirectory.toURI().toURL()] as URL[])
        }

        @Override
        Enumeration<URL> getResources(String name) throws IOException {
            return findResources(name)
        }

        @Override
        URL getResource(String name) {
            return findResource(name)
        }

    }

    /**
     * A single matched resource.
     */
    public static final class MatchedResource {

        private final File file

        private final String name

        private final boolean root

        private MatchedResource(File file) {
            this.name = file.getName()
            this.file = file
            this.root = this.name.endsWith('.jar')
        }

        private MatchedResource(File rootDirectory, File file) {
            String filePath = file.getAbsolutePath()
            String rootDirectoryPath = rootDirectory.getAbsolutePath()
            this.name = StringUtils.cleanPath(filePath.substring(rootDirectoryPath.length() + 1))
            this.file = file
            this.root = false
        }

        private MatchedResource(File resourceFile, String path, boolean root) {
            this.file = resourceFile
            this.name = path
            this.root = root
        }

        String getName() {
            return this.name
        }

        File getFile() {
            return this.file
        }

        boolean isRoot() {
            return this.root
        }

        @Override
        String toString() {
            return this.file.getAbsolutePath()
        }

    }

}
