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
import org.cyclonedx.gradle.CyclonedxDirectTask
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
import org.gradle.api.file.Directory
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
            'MIT'         : [
                    id : 'MIT',
                    url: 'https://opensource.org/license/mit/'
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
            // public domain dedication; no SPDX id. ONLY approved libraries should use this.
            'Public-Domain': [
                    name: 'Public Domain',
                    url : 'https://github.com/stleary/JSON-java/blob/master/LICENSE'
            ],
    ]

    private static Map<String, String> LICENSE_MAPPING = [
            'pkg:maven/com.oracle.coherence.ce/coherence-bom@25.03.1?type=pom': 'UPL-1.0', // does not have map based on license id
            'pkg:maven/com.oracle.coherence.ce/coherence-bom@25.03.2?type=pom': 'UPL-1.0', // does not have map based on license id
            'pkg:maven/com.oracle.coherence.ce/coherence-bom@22.06.2?type=pom': 'UPL-1.0', // does not have map based on license id
            'pkg:maven/jline/jline@2.14.6?type=jar'                           : 'BSD-2-Clause', // maps incorrectly because of https://github.com/CycloneDX/cyclonedx-core-java/issues/205
            'pkg:maven/opensymphony/sitemesh@2.6.0?type=jar'                  : 'OpenSymphony', // custom license approved by legal LEGAL-707
            'pkg:maven/org.antlr/antlr4-runtime@4.7.2?type=jar'               : 'BSD-3-Clause', // maps incorrectly because of https://github.com/CycloneDX/cyclonedx-core-java/issues/205
            // The whole org.jline group declares "The BSD License", which maps incorrectly because of
            // https://github.com/CycloneDX/cyclonedx-core-java/issues/205 - the POMs point at BSD-3-Clause.
            // jline.version tracks the JLine version Groovy ships, so every module resolves to one version.
            'pkg:maven/org.jline/jansi@3.30.9?type=jar'                       : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline@3.30.9?type=jar'                       : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline-builtins@3.30.9?type=jar'              : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline-console@3.30.9?type=jar'               : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline-native@3.30.9?type=jar'                : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline-reader@3.30.9?type=jar'                : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline-style@3.30.9?type=jar'                 : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline-terminal@3.30.9?type=jar'              : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline-terminal-jansi@3.30.9?type=jar'        : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline-terminal-jna@3.30.9?type=jar'          : 'BSD-3-Clause',
            'pkg:maven/org.jline/jline-terminal-jni@3.30.9?type=jar'          : 'BSD-3-Clause',
            'pkg:maven/org.jruby/jzlib@1.1.5?type=jar'                        : 'BSD-3-Clause', // https://web.archive.org/web/20240822213507/http://www.jcraft.com/jzlib/LICENSE.txt shows it's a 3 clause
            'pkg:maven/org.liquibase.ext/liquibase-hibernate5@4.27.0?type=jar': 'Apache-2.0', // maps incorrectly because of https://github.com/liquibase/liquibase/issues/2445 & the base pom does not define a license
            'pkg:maven/org.json/json@20251224?type=jar'                       : 'Public-Domain', // required due to jedis, https://issues.apache.org/jira/browse/LEGAL-666 approves this usage
            // Bouncy Castle Licence is the MIT license (https://www.bouncycastle.org/licence.html); pulled in transitively by the CAS client
            'pkg:maven/org.bouncycastle/bcpkix-jdk18on@1.84?type=jar'         : 'MIT',
            'pkg:maven/org.bouncycastle/bcprov-jdk18on@1.84?type=jar'         : 'MIT',
            'pkg:maven/org.bouncycastle/bcutil-jdk18on@1.84?type=jar'         : 'MIT',
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
            'grails-data-hibernate5-spring-orm' : [
                    'pkg:maven/org.hibernate.common/hibernate-commons-annotations@5.1.2.Final?type=jar': 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
                    'pkg:maven/org.hibernate/hibernate-core-jakarta@5.6.15.Final?type=jar'             : 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
            ],
            'grails-data-hibernate5-dbmigration': [
                    'pkg:maven/javax.xml.bind/jaxb-api@2.3.1?type=jar': 'CDDL-1.1', // api export
            ],
            'grails-data-hibernate5-dbmigration-cli': [
                    'pkg:maven/org.hibernate.common/hibernate-commons-annotations@5.1.2.Final?type=jar': 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
                    'pkg:maven/org.hibernate/hibernate-core-jakarta@5.6.15.Final?type=jar'             : 'LGPL-2.1-only', // hibernate 5 is LGPL, we are migrating to ASF license in hibernate 7
            ],
            'grails-data-hibernate7-dbmigration-core': [
                    'pkg:maven/javax.xml.bind/jaxb-api@2.3.1?type=jar': 'CDDL-1.1', // api export
            ],
            'grails-data-hibernate7-dbmigration': [
                    'pkg:maven/javax.xml.bind/jaxb-api@2.3.1?type=jar': 'CDDL-1.1', // api export
            ],
    ]

    @Override
    void apply(Project project) {
        registerCyclonedxDirectBomTask(project)

        configureSbomMetadata(project)

        Provider<String> artifactId = project.provider { lookupProperty(project, 'pomArtifactId', project.name).toString() }
        def sbomOutputLocation = sbomOutputLocationFor(project, artifactId)
        configureBomTaskOutput(project, 'cyclonedxDirectBom', sbomOutputLocation, artifactId, ['runtimeClasspath'])

        configureNormalization(project)
        ensureLicensesValidated(project)

        // sboms are only published to Grails jar files at this time
        publishSbomForJarProjects(project, sbomOutputLocation)

        configureCliSbom(project)
    }

    static Provider<RegularFile> sbomOutputLocationFor(Project project, Provider<String> artifactId) {
        project.layout.buildDirectory.file(project.provider {
            "${artifactId.get()}-${project.findProperty('projectVersion')}-sbom.json" as String
        })
    }

    /**
     * Register only the per-module {@link CyclonedxDirectTask}. We deliberately do not apply
     * {@code CyclonedxPlugin} because doing so unconditionally also registers the
     * {@code cyclonedxBom} aggregate task ({@code CyclonedxAggregateTask}), and triggers
     * Spring Boot 4's {@code CycloneDxPluginAction} to wire that aggregate's output into the
     * jar at {@code META-INF/sbom/application.cdx.json}. Grails ships per-module SBOMs only
     * (at {@code META-INF/sbom.json}, see {@link #publishSbomForJarProjects}); the aggregate
     * SBOM is misleading for library jars and would also need its own reproducibility
     * post-processing. Registering the direct task ourselves means the aggregate task is
     * never created in the first place, so there is nothing to disable, exclude, or filter.
     */
    private static void registerCyclonedxDirectBomTask(Project project) {
        project.tasks.register('cyclonedxDirectBom', CyclonedxDirectTask) { CyclonedxDirectTask task ->
            Provider<Directory> reportDir = project.layout.buildDirectory.dir('reports/cyclonedx-direct')
            task.xmlOutput.convention(reportDir.map { it.file('bom.xml') })
            task.jsonOutput.convention(reportDir.map { it.file('bom.json') })
        }
    }

    private static void configureSbomMetadata(Project project) {
        project.tasks.withType(CyclonedxDirectTask).configureEach { CyclonedxDirectTask task ->
            task.with {
                projectType.set(Component.Type.valueOf(lookupProperty(project, 'sbomProjectType', 'FRAMEWORK').toString()))
                organizationalEntity.set(new OrganizationalEntity(
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
                licenseChoice.set(new LicenseChoice(
                        licenses: [
                                new License(
                                        name: 'Apache-2.0',
                                        url: 'https://www.apache.org/licenses/LICENSE-2.0.txt'
                                )
                        ]
                ))

                def projectVersion = project.findProperty('projectVersion').toString()
                List<ExternalReference> references = [
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
                externalReferences.set(references)

                // turn off license text since it's base64 encoded & will inflate the jar sizes
                includeLicenseText.set(false)

                // Don't auto-inject the build-system externalReference. cyclonedx-gradle-plugin 3.0.0
                // populates this from CI env vars (e.g. GitHub Actions run URL), which is unknowable
                // from a local rebuild and would break byte-identical reproducibility between CI and
                // local builds. Per ASF policy we ship reproducible artifacts and intentionally leave
                // build info out of the sbom; this is the supported plugin-level off switch for it.
                includeBuildSystem.set(false)

                // disable xml output; the json output location, the scanned configuration, and the
                // component name are set per task by configureBomTaskOutput so each published jar's
                // SBOM lists only its own dependencies (the primary jar scans runtimeClasspath, the
                // cli companion scans cliRuntimeClasspath)
                xmlOutput.unsetConvention()

                // cyclonedx does not support "choosing" the license placed in the sbom
                // see: https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/16
                // Capture project name/path at configuration time to avoid deprecated Task.project access at execution time.
                // isReproducibleBuild and buildDate are providers so they are evaluated lazily at execution time.
                // See: https://docs.gradle.org/current/userguide/configuration_cache.html#config_cache:requirements:use_project_during_execution
                def projectName = project.name
                def projectPath = project.path
                Provider<RegularFile> bomOutput = jsonOutput
                Provider<String> sbomComponent = componentName
                Provider<Boolean> isReproducibleBuildProvider = project.provider { lookupProperty(project, 'isReproducibleBuild') as boolean }
                Provider<ZonedDateTime> buildDateProvider = project.provider { lookupProperty(project, 'buildDate') as ZonedDateTime }
                doLast {
                    // json schema is documented here: https://cyclonedx.org/docs/1.6/json/
                    def rewriteSbom = { File f ->
                        def bom = new JsonSlurper().parse(f)

                        // timestamp is not reproducible: https://github.com/CycloneDX/cyclonedx-gradle-plugin/issues/292
                        // Use a fixed epoch when SOURCE_DATE_EPOCH is not set so the SBOM is identical between
                        // builds. This prevents the non-reproducible timestamp from changing the jar checksum
                        // and cascading cache misses through the compile classpath of downstream projects.
                        ZonedDateTime sbomTimestamp = isReproducibleBuildProvider.get() ?
                                buildDateProvider.get() :
                                Instant.EPOCH.atZone(ZoneOffset.UTC)
                        bom['metadata']['timestamp'] = DateTimeFormatter.ISO_INSTANT.format(sbomTimestamp.truncatedTo(ChronoUnit.SECONDS))

                        // components[*]
                        def comps = (bom instanceof Map && bom.components instanceof List) ? bom.components : []
                        comps.each { c ->
                            // .licenses => choose a license that is compatible with ASF policy if multiple licensed
                            if (c instanceof Map && c.licenses instanceof List && !(c.licenses as List).empty) {
                                def chosen = pickLicense(logger, projectName, sbomComponent.get(), c['bom-ref'] as String, c.licenses as List)
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

                        // force the serialNumber to be reproducible by clearing it & recalculating.
                        // Mix the projectPath into the UUID seed so two modules whose post-processed
                        // BOM JSON happens to be identical (for example, empty BOM platforms with no
                        // runtime dependencies, or modules whose metadata.component is filled in
                        // identically by the CycloneDX plugin) still receive distinct serialNumbers
                        // as required by the CycloneDX specification. Including projectPath preserves
                        // reproducibility because the same project path + same content always yields
                        // the same UUID across rebuilds. This guards against collisions introduced by
                        // CycloneDX 3.0.0 / Gradle 9 metadata changes.
                        // See: https://cyclonedx.org/docs/1.6/json/#serialNumber
                        bom['serialNumber'] = ''
                        def withoutSerial = JsonOutput.prettyPrint(JsonOutput.toJson(bom))
                        def uuidSeed = "${projectPath}\n${withoutSerial}"
                        def uuid = UUID.nameUUIDFromBytes(uuidSeed.getBytes(StandardCharsets.UTF_8))
                        bom['serialNumber'] = "urn:uuid:$uuid".toString()

                        f.setText(JsonOutput.prettyPrint(JsonOutput.toJson(bom)), StandardCharsets.UTF_8.name())

                        logger.info('Rewrote JSON SBOM ({}) to pick preferred license', projectPath)
                    }

                    bomOutput.get().with { rewriteSbom(it.asFile) }
                }

            }
        }
    }

    /**
     * Wires the per-task SBOM settings that differ between a module's primary jar and its cli
     * companion: the emitted component name, the configuration whose resolved runtime classpath is
     * scanned, and the json output location. Together with {@link #configureSbomMetadata} this lets
     * a single module emit more than one SBOM, each describing only the dependencies of its own jar.
     */
    private static void configureBomTaskOutput(Project project, String taskName, Provider<RegularFile> output,
                                               Provider<String> componentName, List<String> includeConfigs) {
        project.tasks.named(taskName, CyclonedxDirectTask).configure { CyclonedxDirectTask task ->
            task.componentName.set(componentName)
            // sboms are published for the purposes of vulnerability analysis so only include the runtime classpath
            task.includeConfigs.set(includeConfigs)
            task.jsonOutput.set(output)
            task.outputs.file(output)
        }
    }

    /**
     * A plugin that ships a cli companion artifact (via {@code org.apache.grails.gradle.grails-plugin-cli})
     * publishes {@code <artifactId>-cli} as its own Maven coordinate with its own runtime dependencies.
     * Give that jar its own SBOM built from the cli source set's runtime classpath — the same
     * configuration the cli publication maps its dependencies from — so the companion carries an
     * accurate bill of materials just like the primary jar.
     */
    private static void configureCliSbom(Project project) {
        project.pluginManager.withPlugin('org.apache.grails.gradle.grails-plugin-cli') {
            Provider<String> cliArtifactId = cliCompanionArtifactId(project)

            project.tasks.register('cyclonedxCliBom', CyclonedxDirectTask) { CyclonedxDirectTask task ->
                Provider<Directory> reportDir = project.layout.buildDirectory.dir('reports/cyclonedx-cli')
                task.xmlOutput.convention(reportDir.map { it.file('bom.xml') })
                task.jsonOutput.convention(reportDir.map { it.file('bom.json') })
            }

            def cliSbomOutputLocation = sbomOutputLocationFor(project, cliArtifactId)
            configureBomTaskOutput(project, 'cyclonedxCliBom', cliSbomOutputLocation, cliArtifactId, ['cliRuntimeClasspath'])

            project.tasks.named('build').configure { it.dependsOn('cyclonedxCliBom') }

            publishSbomForCliJar(project, cliSbomOutputLocation)
        }
    }

    /**
     * The companion coordinate published by the cli-artifact plugin, honouring a custom
     * {@code cliArtifact { artifactId = '...' }}. Read from the extension directly (the same property
     * the cli publication uses) rather than the derived {@code <name>-cli} default, so a customised
     * name is reflected in the SBOM component name and file. Accessed dynamically because build-logic
     * does not compile against the cli-artifact plugin.
     */
    @CompileDynamic
    static Provider<String> cliCompanionArtifactId(Project project) {
        project.extensions.getByName('cliArtifact').artifactId
    }

    private static void publishSbomForCliJar(Project project, Provider<RegularFile> sbomOutputLocation) {
        Provider<Boolean> publishesJavaComponent = project.provider { !project.findProperty('skipJavaComponent') as boolean }

        project.tasks.named('cliJar', Jar).configure { Jar jar ->
            jar.dependsOn('cyclonedxCliBom')
            jar.from(publishesJavaComponent.map { include -> include ? sbomOutputLocation.get() : [] }) { CopySpec spec ->
                spec.into('META-INF')
                spec.rename {
                    'sbom.json'
                }
            }
            jar.manifest { Manifest manifest ->
                manifest.attributes('Sbom-Location': 'META-INF/sbom.json')
                manifest.attributes('Sbom-Format': 'CycloneDX')
            }
            jar.doFirst {
                if (!publishesJavaComponent.get()) {
                    jar.manifest.attributes.remove('Sbom-Location')
                    jar.manifest.attributes.remove('Sbom-Format')
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

    /**
     * Picks the most appropriate license for a dependency from a list of license choices.
     * This method is called at execution time and should not access Task.project.
     *
     * @param logger the logger to use for logging
     * @param projectName the name of the project (captured at configuration time)
     * @param sbomComponent the artifact the SBOM describes (the primary or cli companion coordinate)
     * @param bomRef the bom reference for the dependency
     * @param licenseChoices the list of license choices
     * @return the chosen license
     */
    @CompileDynamic
    static Object pickLicense(org.gradle.api.logging.Logger logger, String projectName, String sbomComponent, String bomRef, List licenseChoices) {
        if (!bomRef) {
            throw new GradleException("No bomRef found for a dependency of ${sbomComponent}, cannot pick license")
        }

        logger.info('Picking license for {} from {} choices', bomRef, licenseChoices.size())
        if (LICENSE_MAPPING.containsKey(bomRef)) {
            // There are several reasons that cyclone will get the license wrong, usually due to upstream not publishing information or publishing it incorrectly
            // see the licenseMapping map above for details
            def licenseId = LICENSE_MAPPING[bomRef]
            logger.lifecycle('Forcing license for {} to {}', bomRef, licenseId)

            def licenseBlock = LICENSES[licenseId]
            if (!licenseBlock) {
                throw new GradleException("Cannot find license information for id ${licenseId} to use for bomRef ${bomRef} in ${sbomComponent}")
            }

            return licenseBlock
        }

        if (!(licenseChoices instanceof List) || licenseChoices.isEmpty()) {
            throw new GradleException("No License was found for dependency: ${bomRef} in ${sbomComponent}")
        }

        def licenseIds = licenseChoices.findAll { it instanceof Map && it.license instanceof Map && it.license.id }
        def foundLicense = PREFERRED_LICENSES.find { p -> licenseIds.any { it.license.id == p } }
        if (foundLicense) {
            return licenseIds.find { it.license.id == foundLicense }
        }

        def defaultLicense = licenseChoices[0] // pick the first one found
        def defaultLicenseId = defaultLicense.license.id as String
        if (defaultLicenseId == null) {
            throw new GradleException("Could not determine License id for dependency: ${bomRef} in ${sbomComponent} for value ${defaultLicense}")
        }
        if (!(defaultLicenseId in PREFERRED_LICENSES)) {
            // an exemption may be declared against the project or the specific SBOM component (e.g. a
            // cli companion whose runtime classpath differs from the primary jar)
            def exemptions = (LICENSE_EXCEPTIONS[projectName] ?: [:]) + (LICENSE_EXCEPTIONS[sbomComponent] ?: [:])
            def permittedLicense = exemptions.get(bomRef) == defaultLicenseId
            if (!permittedLicense) {
                throw new GradleException("Unpermitted License found for bom dependency: ${bomRef} in ${sbomComponent} : ${defaultLicenseId}")
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
                        it.dependsOn('cyclonedxDirectBom')
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
                                jar.dependsOn('cyclonedxDirectBom')
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
