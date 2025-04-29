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
package grails.dev.commands

import grails.util.BuildSettings
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import org.grails.build.parsing.CommandLine

/**
 * A context command to pass to {@link ApplicationCommand} instances
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
@Canonical
class ExecutionContext {
    /**
     * The command line instance
     */
    CommandLine commandLine
    /**
     * The base directory for the project
     */
    final File baseDir = BuildSettings.BASE_DIR
    /**
     * The classes directory of the project
     */
    final File classesDir = BuildSettings.CLASSES_DIR
    /**
     * The target directory of the project
     */
    final File targetDir = BuildSettings.TARGET_DIR
}
