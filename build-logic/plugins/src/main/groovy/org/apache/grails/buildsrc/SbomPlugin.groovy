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

package org.apache.grails.buildsrc

import java.util.concurrent.atomic.AtomicBoolean

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.cyclonedx.gradle.CycloneDxPlugin
import org.cyclonedx.gradle.CycloneDxTask
import org.cyclonedx.model.Component
import org.cyclonedx.model.ExternalReference
import org.cyclonedx.model.License
import org.cyclonedx.model.LicenseChoice
import org.cyclonedx.model.OrganizationalContact
import org.cyclonedx.model.OrganizationalEntity
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.CopySpec
import org.gradle.api.file.RegularFile
import org.gradle.api.java.archives.Manifest
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.bundling.Jar

import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

import static org.apache.grails.buildsrc.GradleUtils.lookupProperty

@CompileStatic
class SbomPlugin implements Plugin<Project> {

    // ordered so that first value is the most preferred, this list is from https://www.apache.org/legal/resolved.html
    private static List<String> PREFERRED_LICENSES = [
            'Apache-2.0', 'EPL-1.0', 'BSD-3-Clause', 'EPL-2.0', 'MIT', 'MIT-0', '0BSD', 'UPL-1.0',
            'CC0-1.0', 'ICU', 'Xnet', 'NCSA', 'W3C', 'Zlib', 'AFL-3.0', 'MS-PL', 'PSF-2.0', 'APAFML',
            'BSL-1.0', 'WTFPL', 'Unlicense', 'HPND', 'EPICS', 'TCL'
    ]

    // licenses are standardized @ https://spdx.org/licenses/
    private static Map<String, LinkedHashMap<String, String>> LICENSES = [
            'Apache-2.0'  : [
                    id : 'Apache-2.0',
                    url: 'https://www.apache.org/licenses/LICENSE-2.0'
            ],
            'BSD-2-Clause': [
                    id : 'BSD-2-Clause',
                    url: 'https://opensource.org/license/bsd-2-clause/'
            ],
            'BSD-3-Clause': [
                    id : 'BSD-3-Clause',
                    url: 'https://opensource.org/license/bsd-3-clause/'
            ],
            'CC0-1.0'     : [
                    id : 'CC0-1.0',
                    url: 'https://creativecommons.org/publicdomain/zero/1.0/'
            ],
            'LGPL-2.1-only': [
                    id : 'LGPL-2.1-only',
                    url: 'https://spdx.org/licenses/LGPL-2.1-only.html'
            ],
            // Variant of Apache 1.1 license. Approved by legal LEGAL-707
            'OpenSymphony': [
                    // id is optional and the opensymphony license doesn't have an SPDX id
                    name: 'The OpenSymphony Software License, Version 1.1',
                    url : 'https://raw.githubusercontent.com/sitemesh/sitemesh2/refs/heads/master/LICENSE.txt'
            ],
            'UPL-1.0'     : [
                    id : 'UPL-1.0',
                    url: 'https://oss.oracle.com/licenses/upl/'
            ],
    ]

    private static Map<String, String> LICENSE_MAPPING = [
            'pkg:maven/org.antlr/antlr4-runtime@4.7.2?type=jar'               : 'BSD-3-Clause', // maps incorrectly because of https://github.com/CycloneDX/cyclonedx-core-java/issues/205
            'pkg:maven/jline/jline@2.14.6?type=jar'                           : 'BSD-2-Clause', // maps incorrectly because of https://github.com/CycloneDX/cyclonedx-core-java/issues/205
            'pkg:maven/org.jline/jline@3.23.0?type=jar'                       : 'BSD-2-Clause', // maps incorrectly because of https://github.com/CycloneDX/cyclonedx-core-java/issues/205
            'pkg:maven/org.liquibase.ext/liquibase-hibernate5@4.27.0?type=jar': 'Apache-2.0', // maps incorrectly because of https://github.com/liquibase/liquibase/issues/2445 & the base pom does not define a license
            'pkg:maven/com.oracle.coherence.ce/coherence-bom@25.03.1?type=pom': 'UPL-1.0', // does not have map based on license id
            'pkg:maven/com.oracle.coherence.ce/coherence-bom@22.06.2?type=pom': 'UPL-1.0', // does not have map based on license id
            'pkg:maven/opensymphony/sitemesh@2.6.0?type=jar'                  : 'OpenSymphony', // custom license approved by legal LEGAL-707
            'pkg:maven/org.jruby/jzlib@1.1.5?type=jar'                        : 'BSD-3-Clause', // https://web.archive.org/web/20240822213507/http://www.jcraft.com/jzlib/LICENSE.txt shows it's a 3 clause
            'pkg:maven/org.jboss/jandex@3.2.3?type=pom'                       : 'CC0-1.0', // upstream declares Public Domain with CC0 URL but no SPDX id
            'pkg:maven/org.hibernate.tool/hibernate-tools-orm@6.6.36.Final?type=jar' : 'LGPL-2.1-only', // upstream pom does not expose SPDX id
            'pkg:maven/org.hibernate.orm/hibernate-core@6.6.36.Final?type=jar': 'LGPL-2.1-only',
            'pkg:maven/org.hibernate.tool/hibernate-tools-utils@6.6.36.Final?type=jar' : 'LGPL-2.1-only' // upstream pom does not expose SPDX id
    ]

