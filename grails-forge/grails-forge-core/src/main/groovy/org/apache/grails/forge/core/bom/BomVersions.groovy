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
package org.apache.grails.forge.core.bom

import groovy.transform.CompileStatic
import java.nio.charset.StandardCharsets

/**
 * The versions a BOM of Grails manages, as they were when this Forge was built: the BOM has the
 * version of Forge, so these are the versions an application that Forge generates with that BOM gets.
 *
 * <p>A table has what the published pom of the BOM manages itself, and what the BOMs of Grails it
 * imports manage: {@code org.apache.grails:grails-base-bom}, which each imports, and
 * {@code org.apache.grails:grails-bom}, which {@code grails-neo4j-bom} imports. What the other BOMs it
 * imports manage, Spring Boot's among them, is not in it: the table is for the artifacts of Grails and
 * of its plugins. It is a resource, {@code META-INF/grails-forge-boms/<artifact of the BOM>.tsv}, with a
 * line of {@code group:artifact}, a tab and the version for each, sorted.</p>
 *
 * @since 9.0
 */
@CompileStatic
final class BomVersions {

    /** The group of the BOMs of Grails */
    public static final String GROUP = 'org.apache.grails'

    /** Where the tables are among the resources */
    public static final String DIRECTORY = 'META-INF/grails-forge-boms'

    /** The BOMs this Forge is built with a table of */
    public static final List<String> SHIPPED = Collections.unmodifiableList(['grails-bom', 'grails-hibernate7-bom', 'grails-neo4j-bom'])

    private final String artifact
    private final Map<String, String> versions

    private BomVersions(String artifact, Map<String, String> versions) {
        this.artifact = artifact
        this.versions = versions
    }

    /**
     * @param artifact the artifact of a BOM of Grails, such as {@code grails-bom}
     * @return its table, or {@code null} when this Forge has none for that BOM
     */
    static BomVersions of(String artifact) {
        if (!SHIPPED.contains(artifact)) {
            return null
        }
        InputStream resource = BomVersions.classLoader.getResourceAsStream("${DIRECTORY}/${artifact}.tsv")
        if (resource == null) {
            return null
        }
        Map<String, String> versions = new LinkedHashMap<>()
        resource.withCloseable { InputStream input ->
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).readLines()) {
                int tab = line.indexOf('\t')
                if (tab > 0) {
                    versions.put(line.substring(0, tab), line.substring(tab + 1))
                }
            }
        }
        return new BomVersions(artifact, Collections.unmodifiableMap(versions))
    }

    /**
     * @return the BOM, as {@code group:artifact}
     */
    String getCoordinates() {
        return "${GROUP}:${artifact}"
    }

    /**
     * @param groupAndArtifact an artifact, as {@code group:artifact}
     * @return the version the BOM manages it at, or {@code null} for one it does not manage
     */
    String versionOf(String groupAndArtifact) {
        return versions.get(groupAndArtifact)
    }

    /**
     * @return what the BOM manages, {@code group:artifact} to version
     */
    Map<String, String> getVersions() {
        return versions
    }
}
