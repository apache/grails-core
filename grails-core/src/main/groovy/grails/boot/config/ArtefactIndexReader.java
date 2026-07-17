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

import grails.io.IOUtils;

/**
 * Reads the optional application artefact index.
 *
 * <p>The index is UTF-8 text at {@value #RESOURCE_NAME}, with one fully qualified
 * class name per nonempty line. Any unreadable or unresolvable entry rejects the
 * complete index so callers can use their normal classpath scan.</p>
 */
final class ArtefactIndexReader {

    static final String RESOURCE_NAME = "META-INF/grails/artefacts.idx";

    private ArtefactIndexReader() {
    }

    static Collection<Class> read(Class<?> applicationClass, Collection<String> packageNames) {
        try {
            URL resource = new URL(IOUtils.findRootResource(applicationClass), RESOURCE_NAME);
            Set<Class> classes = new LinkedHashSet<>();
            return readResource(resource, applicationClass.getClassLoader(), packageNames, classes) ? classes : null;
        } catch (IOException ignored) {
            return null;
        }
    }

    private static boolean readResource(URL resource, ClassLoader classLoader, Collection<String> packageNames, Set<Class> classes) {
        try (InputStream inputStream = resource.openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String className;
            while ((className = reader.readLine()) != null) {
                if (className.isEmpty()) {
                    return false;
                }
                if (isInPackage(className, packageNames)) {
                    classes.add(classLoader.loadClass(className));
                }
            }
            return true;
        } catch (IOException | ClassNotFoundException | LinkageError ignored) {
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