    // we don't distribute these so these licenses are considered acceptable, but we still prefer ASF licenses.
    // Require a whitelist of any case of category X licenses to prevent accidental inclusion in a distributed artifact
    // this list will need to be updated anytime we change versions so we can revise the licenses
    private static Map<String, LinkedHashMap<String, String>> LICENSE_EXCEPTIONS = [
            'grails-data-hibernate5-core'       : [
                    'pkg:maven/org.hibernate.common/hibernate-commons-annotations@5.1.2.Final?type=jar': 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
                    'pkg:maven/org.hibernate/hibernate-core-jakarta@5.6.15.Final?type=jar'             : 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
            ],
            'grails-data-hibernate5'            : [
                    'pkg:maven/org.hibernate.common/hibernate-commons-annotations@5.1.2.Final?type=jar': 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
                    'pkg:maven/org.hibernate/hibernate-core-jakarta@5.6.15.Final?type=jar'             : 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
            ],
            'grails-data-hibernate5-spring-boot': [
                    'pkg:maven/org.hibernate.common/hibernate-commons-annotations@5.1.2.Final?type=jar': 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
                    'pkg:maven/org.hibernate/hibernate-core-jakarta@5.6.15.Final?type=jar'             : 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
            ],
            'grails-data-hibernate5-dbmigration': [
                    'pkg:maven/javax.xml.bind/jaxb-api@2.3.1?type=jar': 'CDDL-1.1', // api export
            ],
    ]

    @Override
    void apply(Project project) {
        project.pluginManager.apply(CycloneDxPlugin)

        def sbomOutputLocation = project.layout.buildDirectory.file(
                project.provider {
                    def artifactId = lookupProperty(project, 'pomArtifactId', project.name)
                    def version = project.findProperty('projectVersion')
                    "$artifactId-$version-sbom.json" as String
                }
        )

        configureSbomTask(project, sbomOutputLocation)
        configureNormalization(project)
        ensureLicensesValidated(project)

        // sboms are only published to Grails jar files at this time
        publishSbomForJarProjects(project, sbomOutputLocation)
    }

