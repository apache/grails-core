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
package org.apache.grails.core.cli

import java.lang.reflect.Field
import java.util.concurrent.TimeUnit

import groovy.json.JsonOutput

import org.grails.build.parsing.DefaultCommandLine

/**
 * What the tools of the specifications of declared arguments say they were given, and how such a tool
 * is run: in a JVM of its own, as the command line starts a standalone tool.
 */
class ToolArgumentsReport {

    private static final int TIMEOUT_SECONDS = 120

    /**
     * Prints one line, {@code BOUND=} and a JSON object: {@code bound}, what the fields of the declarations
     * of the tool hold, by the first long name of an option or the label of a parameter, without those
     * that hold {@code null}; {@code remaining}, the parameters its command line has; and {@code options},
     * the options of its command line by their names.
     *
     * @param tool the tool
     * @param executionContext what it was run with
     * @return {@code true}
     */
    static boolean report(Object tool, ExecutionContext executionContext) {
        Map<String, Object> bound = new LinkedHashMap<>()
        List<Class<?>> hierarchy = []
        for (Class<?> type = tool.getClass(); type != Object; type = type.superclass) {
            hierarchy.add(0, type)
        }
        for (Class<?> type : hierarchy) {
            for (Field field : type.declaredFields) {
                ToolOption option = field.getAnnotation(ToolOption)
                ToolParameter parameter = field.getAnnotation(ToolParameter)
                if (option == null && parameter == null) {
                    continue
                }
                field.accessible = true
                Object value = field.get(tool)
                if (value != null) {
                    bound.put(option != null ? option.names().find { it.startsWith('--') } : parameter.label(), value instanceof Enum ? ((Enum) value).name() : value)
                }
            }
        }
        def commandLine = executionContext.commandLine
        Map options = commandLine instanceof DefaultCommandLine ? ((DefaultCommandLine) commandLine).undeclaredOptions : [:]
        println "BOUND=${JsonOutput.toJson([bound: bound, remaining: commandLine.remainingArgs, options: options])}"
        true
    }

    /**
     * Runs a tool as the command line runs a standalone tool.
     *
     * @param directory the directory of the project, which the JVM is started in
     * @param classes the classes of the tools, with their registrations and descriptors
     * @param arguments what the JVM is given
     * @return {@code ended}, {@code exit}, {@code out} and {@code err}
     */
    static Map<String, Object> run(File directory, File classes, List<String> arguments) {
        List<String> command = [new File(System.getProperty('java.home'), 'bin/java').path,
                                '-cp', classes.path + File.pathSeparator + System.getProperty('java.class.path'),
                                "-Dbase.dir=${directory.path}".toString(), StandaloneToolRunner.name]
        command.addAll(arguments)
        File out = new File(directory, 'out.txt')
        File err = new File(directory, 'err.txt')
        Process process = new ProcessBuilder(command).directory(directory).redirectOutput(out).redirectError(err).start()
        boolean ended = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        if (!ended) {
            process.destroyForcibly().waitFor()
        }
        [ended: ended, exit: ended ? process.exitValue() : null, out: out.getText('UTF-8'), err: err.getText('UTF-8')]
    }
}
