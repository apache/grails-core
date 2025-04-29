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

package org.grails.cli.profile

import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.graph.Exclusion

/**
 * The utility class for the Grails profiles.
 *
 * @author Puneet Behl
 * @since 4.1
 */
class ProfileUtil {

    static Dependency createDependency(String coords, String scope, Map configEntry) {
        if (coords.count(':') == 1) {
            coords = "$coords:BOM"
        }
        Dependency dependency = new Dependency(new DefaultArtifact(coords), scope.toString())
        if (configEntry.containsKey('excludes')) {
            List<Exclusion> dependencyExclusions = new ArrayList<>()
            List excludes = (List) configEntry.excludes
            for (ex in excludes) {
                if (ex instanceof Map) {
                    dependencyExclusions.add(new Exclusion((String) ex.group, (String) ex.module, (String) ex.classifier, (String) ex.extension))
                }
            }
            dependency = dependency.setExclusions(dependencyExclusions)
        }
        dependency
    }
}
