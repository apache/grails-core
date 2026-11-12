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

package org.apache.grails.profiles

import grails.util.Environment
import grails.util.GrailsUtil
import org.grails.io.support.GrailsResourceUtils
import spock.lang.Specification
import spock.lang.Stepwise
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Profile End-to-End Tests
 * These tests verify the profile testing infrastructure is working correctly
 * and demonstrates the capabilities that will be expanded upon
 */
@Stepwise
class ProfileEndToEndTests extends Specification {
    
    @TempDir
    Path tempDir
    
    def "test profile infrastructure is initialized"() {
        expect: "basic profile testing infrastructure is available"
        tempDir != null
        Files.exists(tempDir)
    }
    
    def "test profile directory structure creation"() {
        given: "a project name"
        String projectName = "test-profile-app"
        Path projectDir = tempDir.resolve(projectName)
        
        when: "I create the basic profile structure"
        createProfileStructure(projectDir)
        
        then: "all required directories exist"
        Files.exists(projectDir.resolve("grails-app"))
        Files.exists(projectDir.resolve("grails-app/controllers"))
        Files.exists(projectDir.resolve("grails-app/services"))
        Files.exists(projectDir.resolve("grails-app/domain"))
        Files.exists(projectDir.resolve("grails-app/views"))
        Files.exists(projectDir.resolve("src/main/groovy"))
        Files.exists(projectDir.resolve("src/test/groovy"))
        Files.exists(projectDir.resolve("src/integration-test/groovy"))
    }
    
    def "test profile configuration files can be created"() {
        given: "a project directory"
        String projectName = "config-test-app"
        Path projectDir = tempDir.resolve(projectName)
        createProfileStructure(projectDir)
        
        when: "I create basic configuration files"
        createBasicConfigFiles(projectDir)
        
        then: "configuration files exist"
        Files.exists(projectDir.resolve("grails-app/conf/application.yml"))
        Files.exists(projectDir.resolve("build.gradle"))
        Files.exists(projectDir.resolve("grails-app/init/BootStrap.groovy"))
    }
    
    def "test environment detection in profile context"() {
        when: "I check the current environment"
        Environment currentEnv = Environment.getCurrent()
        
        then: "environment is properly detected"
        currentEnv != null
        currentEnv != Environment.PRODUCTION
    }
    
    def "test Grails utilities are available"() {
        when: "I use Grails utility methods"
        String version = GrailsUtil.getGrailsVersion()
        boolean isDevelopment = Environment.isDevelopmentMode()
        
        then: "utilities work correctly"
        version != null
        version.startsWith("7.")
    }
    
    private void createProfileStructure(Path projectDir) {
        Files.createDirectories(projectDir.resolve("grails-app"))
        Files.createDirectories(projectDir.resolve("grails-app/controllers"))
        Files.createDirectories(projectDir.resolve("grails-app/services"))
        Files.createDirectories(projectDir.resolve("grails-app/domain"))
        Files.createDirectories(projectDir.resolve("grails-app/views"))
        Files.createDirectories(projectDir.resolve("src/main/groovy"))
        Files.createDirectories(projectDir.resolve("src/test/groovy"))
        Files.createDirectories(projectDir.resolve("src/integration-test/groovy"))
        Files.createDirectories(projectDir.resolve("grails-app/conf"))
        Files.createDirectories(projectDir.resolve("grails-app/init"))
    }
    
    private void createBasicConfigFiles(Path projectDir) {
        // Create application.yml
        Path appConfig = projectDir.resolve("grails-app/conf/application.yml")
        Files.write(appConfig, [
            "grails:",
            "    profile: web",
            "    application:",
            "        name: test-profile-app"
        ])
        
        // Create BootStrap.groovy
        Path bootstrap = projectDir.resolve("grails-app/init/BootStrap.groovy")
        Files.write(bootstrap, [
            "class BootStrap {",
            "    def init = { servletContext ->",
            "        // Initialization code",
            "    }",
            "    def destroy = {",
            "        // Cleanup code",
            "    }",
            "}"
        ])
        
        // Create basic build.gradle
        Path buildGradle = projectDir.resolve("build.gradle")
        Files.write(buildGradle, [
            "plugins {",
            "    id 'org.grails.grails-web'",
            "    id 'org.grails.grails-gsp'",
            "}",
            "",
            "dependencies {",
            "    implementation 'org.grails.plugins:gorm-hibernate5'",
            "    implementation 'org.grails.plugins:cache'",
            "    implementation 'org.grails.plugins:async'",
            "}"
        ])
    }
}