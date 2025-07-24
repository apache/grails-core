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
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import org.gradle.api.GradleException

@EqualsAndHashCode(includes = ['version'], callSuper = true)
@CompileStatic
@ToString
class CoordinateVersionHolder extends CoordinateHolder {
    String version

    CoordinateHolder toCoordinateHolder() {
        new CoordinateHolder(groupId: groupId, artifactId: artifactId)
    }

    String getCoordinates() {
        if (!version) {
            throw new GradleException("Constraint does not have a version: ${this}")
        }

        "$groupId:$artifactId:$version" as String
    }
}
