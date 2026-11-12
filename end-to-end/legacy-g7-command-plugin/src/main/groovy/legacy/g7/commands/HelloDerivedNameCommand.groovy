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

import grails.dev.commands.GrailsApplicationCommand

/**
 * Mirrors the command shape {@code create-command} generated on Grails 7: it implements the trait
 * and declares neither {@code getName()} nor {@code getDescription()}, so the registered command
 * name is whatever the trait derives from the class name. Reading {@code name} inside
 * {@code handle()} exercises that derivation from the precompiled Groovy 4 binary rather than
 * from the recompiled trait alone.
 */
class HelloDerivedNameCommand implements GrailsApplicationCommand {

    @Override
    boolean handle() {
        File outputDirectory = file('build/hello-derived-name')
        mkdir(outputDirectory)
        render("G7-DERIVED-NAME-${name}", new File(outputDirectory, 'rendered.txt'))
        true
    }
}
