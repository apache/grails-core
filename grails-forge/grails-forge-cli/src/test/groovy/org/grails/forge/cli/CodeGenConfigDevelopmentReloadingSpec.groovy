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

package org.grails.forge.cli

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import org.grails.forge.io.ConsoleOutput
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Path

class CodeGenConfigDevelopmentReloadingSpec extends Specification {

    @TempDir
    Path tempDir

    ApplicationContext beanContext

    def setup() {
        beanContext = ApplicationContext.run(Environment.CLI)
    }

    def cleanup() {
        beanContext?.close()
    }

    void "test loading grails-forge-cli.yml with reloading field"() {
        given:
        String yamlContent = '''
applicationType: web
defaultPackage: org.example
reloading: devtools
sourceLanguage: groovy
features: [gorm-hibernate5, asset-pipeline-grails]
'''
        File yamlFile = new File(tempDir.toFile(), "grails-forge-cli.yml")
        yamlFile.text = yamlContent
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        config != null
        config.reloading.name() == "DEVTOOLS"
        config.applicationType.getName() == "web"
        config.sourceLanguage.name() == "GROOVY"
        config.features.contains("gorm-hibernate5")
        !config.legacy
    }

    @Unroll("test loading reloading value #reloadingValue maps to enum #expectedEnum")
    void "test all reloading enum values deserialize from YAML"(String reloadingValue, String expectedEnum) {
        given:
        String yamlContent = """
applicationType: web
defaultPackage: org.example
reloading: ${reloadingValue}
sourceLanguage: groovy
features: []
"""
        File yamlFile = new File(tempDir.toFile(), "grails-forge-cli.yml")
        yamlFile.text = yamlContent
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        config != null
        config.reloading.name() == expectedEnum

        where:
        reloadingValue | expectedEnum
        "devtools"     | "DEVTOOLS"
        "jrebel"       | "JREBEL"
        "none"         | "NONE"
    }

    void "test backwards compatibility with grails-cli.yml filename"() {
        given:
        String yamlContent = '''
applicationType: plugin
defaultPackage: org.example.plugin
reloading: jrebel
sourceLanguage: groovy
features: [gorm-hibernate5]
'''
        File yamlFile = new File(tempDir.toFile(), "grails-cli.yml")
        yamlFile.text = yamlContent
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        config != null
        config.applicationType.getName() == "plugin"
        config.reloading.name() == "JREBEL"
        config.sourceLanguage.name() == "GROOVY"
    }

    void "test grails-forge-cli.yml takes precedence over grails-cli.yml"() {
        given:
        String olderFormat = '''
applicationType: web
defaultPackage: org.example.old
reloading: none
sourceLanguage: groovy
features: []
'''
        String newerFormat = '''
applicationType: plugin
defaultPackage: org.example.new
reloading: devtools
sourceLanguage: groovy
features: []
'''
        // Create both files - grails-forge-cli.yml should take precedence
        File olderFile = new File(tempDir.toFile(), "grails-cli.yml")
        olderFile.text = olderFormat
        File newerFile = new File(tempDir.toFile(), "grails-forge-cli.yml")
        newerFile.text = newerFormat
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        config != null
        config.applicationType.getName() == "plugin"
        config.defaultPackage == "org.example.new"
        config.reloading.name() == "DEVTOOLS"
        config.sourceLanguage.name() == "GROOVY"
    }

    void "test missing reloading field uses default"() {
        given:
        String yamlContent = '''
applicationType: web
defaultPackage: org.example
sourceLanguage: groovy
features: []
'''
        File yamlFile = new File(tempDir.toFile(), "grails-forge-cli.yml")
        yamlFile.text = yamlContent
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        config != null
        config.reloading == null  // Not set in YAML, will be null
    }

    void "test minimal valid reloading configuration"() {
        given:
        String yamlContent = '''
reloading: devtools
'''
        File yamlFile = new File(tempDir.toFile(), "grails-forge-cli.yml")
        yamlFile.text = yamlContent
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        config != null
        config.reloading.name() == "DEVTOOLS"
        config.applicationType == null  // Only reloading specified
    }

    void "test case-sensitive reloading deserialization requires lowercase"() {
        given:
        String yamlContent = '''
applicationType: web
defaultPackage: org.example
reloading: DevTools
sourceLanguage: groovy
features: []
'''
        File yamlFile = new File(tempDir.toFile(), "grails-forge-cli.yml")
        yamlFile.text = yamlContent
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        // YAML deserialization is case-sensitive and requires lowercase enum names  
        // Uppercase like "DevTools" will cause a ConversionErrorException
        // This documents the YAML format requirement: reloading: devtools (lowercase)
        thrown(io.micronaut.core.convert.exceptions.ConversionErrorException)
    }

    void "test reloading field preserved through round-trip serialization"() {
        given:
        String yamlContent = '''
applicationType: web
defaultPackage: org.example
reloading: jrebel
sourceLanguage: groovy
features: [asset-pipeline-grails]
'''
        File yamlFile = new File(tempDir.toFile(), "grails-forge-cli.yml")
        yamlFile.text = yamlContent
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        config != null
        config.reloading.toString() == "jrebel"  // toString() returns lowercase
        config.reloading.getName() == "jrebel"   // getName() returns lowercase
        config.reloading.name() == "JREBEL"      // name() returns enum name in uppercase
    }

    void "test backwards compatibility with old testFramework field defaults reloading to NONE"() {
        given:
        String yamlContent = '''
applicationType: web
defaultPackage: org.example
testFramework: spock
sourceLanguage: groovy
features: []
'''
        File yamlFile = new File(tempDir.toFile(), "grails-cli.yml")
        yamlFile.text = yamlContent
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        config != null
        config.applicationType.getName() == "web"
        // Old testFramework field should default reloading to NONE
        config.reloading.name() == "NONE"
        config.sourceLanguage.name() == "GROOVY"
    }

    void "test old testFramework field is ignored if reloading field is present"() {
        given:
        String yamlContent = '''
applicationType: web
defaultPackage: org.example
testFramework: spock
reloading: devtools
sourceLanguage: groovy
features: []
'''
        File yamlFile = new File(tempDir.toFile(), "grails-forge-cli.yml")
        yamlFile.text = yamlContent
        ConsoleOutput consoleOutput = ConsoleOutput.NOOP

        when:
        CodeGenConfig config = CodeGenConfig.load(beanContext, tempDir.toFile(), consoleOutput)

        then:
        config != null
        config.applicationType.getName() == "web"
        // testFramework field should be ignored when reloading is present
        config.reloading.name() == "DEVTOOLS"
        config.sourceLanguage.name() == "GROOVY"
    }
}
