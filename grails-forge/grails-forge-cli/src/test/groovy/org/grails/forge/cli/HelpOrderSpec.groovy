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

import groovy.json.JsonSlurper

/**
 * The document {@code help --json} writes is read and compared by tools, so what it lists is in an
 * order that is said: the options of a command by name, in every language and whatever order the
 * virtual machine gives the fields of a command in. The help of a command lists its options in one
 * order in every language too.
 */
class HelpOrderSpec extends CommandSpec implements CommandFixture {

    PrintStream originalOut
    ByteArrayOutputStream out = new ByteArrayOutputStream()
    Locale locale

    void setup() {
        originalOut = System.out
        System.setOut(new PrintStream(out, true))
        locale = Locale.getDefault()
    }

    void cleanup() {
        System.setOut(originalOut)
        Locale.setDefault(locale)
    }

    void "the options of the command line and of every command are sorted by their longest name, without its hyphens and whatever its case"() {
        when:
        int exit = ForgeCli.execute(beanContext, 'help', '--json')
        Map document = (Map) new JsonSlurper().parseText(out.toString())
        List<List<String>> listed = [keys((List<Map>) document.options)]
        collect((List<Map>) document.commands, listed)

        then:
        exit == 0
        listed.size() > 10
        listed.count { List<String> keys -> keys.size() > 5 } > 5
        listed.findAll { List<String> keys -> keys != keys.toSorted(String.CASE_INSENSITIVE_ORDER) } == []
    }

    void "the document is the same in a language whose upper and lower case are other letters"() {
        when:
        ForgeCli.execute(beanContext, 'help', '--json')
        String plain = out.toString()
        out.reset()
        Locale.setDefault(Locale.forLanguageTag('tr-TR'))
        ForgeCli.execute(beanContext, 'help', '--json')

        then:
        out.toString() == plain
    }

    void "the help of #command lists the options in the same order in a language whose upper case of i is not I"() {
        when:
        ForgeCli.execute(beanContext, command, '--help')
        String plain = out.toString()
        out.reset()
        Locale.setDefault(Locale.forLanguageTag('tr-TR'))
        ForgeCli.execute(beanContext, command, '--help')

        then:
        plain.contains('-i, --inplace')
        plain.lastIndexOf('-i, --inplace') < plain.lastIndexOf('--jdk')
        out.toString() == plain

        where:
        command << ['create-app', 'create-plugin']
    }

    // the name each option is sorted by, for a command and the commands it has
    private static void collect(List<Map> commands, List<List<String>> into) {
        for (Map command : commands) {
            if (command.options != null) {
                into << keys((List<Map>) command.options)
            }
            collect((List<Map>) (command.commands ?: []), into)
        }
    }

    private static List<String> keys(List<Map> options) {
        return options.collect { Map option -> ((List<String>) option.names).max { String name -> name.length() }.replaceFirst(/^-+/, '') }
    }
}
