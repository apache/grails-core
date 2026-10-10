/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.cli

import org.springframework.context.ConfigurableApplicationContext
import spock.lang.Specification

class ForgeCliSpec extends Specification {

    void "execute reuses a live application without closing it"() {
        given:
        ConfigurableApplicationContext context = Application.builder().run()

        when:
        int first = ForgeCli.execute(context, '--help')
        int second = ForgeCli.execute(context, 'create-app', '--help')

        then:
        first == 0
        second == 0
        context.active

        cleanup:
        context.close()
    }

    void "run starts and closes an application of its own"() {
        expect:
        ForgeCli.run('--help') == 0
    }

    void "run reports an unknown command through the exit code"() {
        given:
        PrintStream originalErr = System.err
        System.setErr(new PrintStream(new ByteArrayOutputStream()))

        expect:
        ForgeCli.run('no-such-command') != 0

        cleanup:
        System.setErr(originalErr)
    }

}
