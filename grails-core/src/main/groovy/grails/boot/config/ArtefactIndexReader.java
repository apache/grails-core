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
package grails.boot.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import grails.io.IOUtils;

/**
 * Reads the optional application artefact index.
 *
 * <p>The index is UTF-8 text at {@value #RESOURCE_NAME}, with one fully qualified
 * class name per line; blank lines are ignored. Any entry that fails to resolve to a
 * loadable class, or any failure while locating or reading the index itself, rejects
 * the complete index so callers fall back to their normal classpath scan. Entries that
 * do not match the given package names are silently excluded from the result without
 * invalidating the index.</p>
 *
 * <p>Unlike {@link grails.boot.config.tools.ClassPathScanner}, which this reader
 * substitutes when a usable index is present, entries are trusted without an artefact
 * annotation check, and the given package names are honored exactly as given rather
 * than also excluding {@code ClassPathScanner}'s default ignored root packages (such as
 * {@code com}, {@code org} and {@code net}). An index producer must reproduce
 * {@code ClassPathScanner}'s selection semantics to keep behavior equivalent to
 * classpath scanning.</p>
 *
 * <p>Reading the index can be forced off, in favor of classpath scanning, by setting
 * the {@value #DISABLED_PROPERTY} system property to {@code true}.</p>
 */
final class ArtefactIndexReader {

    static final String RESOURCE_NAME = "META-INF/grails/artefacts.idx";

    static final String DISABLED_PROPERTY = "grails.artefactIndex.disabled";

    private static final Logger log = LoggerFactory.getLogger(ArtefactIndexReader.class);

    private ArtefactIndexReader() {
    }

    static Collection<Class> read(Class<?> applicationClass, Collection<String> packageNames) {
        if (Boolean.getBoolean(DISABLED_PROPERTY)) {
            log.debug("Artefact index reading is disabled via the '{}' system property; using classpath scanning", DISABLED_PROPERTY);
            return null;
        }
        try {
            URL resource = new URL(IOUtils.findRootResource(applicationClass), RESOURCE_NAME);
            Set<Class> classes = new LinkedHashSet<>();
            if (readResource(resource, applicationClass.getClassLoader(), packageNames, classes)) {
                log.debug("Using artefact index at {} ({} classes)", resource, classes.size());
                return classes;
            }
            return null;
        } catch (IOException | RuntimeException e) {
            log.debug("Artefact index for {} could not be read; falling back to classpath scanning", applicationClass.getName(), e);
            return null;
        }
    }

    private static boolean readResource(URL resource, ClassLoader classLoader, Collection<String> packageNames, Set<Class> classes) {
        try (InputStream inputStream = resource.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String className;
            while ((className = reader.readLine()) != null) {
                if (className.isEmpty()) {
                    continue;
                }
                if (isInPackage(className, packageNames)) {
                    classes.add(classLoader.loadClass(className));
                }
            }
            return true;
        } catch (IOException | ClassNotFoundException | LinkageError e) {
            log.debug("Artefact index at {} is invalid; falling back to classpath scanning", resource, e);
            return false;
        }
    }

    private static boolean isInPackage(String className, Collection<String> packageNames) {
        for (String packageName : packageNames) {
            if (packageName != null && (packageName.isEmpty() ? !className.contains(".") : className.startsWith(packageName + "."))) {
                return true;
            }
        }
        return false;
    }
}
