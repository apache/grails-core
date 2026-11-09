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
package legacy.g7.commands

import groovy.transform.CompileStatic

import grails.dev.commands.ApplicationCommand
import grails.dev.commands.ExecutionContext

/**
 * Precompiled against Grails 7 / Groovy 4 so the monorepo can prove that an unchanged
 * published Grails 7 application-command binary still links and runs through the Grails 8
 * compatibility bridge.
 */
@CompileStatic
class HelloG7PrecompiledCommand implements ApplicationCommand {

    @Override
    String getName() {
        'hello-g7-precompiled'
    }

    @Override
    String getDescription() {
        'Runs a Grails 7 / Groovy 4 precompiled application command'
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        new File(executionContext.baseDir, 'hello-g7-precompiled.txt').text = "G7-CONTEXT-${applicationContext != null}"
        true
    }
}
