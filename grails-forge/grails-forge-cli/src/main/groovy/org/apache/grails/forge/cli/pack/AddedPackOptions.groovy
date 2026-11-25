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
package org.apache.grails.forge.cli.pack

import groovy.transform.CompileStatic
import picocli.CommandLine

/**
 * The options of a command that adds features to an application, or shows what adding them changes,
 * by which a feature pack the application does not have yet is taken and its feature is given its
 * options: {@code feature add} and {@code feature diff} declare them here, once, so that what the one
 * takes the other takes. The packs the application has are given again with {@link RecordedPackOptions}.
 *
 * @since 9.0
 */
@CompileStatic
class AddedPackOptions {

    @CommandLine.Option(names = ['--feature-option'], paramLabel = 'FEATURE.NAME=VALUE',
            description = 'An option of a feature that is added, which the feature pack of the feature declares: the name of the feature, a dot, the ' +
                    'name of the option and its value. An option that is not given has the value the pack gives it. The options of the features the ' +
                    'application has stay what its record says. Can be given more than once.')
    List<String> featureOptions = new ArrayList<>()

    @CommandLine.Option(names = ['--feature-pack-sha256'], paramLabel = 'COORDINATES=SHA256',
            description = 'The SHA-256 the jar of a plugin that is added by its coordinates has to have, as group:artifact:version=sha256. ' +
                    'A jar with another one is refused before it is opened. Can be given more than once.')
    List<String> featurePackDigests = new ArrayList<>()

    @CommandLine.Option(names = ['--feature-catalog'], paramLabel = 'URL',
            description = 'A feature catalog, in which a name that is neither a feature of Grails nor that of a feature pack that was given is ' +
                    'looked up, as the create commands look it up: the catalog says which plugin has the feature, in which version and with which ' +
                    'SHA-256. The address is https, or file: for a file; none is for no catalog. Without the option, the catalogs of the ' +
                    'GRAILS_FORGE_CATALOG environment variable are read, else the plugin index the GRAILS_PLUGIN_INDEX environment variable names, ' +
                    'and without either none. Can be given more than once.')
    List<String> featureCatalogs = new ArrayList<>()

    @CommandLine.Option(names = ['--refresh'],
            description = 'Read each feature catalog anew, and not the copy of it that is kept on this computer for a day, and fetch ' +
                    'the jar of a plugin that is added, and not the copy of it that is kept on this computer')
    boolean refresh

    /**
     * @return the options that were given, as they are typed, in the order the help has them
     */
    List<String> given() {
        List<String> given = []
        if (!featureCatalogs.isEmpty()) {
            given << '--feature-catalog'
        }
        if (!featureOptions.isEmpty()) {
            given << '--feature-option'
        }
        if (!featurePackDigests.isEmpty()) {
            given << '--feature-pack-sha256'
        }
        if (refresh) {
            given << '--refresh'
        }
        return given
    }
}
