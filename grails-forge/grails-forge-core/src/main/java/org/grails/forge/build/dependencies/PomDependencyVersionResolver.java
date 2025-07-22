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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.util.StringUtils;
import jakarta.inject.Singleton;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
public class PomDependencyVersionResolver implements CoordinateResolver {

    private static final String NODE_NAME_TEXT = "#text";
    private final Map<String, Coordinate> coordinates;

    public PomDependencyVersionResolver(ResourceResolver resourceResolver) {
        Map<String, Coordinate> coordinates = new HashMap<>();
        for (URL url : resourceResolver.getResources("classpath:pom.xml").collect(Collectors.toList())) {
            try {
                InputStream inputStream = url.openStream();
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

                    if (StringUtils.isNotEmpty(groupId) && StringUtils.isNotEmpty(artifactId)) {
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
                e.printStackTrace();
            }
        }
        this.coordinates = coordinates;
    }

    @Override
    @NonNull
    public Optional<Coordinate> resolve(@NonNull String artifactId) {
        return Optional.ofNullable(coordinates.get(artifactId));
    }

    private static Document documentFor(@NonNull InputStream inputStream)
            throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        return dBuilder.parse(inputStream);
    }

    @NonNull
    private Optional<String> valueOfNode(@NonNull Node node) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeName().equals(NODE_NAME_TEXT)) {
                return Optional.of(child.getNodeValue());
            }
        }
        return Optional.empty();
    }

    @NonNull
    public Map<String, Coordinate> getCoordinates() {
        return coordinates;
    }
}
