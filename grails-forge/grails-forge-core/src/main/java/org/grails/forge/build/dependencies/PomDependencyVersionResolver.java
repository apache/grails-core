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
package org.grails.forge.build.dependencies;

import jakarta.annotation.Nonnull;
import org.grails.forge.util.NameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PomDependencyVersionResolver implements CoordinateResolver {

    private static final Logger LOG = LoggerFactory.getLogger(PomDependencyVersionResolver.class);

    private static final String NODE_NAME_TEXT = "#text";
    private final Map<String, Coordinate> coordinates;

    public PomDependencyVersionResolver() {
        this(loadPomResources());
    }

    PomDependencyVersionResolver(Iterable<URL> pomUrls) {
        Map<String, Coordinate> coordinates = new HashMap<>();
        for (URL url : pomUrls) {
            try (InputStream inputStream = url.openStream()) {
                Document doc = documentFor(inputStream);
                doc.getDocumentElement().normalize();
                NodeList nList = doc.getElementsByTagName("dependency");
                for (int i = 0; i < nList.getLength(); i++) {
                    Node node = nList.item(i);
                    NodeList childNodes = node.getChildNodes();
                    String groupId = null;
                    String artifactId = null;
                    String version = null;
                    boolean pom = false;
                    for (int x = 0; x < childNodes.getLength(); x++) {
                        Node child = childNodes.item(x);
                        if (child.getNodeName().equals("version")) {
                            if (valueOfNode(child).isPresent()) {
                                version = valueOfNode(child).get();
                            }
                        }
                        if (child.getNodeName().equals("groupId")) {
                            if (valueOfNode(child).isPresent()) {
                                groupId = valueOfNode(child).get();
                            }
                        }
                        if (child.getNodeName().equals("artifactId")) {
                            if (valueOfNode(child).isPresent()) {
                                artifactId = valueOfNode(child).get();
                            }
                        }
                        if (child.getNodeName().equals("type")) {
                            if (valueOfNode(child).isPresent()) {
                                pom = "pom".equalsIgnoreCase(valueOfNode(child).get());
                            }
                        }
                    }

                    if (!NameUtils.isBlank(groupId) && !NameUtils.isBlank(artifactId)) {
                        DependencyCoordinate dependencyCoordinate = Dependency.builder()
                                .groupId(groupId)
                                .artifactId(artifactId)
                                .version(version)
                                .pom(pom)
                                .buildCoordinate();
                        coordinates.put(dependencyCoordinate.getArtifactId(), dependencyCoordinate);
                    }
                }
            } catch (IOException | SAXException | ParserConfigurationException e) {
                LOG.warn("Unable to read dependency versions from " + url, e);
            }
        }
        this.coordinates = coordinates;
    }

    private static List<URL> loadPomResources() {
        ClassLoader classLoader = PomDependencyVersionResolver.class.getClassLoader();
        if (classLoader == null) {
            return Collections.emptyList();
        }
        try {
            Enumeration<URL> resources = classLoader.getResources("pom.xml");
            List<URL> urls = new ArrayList<>();
            while (resources.hasMoreElements()) {
                urls.add(resources.nextElement());
            }
            return urls;
        } catch (IOException e) {
            LOG.warn("Unable to locate pom.xml resources", e);
            return Collections.emptyList();
        }
    }

    @Override
    @Nonnull
    public Optional<Coordinate> resolve(@Nonnull String artifactId) {
        return Optional.ofNullable(coordinates.get(artifactId));
    }

    private static Document documentFor(@Nonnull InputStream inputStream)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(inputStream);
    }

    @Nonnull
    private Optional<String> valueOfNode(@Nonnull Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeName().equals(NODE_NAME_TEXT)) {
                return Optional.of(child.getNodeValue());
            }
        }
        return Optional.empty();
    }

    @Nonnull
    public Map<String, Coordinate> getCoordinates() {
        return coordinates;
    }
}
