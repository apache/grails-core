/**
 * Copyright 2024 The Unity Foundation
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
package grails.doc

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

/**
 * Task to fetch the Grails source code.
 *
 * @author Puneet Behl
 * @since 6.2.0
 */
abstract class FetchGrailsTestsSourceTask extends DefaultTask {

    @Optional
    @Input
    String explicitGrailsTestsHome = project.findProperty('grails.functional.test.home') ?: null

    @Optional
    @Input
    String grailsVersion = System.getenv('TARGET_GRAILS_VERSION')

    @OutputDirectory
    def grailsTestsCheckOutDir = project.findProperty('grailsTestsCheckOutDir') ?: project.layout.buildDirectory.dir("grails-functional-tests/checkout")

    @TaskAction
    void fetchGrailsTestsSource() {
        if (!explicitGrailsTestsHome) {
            ant.mkdir(dir: grailsTestsCheckOutDir)

            println "Downloading Grails Functional Tests source code. If you already have a copy " +
                    "of the Grails Functional Tests source code checked out you can avoid this download " +
                    "by setting the grails.functional.test.home system property to point to your local " +
                    "copy of the source. See README.md for more information."

            def zipFile = "${grailsTestsCheckOutDir}/grails-src.zip"
            if (grailsVersion) {
                ant.get(src: "https://github.com/grails/grails-functional-tests/archive/refs/heads/${grailsVersion.replaceFirst(/^(\d+\.\d+)\..*/, '$1.x')}.zip", dest: zipFile, verbose: true)
            } else {
                ant.get(src: "https://github.com/grails/grails-functional-tests/zipball/${project.githubBranch}", dest: zipFile, verbose: true)
            }

            ant.unzip(src: zipFile, dest: grailsTestsCheckOutDir) {
                mapper(type: "regexp", from: "(grails-functional-tests-\\S*?/)(.*)", to: "grails-functional-test-src/\\2")
            }

            ant.chmod(file: "${grailsTestsCheckOutDir}/grails-functional-test-src/gradlew", perm: 700)

            println "Grails Functional Tests source code has been downloaded to ${grailsTestsCheckOutDir}"
        } else {
            println "GRAILS TESTS HOME=${explicitGrailsTestsHome}"
        }
    }
}

