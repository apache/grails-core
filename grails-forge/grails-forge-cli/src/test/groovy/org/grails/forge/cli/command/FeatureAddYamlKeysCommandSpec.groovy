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

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import spock.lang.Unroll

/**
 * {@code feature add} for a feature whose setting the application has already, written in another
 * way than the feature writes it: the configuration is not written with the setting twice.
 */
class FeatureAddYamlKeysCommandSpec extends AbstractFeatureAddCommandSpec {

    @Unroll
    void "the setting of the feature that the application has #written is a conflict, and the configuration stays a file that reads"() {
        given:
        create(dir, CreateAppCommand, [])
        File configuration = new File(dir, 'grails-app/conf/application.yml')
        configuration.text = mine + configuration.text
        Map<String, String> before = tree(dir)

        when:
        int exit = add('grails-quartz')

        then:
        exit == 1
        out.toString().contains("    conflict grails-app/conf/application.yml: the project and the feature each have the ${what} quartz.autoStartup in one document of this file")
        tree(dir) == before
        readsWithoutARepeatedKey(configuration.text)

        where:
        written            | mine                              || what
        'in single quotes' | "'quartz.autoStartup': false\n"   || 'top-level key'
        'in double quotes' | '"quartz.autoStartup": false\n'   || 'top-level key'
        'as nested keys'   | 'quartz:\n  autoStartup: false\n' || 'setting'
    }

    private static boolean readsWithoutARepeatedKey(String text) {
        LoaderOptions options = new LoaderOptions()
        options.setAllowDuplicateKeys(false)
        return new Yaml(new SafeConstructor(options)).load(text) instanceof Map
    }
}
