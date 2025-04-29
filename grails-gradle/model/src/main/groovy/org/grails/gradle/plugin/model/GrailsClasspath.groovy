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
package org.grails.gradle.plugin.model

import groovy.transform.CompileStatic

/**
 * Gradle ToolingModel class that is used to return Classpath to Grails cli
 *
 * this file is also in grails-shell-cli project
 */
@CompileStatic
interface GrailsClasspath extends Serializable {
    /**
     * @return All Grails dependencies, pull from 'testRuntimeOnly' scope
     */
    List<URL> getDependencies()

    /**
     * @return The profile dependencies
     */
    List<URL> getProfileDependencies()
    /**
     * @return The error message if one occurred
     */
    String getError()
}
