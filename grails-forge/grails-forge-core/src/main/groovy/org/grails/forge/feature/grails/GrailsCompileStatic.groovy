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
package org.grails.forge.feature.grails

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.feature.Category
import org.grails.forge.feature.Feature

/**
 * Compiles the application statically through the Grails Gradle plugin's {@code compileStatic} options:
 * {@code controllers}, {@code services} and {@code tagLibs}, and {@code gsp} for the views when the
 * application has GSP. Each is set on its own rather than through {@code all}, so an artefact type a later
 * release adds to {@code all} is not compiled statically without being asked for. The block itself is
 * written by the {@code build.gradle} template.
 */
@Component
@CompileStatic
class GrailsCompileStatic implements Feature {

    public static final String NAME = 'grails-compile-static'

    @Override
    String getName() {
        return NAME
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Override
    String getTitle() {
        return 'Compile Static'
    }

    @Override
    String getDescription() {
        return 'Compiles controllers, services and tag libraries statically, and GSP views in applications that use GSP, by enabling the Grails Gradle plugin\'s compileStatic options.'
    }

    @Override
    String getCategory() {
        return Category.CONFIGURATION
    }
}
