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

    void "the generator the hosted Forge is made of is there"() {
        expect:
        Class.forName('org.apache.grails.forge.core.pack.PackLoader', false, getClass().classLoader) != null
    }
}
