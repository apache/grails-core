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
package org.apache.grails.core.cli.script

import org.apache.grails.gradle.model.cli.CommandArgumentsFile

// Stands in for the class of the command line tier of Grails that compiles and runs a script. It
// runs none: it says what its JVM was started with, where the project is said to be, and what the
// name and the arguments of the script are once the file that holds them is read as the real class
// reads it, each as the bytes of its UTF-8 form.
class ScriptCommandRunner {

    static void main(String[] args) {
        say('STARTED', args)
        println "BASE=${System.getProperty('base.dir')}"
        println "STACKTRACE=${Boolean.getBoolean('grails.full.stacktrace')}"
        println "PROBE=${System.getProperty('grails.probe.one')}"
        if (args.length == 1 && args[0].startsWith(CommandArgumentsFile.ARGUMENT)) {
            try {
                say('ARGUMENT', CommandArgumentsFile.read(new File(args[0].substring(CommandArgumentsFile.ARGUMENT.length()))) as String[])
            } catch (CommandArgumentsFile.Refused refused) {
                System.err.println("runScriptCommand: The file of arguments ${refused.message}")
                System.exit(1)
            }
        } else if (args.length > 0 && args[0] == '--') {
            say('ARGUMENT', args.toList().subList(1, args.length) as String[])
        } else {
            System.err.println('runScriptCommand: started with neither a name nor a file')
            System.exit(1)
        }
        if (args.contains('fail')) {
            System.exit(1)
        }
    }

    private static void say(String what, String[] values) {
        println "${what}S=${values.length}"
        values.eachWithIndex { String value, int index ->
            println "${what}[${index}]=<${value.getBytes('UTF-8').encodeHex()}>"
        }
    }
}
