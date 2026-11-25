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
package org.grails.forge.cli

/**
 * The CLI as the program it is: {@link Application#main} in a virtual machine of its own, made of the
 * classes this build compiled, with the output and the environment the specification gives it. What a
 * specification cannot see in its own virtual machine is seen there: the output the process was
 * started with, the end of the virtual machine, and what the program does when nothing is replaced.
 */
class ForkedCli {

    /**
     * @return whether the class path of this virtual machine is one another can be started with
     */
    static boolean available() {
        String location = new File(Application.protectionDomain.codeSource.location.toURI()).path
        return System.getProperty('java.class.path').split(File.pathSeparator).contains(location)
    }

    /**
     * @param directory the directory the CLI is run in
     * @param jvm options of the virtual machine
     * @param args the command line
     * @return what starts it; the environment is that of this process, and the home that of this
     * virtual machine unless an option names another
     */
    static ProcessBuilder builder(File directory, List<String> jvm, String... args) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path]
        // The home of the specifications, which the build gives this virtual machine and another does not
        // have by itself: the program would keep what it keeps under the home of whoever runs the build.
        // An option of the specification that names another home comes after this one, and counts.
        command.add("-Duser.home=${System.getProperty('user.home')}".toString())
        command.addAll(jvm)
        // Not an argument and not a variable of the environment: the class path of a build is longer than
        // either can be on Windows, where each ends at 32767 characters. Java reads it from a file, in
        // which a backslash between quotes is written twice.
        File classPath = File.createTempFile('grails-cli-class-path', '.txt')
        classPath.deleteOnExit()
        classPath.setText('-cp\n"' + System.getProperty('java.class.path').replace('\\', '\\\\') + '"\n', 'UTF-8')
        command.add('@' + classPath.path)
        command.add(Application.name)
        command.addAll(args.toList())
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory)
        builder.environment().remove('CLASSPATH')
        return builder
    }
}
