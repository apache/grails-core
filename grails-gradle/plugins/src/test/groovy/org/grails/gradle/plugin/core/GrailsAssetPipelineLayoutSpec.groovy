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

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

/**
 * Covers where the asset pipeline reads a Grails application's assets from and compiles them to,
 * using the asset pipeline plugin the Grails BOM manages.
 *
 * <p>The pipeline only defaults to {@code grails-app/assets} if the Grails plugin was applied
 * before it, and the Grails plugin can only configure it once it has been applied. Either order
 * has to end up with the same layout.</p>
 *
 * @since 8.0
 */
class GrailsAssetPipelineLayoutSpec extends Specification {

    private static final String GRAILS_WEB_PLUGIN = 'org.apache.grails.gradle.grails-web'

    private static final String ASSET_PIPELINE_PLUGIN = 'cloud.wondrify.asset-pipeline'

    @TempDir
    File projectDir

    /** Canonical paths: the temp dir arrives as /var/... but Gradle resolves it to /private/var/... */
    private String pathOf(String relative) {
        new File(projectDir, relative).canonicalPath
    }

    void 'assets are read from grails-app/assets and compiled to build/assets when #order'() {
        given:
        Project project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        project.extensions.extraProperties.set('grailsVersion', System.getProperty('projectVersion'))

        when:
        plugins.each { String id -> project.pluginManager.apply(id) }

        then:
        project.assets.assetsPath.get().asFile.canonicalPath == pathOf('grails-app/assets')
        project.tasks.named('assetCompile').get().destinationDirectory.get().asFile.canonicalPath ==
                pathOf('build/assets')

        where:
        order                                  | plugins
        'the Grails plugin is applied first'   | [GRAILS_WEB_PLUGIN, ASSET_PIPELINE_PLUGIN]
        'the asset pipeline is applied first'  | [ASSET_PIPELINE_PLUGIN, GRAILS_WEB_PLUGIN]
    }
}
