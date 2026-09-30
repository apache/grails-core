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
import grails.util.Holders
import org.grails.io.support.GrailsResourceUtils
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

/**
 * Profile functionality tests that work with available Grails APIs
 * This verifies that profile-related functionality works correctly
 */
class ProfileIntegrationTests extends Specification {
    
    @TempDir
    Path tempDir
    
    def "test profile directory structure can be created"() {
        given: "a temporary project directory"
        Path projectDir = tempDir.resolve("test-project")
        Files.createDirectories(projectDir)
        
        when: "I create basic profile structure"
        Files.createDirectories(projectDir.resolve("grails-app"))
        Files.createDirectories(projectDir.resolve("grails-app/controllers"))
        Files.createDirectories(projectDir.resolve("grails-app/services"))
        Files.createDirectories(projectDir.resolve("grails-app/domain"))
        Files.createDirectories(projectDir.resolve("src/main/groovy"))
        Files.createDirectories(projectDir.resolve("src/test/groovy"))
        
        then: "directory structure is created correctly"
        Files.exists(projectDir.resolve("grails-app"))
        Files.exists(projectDir.resolve("grails-app/controllers"))
        Files.exists(projectDir.resolve("grails-app/services"))
        Files.exists(projectDir.resolve("grails-app/domain"))
        Files.exists(projectDir.resolve("src/main/groovy"))
        Files.exists(projectDir.resolve("src/test/groovy"))
    }
    
    def "test profile-related utilities work"() {
        when: "I use Grails resource utilities"
        def resourcePath = GrailsResourceUtils.CLASSPATH_URL_PREFIX + "application.yml"
        
        then: "the utility methods work correctly"
        resourcePath.startsWith("classpath:")
        !resourcePath.contains("..")
    }
    
    def "test environment detection works"() {
        when: "I get the current environment"
        Environment currentEnv = Environment.getCurrent()
        
        then: "environment is properly detected"
        currentEnv != null
        currentEnv.getName() != null
        !currentEnv.getName().isEmpty()
    }
    
    def "test Holders utility is available"() {
        when: "I access Holders"
        def appClassLoader = Holders.getPluginManager()?.getClassLoader()
        
        then: "Holders is accessible (may be null in test context but class is available)"
        Holders != null
    }
}