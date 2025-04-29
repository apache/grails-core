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

package org.grails.plugins.databasemigration.liquibase

import groovy.transform.CompileStatic
import liquibase.resource.AbstractPathResourceAccessor
import liquibase.resource.PathResource
import liquibase.resource.Resource
import liquibase.resource.ResourceAccessor
import liquibase.resource.ZipPathHandler

import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths

@CompileStatic
class EmbeddedJarPathHandler extends ZipPathHandler {
    @Override
    int getPriority(String root) {
        if (root.startsWith("jar:file:") && root.endsWith("!/")) { //only can handle `jar:` urls for the entire jar
            if (parseJarPath(root).contains('!')) {
                return PRIORITY_SPECIALIZED
            }
        }
        PRIORITY_NOT_APPLICABLE
    }

    private String parseJarPath(String root) {
        root.substring(9, root.lastIndexOf("!"))
    }

    @Override
    ResourceAccessor getResourceAccessor(String root) throws FileNotFoundException {
        String jarPath = parseJarPath(root)
        new EmbeddedJarResourceAccessor(jarPath.split('!').toList())
    }
}

@CompileStatic
class EmbeddedJarResourceAccessor extends AbstractPathResourceAccessor {
    private FileSystem fileSystem

    EmbeddedJarResourceAccessor(List<String> jarPaths) {
        try {
            Path firstPath = Paths.get(jarPaths.pop())
            fileSystem = FileSystems.newFileSystem(firstPath, null as ClassLoader)

            while(jarPaths) {
                Path innerPath = fileSystem.getPath(jarPaths.pop())
                fileSystem = FileSystems.newFileSystem(innerPath, null as ClassLoader)
            }
        } catch (e) {
            throw new IllegalArgumentException(e.getMessage(), e)
        }
    }

    @Override
    void close() throws Exception {
        //can't close the filesystem because they often get reused and/or are being used by other things
    }

    @Override
    protected Path getRootPath() {
        return this.fileSystem.getPath("/")
    }

    @Override
    protected Resource createResource(Path file, String pathToAdd) {
        return new PathResource(pathToAdd, file)
    }

    @Override
    List<String> describeLocations() {
        return Collections.singletonList(fileSystem.toString())
    }
}
