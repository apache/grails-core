/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.plugins.databasemigration.liquibase

import liquibase.resource.PathHandler
import liquibase.resource.ResourceAccessor
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class EmbeddedJarPathHandlerSpec extends Specification {

    EmbeddedJarPathHandler handler = new EmbeddedJarPathHandler()

    @TempDir
    Path tempDir

    def 'test getPriority'() {
        expect:
        handler.getPriority(root) == expectedPriority

        where:
        root                                                 | expectedPriority
        'jar:file:/path/to/outer.jar!/inner.jar!/'           | PathHandler.PRIORITY_SPECIALIZED
        'jar:file:/path/to/outer.jar!/nested/inner.jar!/'    | PathHandler.PRIORITY_SPECIALIZED
        'jar:file:/path/to/outer.jar!/'                      | PathHandler.PRIORITY_NOT_APPLICABLE
        'file:/path/to/dir/'                                 | PathHandler.PRIORITY_NOT_APPLICABLE
        'jar:file:/path/to/outer.jar!/some/path'             | PathHandler.PRIORITY_NOT_APPLICABLE
    }

    def 'getResourceAccessor handles nested jars'() {
        given: 'A physical jar file on disk'
        Path outerJar = tempDir.resolve('outer.jar')
        createJarWithInnerJar(outerJar, 'inner.jar')

        String root = "jar:file:${outerJar.toAbsolutePath()}!/inner.jar!/"

        when:
        ResourceAccessor accessor = handler.getResourceAccessor(root)

        then:
        accessor instanceof EmbeddedJarResourceAccessor
        accessor.describeLocations().any { it.contains('inner.jar') }

        cleanup:
        accessor?.close()
    }

    def 'getResourceAccessor throws IllegalArgumentException for invalid paths'() {
        when:
        handler.getResourceAccessor('jar:file:/non/existent.jar!/inner.jar!/')

        then:
        thrown(IllegalArgumentException)
    }

    /**
     * Helper to create a JAR file that contains another JAR file.
     */
    private void createJarWithInnerJar(Path outerJarPath, String innerJarName) {
        // 1. Create inner jar content in memory
        ByteArrayOutputStream innerJarByteStream = new ByteArrayOutputStream()
        JarOutputStream innerJarStream = new JarOutputStream(innerJarByteStream)
        innerJarStream.putNextEntry(new JarEntry('test.txt'))
        innerJarStream.write('hello'.bytes)
        innerJarStream.closeEntry()
        innerJarStream.close()

        // 2. Create outer jar on disk
        JarOutputStream outerJarStream = new JarOutputStream(Files.newOutputStream(outerJarPath))
        outerJarStream.putNextEntry(new JarEntry(innerJarName))
        outerJarStream.write(innerJarByteStream.toByteArray())
        outerJarStream.closeEntry()
        outerJarStream.close()
    }
}
