/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.grails.cli

import groovy.transform.CompileStatic

import java.lang.reflect.Method

/**
 * Class will delegate between grails-shell-cli & grails-forge-cli
 */
@CompileStatic
class DelegatingShellApplication {
    static void main(String[] args) {
        // Ideally, we'd use pico cli to delegate between them and get autocomplete, but we can't easily do that because
        // grails-forge-cli does not implement picocli compliant commands (they can't be nested because they're beans that micronaut expects to create)

        Tuple2<Boolean, String[]> info = determineForge(args)

        ClassLoader classLoader = DelegatingShellApplication.classLoader
        Thread.currentThread().contextClassLoader = classLoader

        // initialize reflectively so that the static initializes are called instead of being called at compile time
        String entryName = info.v1 ? 'org.grails.forge.cli.Application' : 'org.grails.cli.GrailsCli'
        Class<?> entry = Class.forName(entryName, true, classLoader)

        Method mainMethod = entry.getMethod('main', String[].class)
        mainMethod.invoke(null, (Object) info.v2)
    }

    private static void validateShellType(String shellType) {
        if (shellType != 'shell' && shellType != 'forge') {
            System.err.println("Argument of -t, --type must be either 'shell' or 'forge'")
            System.exit(1)
        }
    }

    static Tuple2<Boolean, String[]> determineForge(String[] args) {
        String preferredShell = System.getenv('GRAILS_PREFERRED_SHELL')?.trim()?.toLowerCase() ?: 'shell'
        validateShellType(preferredShell)

        if(!args) {
            return new Tuple2<>(preferredShell == 'forge', args)
        }

        List<String> listArgs = args.toList()
        String first = listArgs.first().trim().toLowerCase()
        if(first != '-t' && first != '--type') {
            return new Tuple2<>(preferredShell == 'forge', args)
        }

        if(listArgs.size() == 1) {
            System.err.println("Missing argument for -t, --type; possible values are 'shell' or 'forge'")
            System.exit(1)
        }

        String shellType = listArgs[1].trim().toLowerCase()
        validateShellType(shellType)

        new Tuple2<>(shellType == 'forge', listArgs.drop(2).toArray(new String[0]))
    }
}
