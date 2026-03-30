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
package org.grails.gradle.plugin.core

import groovy.transform.CompileStatic

import grails.util.GrailsNameUtils

/**
 * Represents a configurable test phase (e.g. integrationTest, functionalTest).
 *
 * <p>Default values for {@code sourceFolderName} and {@code systemPropertyName}
 * are derived from the phase {@code name} so that users only need to declare the
 * phase by name in most cases.</p>
 *
 * @since 2025.0
 */
@CompileStatic
class TestPhase {

    final String name

    String sourceFolderName

    String systemPropertyName

    boolean ideaIntegration = true

    TestPhase(String name) {
        this.name = name
        this.sourceFolderName = "src/${GrailsNameUtils.getScriptName(name)}"
        String naturalPart = name.replaceAll(/Test$/, '')
        this.systemPropertyName = "is.grails.${GrailsNameUtils.getScriptName(naturalPart)}.test"
    }
}
