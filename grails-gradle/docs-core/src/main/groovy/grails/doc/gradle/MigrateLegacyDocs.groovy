/* Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.doc.gradle

import grails.doc.LegacyDocMigrator
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*

/**
 * Gradle task for migrating Grails 1.x gdocs to the newer Grails 2.x form with
 * a YAML-based table of contents.
 */
class MigrateLegacyDocs extends DefaultTask {
    @InputDirectory File guideDir = new File(project.projectDir, "src/guide")
    @InputDirectory File resourcesDir = new File(project.projectDir, "resources")
    @OutputDirectory File outputDir = new File(project.projectDir, "src/guide.migrated")

    @TaskAction
    def migrate() {
        def props = new Properties()
        new File("${resourcesDir}/doc.properties").withInputStream {input ->
            props.load(input)
        }
        props = props.findAll { it.key.startsWith("alias.") }.collectEntries { [it.key[6..-1], it.value] }

        def migrator = new LegacyDocMigrator(guideDir, outputDir, props)
        migrator.migrate()
    }
}
