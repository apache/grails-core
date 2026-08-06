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
package org.apache.grails.core.cli.compiler

import org.codehaus.groovy.ast.ClassHelper

import org.apache.grails.core.cli.ApplicationCommandTargetAware
import spock.lang.Specification
import spock.lang.Unroll

/**
 * Commands shipped in a companion cli artifact compile from the {@code cli} source set
 * ({@code src/cli/groovy}), which is not a standard project-source location — the transformation
 * must still register them in {@code META-INF/grails-cli.factories}.
 */
class CommandFactoriesTransformationSpec extends Specification {

    @Unroll
    def "recognises #path as a cli source: #expected"() {
        expect:
        CommandFactoriesTransformation.isCliSource(new URI("file://${path}").toURL()) == expected

        where:
        path                                                                    || expected
        '/work/my-plugin/src/cli/groovy/com/example/cli/AuditCommand.groovy'    || true
        '/work/my-plugin/src/cli/java/com/example/cli/AuditCommand.java'        || true
        '/work/my-plugin/src/main/groovy/com/example/RuntimeClass.groovy'       || false
        '/work/my-plugin/src/cli/resources/META-INF/grails-cli.factories'       || false
        '/work/my-plugin/grails-app/commands/example/BackupCommand.groovy'      || false
        '/work/clipboard/src/climate/groovy/com/example/NotCliCommand.groovy'   || false
    }

    def "does not register target-aware command adapters as command factories"() {
        expect:
        !CommandFactoriesTransformation.shouldRegisterCommand(ClassHelper.make(TargetAwareTestAdapter))
    }
}

class TargetAwareTestAdapter implements ApplicationCommandTargetAware {

    @Override
    Object getTarget() {
        null
    }
}
