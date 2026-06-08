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

package grails.plugin.springsecurity

import grails.dev.commands.ExecutionContext
import org.grails.build.parsing.CommandLine

trait CommandLineHelper {

    static final boolean SUCCESS = true
    static final boolean FAILURE = false

    abstract ExecutionContext getExecutionContext()

    boolean isFlagPresent(String name) {
        final CommandLine commandLine = executionContext.commandLine
        if (commandLine.hasOption(name)) {
            return commandLine.optionValue(name) ? true : false
        } else {
            def value = commandLine?.undeclaredOptions?.get(name)
            return value ? true : false
        }
    }

    String flagValue(String name) {
        final CommandLine commandLine = executionContext.commandLine
        if (commandLine.hasOption(name)) {
            return commandLine.optionValue(name)
        } else {
            def value = commandLine?.undeclaredOptions?.get(name)
            return value
        }
    }

}