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

package org.grails.forge.util

import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class VersionInfoSpec extends Specification {

    @TempDir
    Path versionsDir

    void "test get dependency version"() {
        given:
        def version = VersionInfo.getDependencyVersion("grails")

        expect:
        version.key == 'grails.version'
        version.value
    }

    @Unroll
    void "test version #version reports end of support"() {
        given:
        Class<?> versionInfo = loadVersionInfo(version, '2026-10-31')

        expect:
        versionInfo.getMethod('getEndOfSupport').invoke(null) == Optional.of(LocalDate.of(2026, 10, 31))
        !versionInfo.getMethod('getDependencyVersions').invoke(null).containsKey('grails.endOfSupport')
        versionInfo.getMethod('getDependencyVersions').invoke(null)['grails.version'] == version

        where:
        version << ['7.0.17', '7.0.18-SNAPSHOT']
    }

    @Unroll
    void "test no end of support is reported for version #version and date #endOfSupport"() {
        given:
        Class<?> versionInfo = loadVersionInfo(version, endOfSupport)

        expect:
        versionInfo.getMethod('getEndOfSupport').invoke(null) == Optional.empty()
        !versionInfo.getMethod('getDependencyVersions').invoke(null).containsKey('grails.endOfSupport')

        where:
        version  | endOfSupport
        '7.0.17' | null
        '7.0.17' | '20207-07-31'
    }

    void "test the current build reports end of support"() {
        expect:
        VersionInfo.getEndOfSupport().present
        !VersionInfo.getDependencyVersions().containsKey('grails.endOfSupport')
    }

    private Class<?> loadVersionInfo(String version, String endOfSupport) {
        String properties = "grails.version=${version}\n"
        if (endOfSupport != null) {
            properties += "grails.endOfSupport=${endOfSupport}\n"
        }
        Files.writeString(versionsDir.resolve('grails-versions.properties'), properties)
        URL[] urls = [
                versionsDir.toUri().toURL(),
                VersionInfo.protectionDomain.codeSource.location,
                GroovyObject.protectionDomain.codeSource.location
        ]
        new URLClassLoader(urls, ClassLoader.platformClassLoader).loadClass(VersionInfo.name)
    }
}
