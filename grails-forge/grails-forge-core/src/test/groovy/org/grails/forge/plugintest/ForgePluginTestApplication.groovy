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
package org.grails.forge.plugintest

import groovy.transform.CompileStatic
import org.springframework.boot.autoconfigure.EnableAutoConfiguration

import grails.boot.config.GrailsAutoConfiguration

/**
 * A Grails application with nothing of its own, in a package of its own so that artefact scanning
 * stays away from the generator classes: what it has, it has from the Forge plugin. The Grails
 * compiler enables auto-configuration on an application under {@code grails-app/init} only, so
 * this one, a test source, enables it itself; that is how the plugin's auto-configuration reaches
 * the context.
 */
@CompileStatic
@EnableAutoConfiguration
class ForgePluginTestApplication extends GrailsAutoConfiguration {
}
