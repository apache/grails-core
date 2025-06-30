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
package org.grails.gradle.plugin.publishing

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

import javax.inject.Inject

/**
 * @author Puneet Behl
 * @author James Daugherty
 * @since 4.0.11
 */
@CompileStatic
class GrailsPublishExtension {

    /**
     * The slug from github
     */
    final Property<String> githubSlug

    /**
     * The website URL of the published project; defaulted by the github slug if not set
     */
    final Property<String> websiteUrl

    /**
     * The SCM url, defaults based on the github slug to https://github.com/${githubSlug}
     */
    final Property<String> scmUrl

    /**
     * The url to connect via SCM tool, defaults based on the github slug to "scm:git@github.com:${githubSlug}.git"
     */
    final Property<String> scmUrlConnection

    /**
     * The source control URL of the project
     */
    final Property<String> vcsUrl

    /**
     * The license of the plugin
     */
    License license = new License()

    /**
     * The developers of the project
     */
    final MapProperty<String, String> developers

    /**
     * Title of the project, defaults to the project name
     */
    final Property<String> title

    /**
     * Description of the plugin
     */
    final Property<String> desc

    /**
     * The issue tracker name; github if github slug is set and not overridden
     */
    final Property<String> issueTrackerName

    /**
     * The issue tracker URL
     */
    final Property<String> issueTrackerUrl

    /**
     * Overrides the artifactId of the published artifact
     */
    final Property<String> artifactId

    /**
     * Overrides the groupId of the published artifact
     */
    final Property<String> groupId

    /**
     * Whether to publish test sources with a "tests" classifier
     */
    final Property<Boolean> publishTestSources

    /**
     * An optional closure to be invoked via pom.withXml { } to allow further customization
     */
    Closure pomCustomization

    /**
     * If another process will add the components set this to false so only the publication is created
     */
    final Property<Boolean> addComponents

    /**
     * The name of the publication
     */
    final Property<String> publicationName

    /**
     * If set, a local repository will be setup for the given path with the name 'TestCaseMavenRepo'. This can be useful
     * when testing plugins locally with builds that can't make use of includedBuild
     */
    final Property<Directory> testRepositoryPath

    @Inject
    GrailsPublishExtension(ObjectFactory objects, Project project) {
        githubSlug = objects.property(String).convention(
            project.provider {
                project.findProperty('githubSlug') as String
            }
        )
        websiteUrl = objects.property(String).convention(project.provider {
            String githubSlug = githubSlug.getOrNull()
            githubSlug ? "https://github.com/$githubSlug" as String : null
        })
        scmUrl = objects.property(String).convention(project.provider {
            String githubSlug = githubSlug.getOrNull()
            githubSlug ? "https://github.com/$githubSlug" as String : null
        })
        scmUrlConnection = objects.property(String).convention(project.provider {
            String githubSlug = githubSlug.getOrNull()
            githubSlug ? "scm:git@github.com:${githubSlug}.git" as String : null
        })
        vcsUrl = objects.property(String).convention(project.provider {
            String githubSlug = githubSlug.getOrNull()
            githubSlug ? "git@github.com:${githubSlug}.git" as String : null
        })
        developers = objects.mapProperty(String, String).convention([:])
        title = objects.property(String).convention(project.provider { project.name })
        desc = objects.property(String).convention(project.provider {
            title.getOrNull()
        })
        issueTrackerName = objects.property(String).convention(project.provider {
            String githubSlug = githubSlug.getOrNull()
            githubSlug ? 'GitHub Issues' : 'Issues'
        })
        issueTrackerUrl = objects.property(String).convention(project.provider {
            String githubSlug = githubSlug.getOrNull()
            githubSlug ? "https://github.com/$githubSlug/issues" as String : null
        })
        artifactId = objects.property(String).convention(project.provider {
            project.name
        })
        groupId = objects.property(String).convention(project.provider {
            project.group as String
        })
        publishTestSources = objects.property(Boolean).convention(false)
        addComponents = objects.property(Boolean).convention(true)
        publicationName = objects.property(String).convention('maven')
        testRepositoryPath = objects.directoryProperty().convention(null as Directory)
    }

    License getLicense() {
        return license
    }

    /**
     * Configures the license
     *
     * @param configurer The configurer
     * @return the license instance
     */
    License license(@DelegatesTo(License) Closure configurer) {
        configurer.delegate = license
        configurer.resolveStrategy = Closure.DELEGATE_FIRST
        configurer.call()
        return license
    }

    void setLicense(License license) {
        this.license = license
    }

    void setLicense(String license) {
        this.license.name = license
    }

    static class License {
        String name
        String url
        String distribution = 'repo'

        static final License APACHE2 = new License(name: 'The Apache Software License, Version 2.0', url: 'https://www.apache.org/licenses/LICENSE-2.0.txt')
        static final License EPL1 = new License(name: 'Eclipse Public License - v 1.0', url: 'https://www.eclipse.org/legal/epl-v10.html')
        static final License LGPL21 = new License(name: 'GNU Lesser General Public License, version 2.1', url: 'https://www.gnu.org/licenses/old-licenses/lgpl-2.1.html')
        static final License LGPL = new License(name: 'GNU Lesser General Public License', url: 'https://www.gnu.org/licenses/lgpl-3.0.html')
        static final License GPL = new License(name: 'GNU General Public License', url: 'https://www.gnu.org/licenses/gpl-3.0.en.html')
        static final License CPL = new License(name: 'Common Public License Version 1.0 (CPL)', url: 'https://opensource.org/licenses/cpl1.0.php')
        static final License AGPL = new License(name: 'GNU Affero General Public License', url: 'https://www.gnu.org/licenses/agpl-3.0.html')
        static final License MIT = new License(name: 'The MIT License (MIT)', url: 'https://opensource.org/licenses/MIT')
        static final License BSD = new License(name: 'The BSD 3-Clause License', url: 'https://opensource.org/licenses/BSD-3-Clause')
        static final Map<String, License> LICENSES = [
                'Apache-2.0'  : APACHE2,
                'Apache'      : APACHE2,
                'AGPL'        : AGPL,
                'AGPL-3.0'    : AGPL,
                'GPL-3.0'     : GPL,
                'GPL'         : GPL,
                'EPL'         : EPL1,
                'EPL-1.0'     : EPL1,
                'CPL'         : CPL,
                'CPL-1.0'     : CPL,
                'LGPL'        : LGPL,
                'LGPL-3.0'    : LGPL,
                'LGPL-2.1'    : LGPL21,
                'BSD'         : BSD,
                'BSD 3-Clause': BSD,
                'MIT'         : MIT
        ]
    }
}
