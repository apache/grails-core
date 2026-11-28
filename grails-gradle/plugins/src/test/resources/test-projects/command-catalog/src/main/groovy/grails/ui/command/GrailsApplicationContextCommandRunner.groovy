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
package grails.ui.command

import org.apache.grails.gradle.model.cli.CommandArgumentsFile

// Stands in for the class of grails-console that runs a tool. It says what its JVM was started
// with, and what the arguments of the tool are once it has done what the real class does first
// with them (a build can give them in a file), each as the bytes of its UTF-8 form, so that nothing
// a terminal or a log does to a character can make two arguments look alike. It says its class path
// as well, one entry a line.
class GrailsApplicationContextCommandRunner {

    static void main(String[] args) {
        System.getProperty('java.class.path').split(File.pathSeparator).each { String entry ->
            println "CLASSPATH_ENTRY=${entry}"
        }
        say('STARTED', args)
        String[] arguments = null
        try {
            arguments = CommandArgumentsFile.expand(args)
        } catch (CommandArgumentsFile.Refused refused) {
            System.err.println("runTool: The file of arguments ${refused.message}")
            System.exit(1)
        }
        say('ARGUMENT', arguments)
        // a tool that fails: the exit code the build is to report
        if (arguments.length > 0 && arguments[0] == 'fail') {
            System.exit(3)
        }
    }

    private static void say(String what, String[] values) {
        println "${what}S=${values.length}"
        values.eachWithIndex { String value, int index ->
            println "${what}[${index}]=<${value.getBytes('UTF-8').encodeHex()}>"
        }
    }
}
