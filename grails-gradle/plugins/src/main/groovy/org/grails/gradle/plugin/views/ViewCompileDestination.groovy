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
package org.grails.gradle.plugin.views

import java.nio.file.Path

import groovy.transform.CompileStatic

import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.DeleteSpec
import org.gradle.api.file.FileSystemOperations

/**
 * The directory a view compile task compiles into.
 *
 * <p>A compiler leaves alone what it finds there - the page compiler a page whose class is newer than
 * its source, both the class of a view that is gone - so a destination that kept what an earlier
 * execution compiled would carry views of another environment, classpath or configuration, or views
 * that no longer exist, into the archive and the build cache. It is emptied before each execution,
 * and so holds exactly what that execution compiled, at the cost of compiling every view each time
 * the task runs. It has therefore to be a directory of the task's own, within the build directory,
 * and one the compiler's classpath does not hold: a build that points a task at the build directory
 * itself, outside it, or at a directory of classes the views are compiled against is refused. A
 * directory shared with the output of another task is emptied of that output too, which Gradle
 * reports as overlapping outputs. A destination that is itself a symbolic link is not one Gradle
 * keeps: it removes a link at a declared output path as a stale output before the task runs.</p>
 *
 * @since 7.0.18
 */
@CompileStatic
final class ViewCompileDestination {

    private ViewCompileDestination() {
    }

    /**
     * Empties {@code destination}, with Gradle's deleter, which removes a symbolic link beneath it
     * rather than what the link points at, and leaves the directory there to compile into.
     *
     * @param operations the build's file operations
     * @param destination the directory
     * @param buildDirectory the build directory, which the destination has to be a directory within
     * @param classpath the compiler's classpath, which the destination may not hold or be held by
     * @param task the path of the task, for the message
     * @throws InvalidUserDataException where the destination is not a directory of the task's own
     */
    static void empty(FileSystemOperations operations, File destination, File buildDirectory, Collection<File> classpath, String task) {
        Path target = destination.canonicalFile.toPath()
        Path build = buildDirectory.canonicalFile.toPath()
        if (target == build || !target.startsWith(build)) {
            throw new InvalidUserDataException("The destination of ${task}, ${destination}, is not a directory within the build " +
                    "directory ${buildDirectory}. It is emptied before each compilation, so it has to be a directory of the " +
                    "task's own there")
        }
        File held = classpath.find { File entry ->
            Path path = entry.canonicalFile.toPath()
            path.startsWith(target) || target.startsWith(path)
        }
        if (held != null) {
            throw new InvalidUserDataException("The destination of ${task}, ${destination}, holds or is held by ${held}, which " +
                    'is on the classpath the views are compiled against. It is emptied before each compilation, so it has to ' +
                    "be a directory of the task's own")
        }
        operations.delete { DeleteSpec spec -> spec.delete(destination) }
        destination.mkdirs()
    }
}
