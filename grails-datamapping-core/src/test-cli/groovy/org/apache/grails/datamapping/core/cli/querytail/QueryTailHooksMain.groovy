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
package org.apache.grails.datamapping.core.cli.querytail

import java.lang.reflect.Field

import org.apache.grails.core.cli.ExecutionContext
import org.grails.build.parsing.CommandLineParser

/**
 * Runs the query-tail command a number of times in this JVM, as a JVM that runs commands one after another
 * does, and prints how many shutdown hooks the runs left registered. The JVM is started with
 * {@code --add-opens java.base/java.lang=ALL-UNNAMED}, which the registry of the hooks is read with.
 */
class QueryTailHooksMain {

    static void main(String[] args) {
        Field field = Class.forName('java.lang.ApplicationShutdownHooks').getDeclaredField('hooks')
        field.accessible = true
        Map<?, ?> hooks = (Map<?, ?>) field.get(null)
        int before = hooks.size()
        int times = Integer.parseInt(args[1])
        times.times {
            new QueryTailTool().handle(new ExecutionContext(new CommandLineParser().parse('query-tail', '--once', "--file=${args[0]}".toString())))
        }
        System.out.println("hooks left: ${hooks.size() - before}")
    }
}
