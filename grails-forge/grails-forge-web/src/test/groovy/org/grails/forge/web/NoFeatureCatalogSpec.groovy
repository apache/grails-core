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
package org.grails.forge.web

import spock.lang.Specification
import spock.lang.Unroll

/**
 * The hosted Forge offers the features of Grails only. What takes the feature pack of a plugin from a
 * repository, and what reads a feature catalog, is code of the command line, and is not among what the
 * hosted Forge is made of: it cannot fetch a pack or read a catalog whatever it is asked.
 */
class NoFeatureCatalogSpec extends Specification {

    /** The sources of the command line, beside this module: the names of its classes are taken from them, and not written here. */
    static final File CLI_SOURCES = new File('../grails-forge-cli/src/main/groovy')

    /** The packages the command line is in, under which the hosted Forge has nothing. */
    static final List<String> CLI_PACKAGES = ['org/apache/grails/forge/cli', 'org/grails/forge/cli']

    @Unroll
    void "#type is not on the class path of the hosted Forge"() {
        when:
        Class.forName(type, false, getClass().classLoader)

        then:
        thrown(ClassNotFoundException)

        where:
        type << ['org.apache.grails.forge.cli.pack.PackCatalogs', 'org.apache.grails.forge.cli.pack.PackCatalog',
                 'org.apache.grails.forge.cli.pack.PackArtifacts', 'org.grails.forge.cli.command.CreateCommand']
    }

    void "the classes named above are classes the command line has, so that one that is renamed is noticed"() {
        expect:
        ['org/apache/grails/forge/cli/pack/PackCatalogs.groovy', 'org/apache/grails/forge/cli/pack/PackCatalog.groovy',
         'org/apache/grails/forge/cli/pack/PackArtifacts.groovy', 'org/grails/forge/cli/command/CreateCommand.groovy'].every { String source ->
            new File(CLI_SOURCES, source).isFile()
        }
    }

    void "no class the command line is made of is on the class path of the hosted Forge, by the names its sources give them"() {
        given: 'every source file of the command line, as the class of its name'
        List<String> types = []
        CLI_SOURCES.eachFileRecurse { File file ->
            if (file.isFile() && (file.name.endsWith('.groovy') || file.name.endsWith('.java'))) {
                String path = CLI_SOURCES.toPath().relativize(file.toPath()).join('/')
                types << path.substring(0, path.lastIndexOf('.')).replace('/', '.')
            }
        }

        expect: 'the sources are where they are looked for, and all of them are in the packages of the command line'
        types.size() > 50
        types.every { String type -> CLI_PACKAGES.any { String prefix -> type.startsWith(prefix.replace('/', '.') + '.') } }

        and: 'none of them can be loaded'
        types.findAll { String type ->
            try {
                Class.forName(type, false, getClass().classLoader)
                return true
            } catch (ClassNotFoundException | LinkageError ignored) {
                return false
            }
        } == []
    }

    void "nothing at all is under the packages of the command line on the class path of the hosted Forge"() {
        expect:
        CLI_PACKAGES.collectMany { String prefix -> getClass().classLoader.getResources(prefix).toList() + getClass().classLoader.getResources(prefix + '/').toList() } == []
    }

    void "the generator the hosted Forge is made of is there"() {
        expect:
        Class.forName('org.apache.grails.forge.core.pack.PackLoader', false, getClass().classLoader) != null
    }
}
