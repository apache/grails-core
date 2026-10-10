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

package org.grails.forge.cli.command

import org.grails.forge.cli.CliRunner
import org.grails.forge.cli.ForgeCommand
import org.grails.forge.cli.CommandFixture
import org.grails.forge.cli.CommandSpec
import org.grails.forge.util.VersionInfo

class ApplicationCommandSpec extends CommandSpec implements CommandFixture {

    void "print version info via: grails #args"() {
        given:
        ByteArrayOutputStream out = new ByteArrayOutputStream()
        System.setOut(new PrintStream(out))

        when:
        CliRunner.run(ForgeCommand, beanContext, args)

        then:
        noExceptionThrown()
        out.toString().contains("Grails Version: " + VersionInfo.getGrailsVersion())
        out.toString().contains("JVM Version: " + Objects.requireNonNullElse(System.getProperty("java.version"), "<a valid java.home was not found>" ))

        where:
        args        | _
        "--version" | _
        "-V"        | _
    }
}