    private static void configureSbomTask(Project project, Provider<RegularFile> sbomOutputLocation) {
        project.tasks.withType(CycloneDxTask).configureEach { CycloneDxTask task ->
            task.with {
                // the 2.x version of Cyclonedx uses a legacy syntax & helpers for setting inputs so the syntax below
                // is required until the 3.x version is GA
                projectType = Component.Type.valueOf(lookupProperty(project, 'sbomProjectType', 'FRAMEWORK'))
                componentName = lookupProperty(project, 'pomArtifactId', project.name)
                task.@organizationalEntity.set(new OrganizationalEntity(
                        name: 'Apache Software Foundation',
                        urls: [
                                'https://www.apache.org/',
                                'https://security.apache.org/'
                        ],
                        contacts: [
                                new OrganizationalContact(
                                        name: 'Apache Grails Development Team',
                                        email: 'dev@grails.apache.org'
                                )
                        ]
                ))
                task.@licenseChoice.set(new LicenseChoice(
                        licenses: [
                                new License(
                                        name: 'Apache-2.0',
                                        url: 'https://www.apache.org/licenses/LICENSE-2.0.txt'
                                )
                        ]
                ))

                def projectVersion = project.findProperty('projectVersion').toString()
                def references = [
                        new ExternalReference(
                                url: 'https://grails.apache.org',
                                type: ExternalReference.Type.WEBSITE
                        ),
                        new ExternalReference(
                                url: 'https://github.com/apache/grails-core',
                                type: ExternalReference.Type.VCS
                        ),
                        new ExternalReference(
                                url: projectVersion.endsWith('SNAPSHOT') ? 'dev@grails.apache.org' : 'users@grails.apache.org',
                                type: ExternalReference.Type.MAILING_LIST
                        )
                ]

                if (!projectVersion.endsWith('SNAPSHOT')) {
                    references.add(
                            new ExternalReference(
                                    url: "https://grails.apache.org/docs/${project.findProperty('projectVersion')}/index.html",
                                    type: ExternalReference.Type.DOCUMENTATION
                            )
                    )
                }
                task.@externalReferences.set(references)

                // sboms are published for the purposes of vulnerability analysis so only include the runtime classpath
                includeConfigs = ['runtimeClasspath']
                skipConfigs = ['compileClasspath', 'testRuntimeClasspath']

                // turn off license text since it's base64 encoded & will inflate the jar sizes
                includeLicenseText = false

                // disable xml output
                xmlOutput.unsetConvention()
                jsonOutput.set(sbomOutputLocation.get())
                outputs.file(sbomOutputLocation)

                // cyclonedx does not support "choosing" the license placed in the sbom
                // see: https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/16
                doLast {
                    // json schema is documented here: https://cyclonedx.org/docs/1.6/json/
                    def rewriteSbom = { File f ->
                        def bom = new JsonSlurper().parse(f)

                        // timestamp is not reproducible: https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/292
                        // Use a fixed epoch when SOURCE_DATE_EPOCH is not set so the SBOM is identical between
                        // builds. This prevents the non-reproducible timestamp from changing the jar checksum
                        // and cascading cache misses through the compile classpath of downstream projects.
                        boolean isReproducibleBuild = lookupProperty(project, 'isReproducibleBuild')
                        ZonedDateTime sbomTimestamp = isReproducibleBuild ?
                                lookupProperty(project, 'buildDate') as ZonedDateTime :
                                Instant.EPOCH.atZone(ZoneOffset.UTC)
                        bom['metadata']['timestamp'] = DateTimeFormatter.ISO_INSTANT.format(sbomTimestamp.truncatedTo(ChronoUnit.SECONDS))

                        // components[*]
                        def comps = (bom instanceof Map && bom.components instanceof List) ? bom.components : []
                        comps.each { c ->
                            // .licenses => choose a license that is compatible with ASF policy if multiple licensed
                            if (c instanceof Map && c.licenses instanceof List && !(c.licenses as List).empty) {
                                def chosen = pickLicense(task, c['bom-ref'] as String, c.licenses as List)
                                if (chosen != null) {
                                    c.licenses = [chosen]
                                }
                            }

                            // .hashes => project hashes are only generated if the jar file has been created,
                            // which with a parallel build may not have occurred, so for any dependency that is a
                            // project we exclude them
                            if (c instanceof Map && c.hashes instanceof List && !(c.hashes as List).empty) {
                                def componentPath = c['bom-ref'] as String
                                if (componentPath.contains('?project_path=')) {
                                    c.remove('hashes')
                                }
                            }
                        }

                        // dependencies[*].dependsOn is not reproducible, so sort it
                        def dependencies = (bom instanceof Map && bom.dependencies instanceof List) ? bom.dependencies : []
                        dependencies.each { d ->
                            if (d instanceof Map && d.dependsOn instanceof List && !(d.dependsOn as List).empty) {
                                d.dependsOn = (d.dependsOn as List).sort()
                            }
                        }

                        // force the serialNumber to be reproducible by removing it & recalculating
                        bom['serialNumber'] = ''
                        def withOutSerial = JsonOutput.prettyPrint(JsonOutput.toJson(bom))
                        def uuid = UUID.nameUUIDFromBytes(withOutSerial.getBytes(StandardCharsets.UTF_8.name()))
                        bom['serialNumber'] = "urn:uuid:$uuid".toString()

                        f.setText(JsonOutput.prettyPrint(JsonOutput.toJson(bom)), StandardCharsets.UTF_8.name())

                        logger.info('Rewrote JSON SBOM ({}) to pick preferred license', project.relativePath(f))
                    }

                    sbomOutputLocation.get().with { rewriteSbom(it.asFile) }
                }

            }
        }
    }

