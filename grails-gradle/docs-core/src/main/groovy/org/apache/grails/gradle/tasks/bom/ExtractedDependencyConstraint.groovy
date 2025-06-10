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

package org.apache.grails.gradle.tasks.bom

import groovy.transform.CompileStatic
import groovy.transform.MapConstructor
import groovy.transform.ToString

import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
@MapConstructor(includes = ['groupId', 'artifactId', 'version', 'versionPropertyReference', 'source'], includeSuperProperties = true)
@ToString(includes = ['groupId', 'artifactId', 'version', 'versionPropertyReference', 'source'], includeSuperProperties = true)
class ExtractedDependencyConstraint extends CoordinateVersionHolder {
    String versionPropertyReference
    String source

    ExtractedDependencyConstraint(String coordinates) {
        coordinates.split(':').with { String[] parts ->
            groupId = parts[0]
            artifactId = parts[1]
            version = parts[2]
        }
    }

    String getVersionPropertyReference() {
        versionPropertyReference == '${project.version}' ? '' : versionPropertyReference
    }

    String getVersionPropertyName() {
        String property = getVersionPropertyReference()
        if(!property) {
            return null
        }

        Pattern dynamicPattern = ~/\$\{([^}]+)\}/

        Matcher matcher = property =~ dynamicPattern
        if(!matcher.find()) {
            throw new IllegalStateException("Invalid Version Property: ${property}")
        }

        matcher.group(1)
    }
}