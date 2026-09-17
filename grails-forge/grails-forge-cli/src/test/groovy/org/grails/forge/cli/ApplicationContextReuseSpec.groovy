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

import org.grails.forge.ForgeContexts
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import spock.lang.Specification

class ApplicationContextReuseSpec extends Specification {

    void "execute reuses a live context without closing it"() {
        given:
        AnnotationConfigApplicationContext context = ForgeContexts.create()

        when:
        int first = Application.execute(context, ['--help'] as String[])
        int second = Application.execute(context, ['create-app', '--help'] as String[])

        then:
        first == 0
        second == 0
        context.active

        cleanup:
        context.close()
    }

    void "one-shot execute still starts and closes its own context"() {
        expect:
        Application.execute(['--help'] as String[]) == 0
    }
}