    private static void configureNormalization(Project project) {
        project.normalization { handler ->
            handler.runtimeClasspath {
                it.ignore("META-INF/sbom.json")
            }
        }
    }
    @CompileDynamic
    private static Object pickLicense(CycloneDxTask task, String bomRef, List licenseChoices) {
        if (!bomRef) {
            throw new GradleException("No bomRef found for a dependency of ${task.project.name}, cannot pick license")
        }

        task.logger.info('Picking license for {} from {} choices', bomRef, licenseChoices.size())
        if (LICENSE_MAPPING.containsKey(bomRef)) {
            // There are several reasons that cyclone will get the license wrong, usually due to upstream not publishing information or publishing it incorrectly
            // see the licenseMapping map above for details
            def licenseId = LICENSE_MAPPING[bomRef]
            task.logger.lifecycle('Forcing license for {} to {}', bomRef, licenseId)

            def licenseBlock = LICENSES[licenseId]
            if (!licenseBlock) {
                throw new GradleException("Cannot find license information for id ${licenseId} to use for bomRef ${bomRef} in project ${task.project.name}")
            }

            return licenseBlock
        }

        if (!(licenseChoices instanceof List) || licenseChoices.isEmpty()) {
            throw new GradleException("No License was found for dependency: ${bomRef} in project ${task.project.name}")
        }

        def licenseIds = licenseChoices.findAll { it instanceof Map && it.license instanceof Map && it.license.id }
        def foundLicense = PREFERRED_LICENSES.find { p -> licenseIds.any { it.license.id == p } }
        if (foundLicense) {
            return licenseIds.find { it.license.id == foundLicense }
        }

        def defaultLicense = licenseChoices[0] // pick the first one found
        def defaultLicenseId = defaultLicense.license.id as String
        if (defaultLicenseId == null) {
            throw new GradleException("Could not determine License id for dependency: ${bomRef} in project ${task.project.name} for value ${defaultLicense}")
        }
        if (!(defaultLicenseId in PREFERRED_LICENSES)) {
            def projectLicenseExemptions = LICENSE_EXCEPTIONS[task.project.name] ?: [:]
            def permittedLicense = projectLicenseExemptions.get(bomRef) == defaultLicenseId
            if (!permittedLicense) {
                throw new GradleException("Unpermitted License found for bom dependency: ${bomRef} in project ${task.project.name} : ${defaultLicenseId}")
            }
        }

        return defaultLicense
    }

    private static void ensureLicensesValidated(Project project) {
        def initialized = new AtomicBoolean(false)
        // platforms only have constraints, so validate is not performed at this time
        ['java', 'java-library'].each {
            project.plugins.withId(it) {
                if (initialized.compareAndSet(false, true)) {
                    project.tasks.named('build').configure {
                        it.dependsOn('cyclonedxBom')
                    }
                }
            }
        }
    }

    private static void publishSbomForJarProjects(Project project, Provider<RegularFile> sbomOutputLocation) {
        def initialized = new AtomicBoolean(false)
        ['java', 'java-library'].each {
            project.plugins.withId(it) {
                if (initialized.compareAndSet(false, true)) {
                    project.afterEvaluate {
                        if (!project.findProperty('skipJavaComponent')) {
                            project.tasks.named('jar', Jar).configure { Jar jar ->
                                jar.dependsOn('cyclonedxBom')
                                jar.from(sbomOutputLocation) { CopySpec spec ->
                                    spec.into('META-INF')
                                    spec.rename {
                                        'sbom.json'
                                    }
                                }
                                jar.manifest { Manifest manifest ->
                                    manifest.attributes('Sbom-Location': 'META-INF/sbom.json')
                                    manifest.attributes('Sbom-Format': 'CycloneDX')
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
