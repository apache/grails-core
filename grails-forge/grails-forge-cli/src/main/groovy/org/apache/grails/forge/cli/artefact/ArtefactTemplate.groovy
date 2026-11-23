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
package org.apache.grails.forge.cli.artefact

import groovy.transform.CompileStatic

/**
 * The templates the code generation commands write the classes of an application from. Each has the
 * location and the syntax the template of the same purpose had for the commands of a profile, so a
 * template written for those is read here as it is.
 *
 * @since 9.0
 */
@CompileStatic
enum ArtefactTemplate {

    CONTROLLER('artifacts/Controller.groovy', Syntax.TOKENS),
    CONTROLLER_TEST('testing/Controller.groovy', Syntax.TOKENS),
    SERVICE('artifacts/Service.groovy', Syntax.TOKENS),
    SERVICE_TEST('testing/Service.groovy', Syntax.TOKENS),
    DOMAIN_CLASS('artifacts/DomainClass.groovy', Syntax.TOKENS),
    DOMAIN_CLASS_TEST('testing/DomainClass.groovy', Syntax.TOKENS),
    TAG_LIB('artifacts/TagLib.groovy', Syntax.TOKENS),
    TAG_LIB_TEST('testing/TagLib.groovy', Syntax.TOKENS),
    INTERCEPTOR('artifacts/Interceptor.groovy', Syntax.TOKENS),
    INTERCEPTOR_TEST('testing/Interceptor.groovy', Syntax.TOKENS),
    // the template of the Quartz plugin's script, which wrote its names as the template engine of Groovy reads them
    JOB('Job.groovy', Syntax.PLACEHOLDERS),
    UNIT_TEST('testing/Generic.groovy', Syntax.TOKENS),
    INTEGRATION_TEST('testing/Integration.groovy', Syntax.TOKENS),
    FUNCTIONAL_TEST('testing/Functional.groovy', Syntax.TOKENS),
    // the template of the Geb plugin's script, where it was and written as it was; what this program has for it
    // depends on where the browser of the project runs, so there are two, and a project has one of its own for both
    GEB_CONTAINER_FUNCTIONAL_TEST('FunctionalSpec.groovy', Syntax.PLACEHOLDERS, 'geb/ContainerGebSpec.groovy'),
    GEB_FUNCTIONAL_TEST('FunctionalSpec.groovy', Syntax.PLACEHOLDERS, 'geb/GebSpec.groovy'),
    RESTFUL_CONTROLLER('artifacts/RestfulController.groovy', Syntax.TOKENS),
    RESOURCE('artifacts/Resource.groovy', Syntax.TOKENS),
    RESOURCE_TEST('testing/Resource.groovy', Syntax.TOKENS),
    COMMAND('artifacts/Command.groovy', Syntax.TOKENS)

    /**
     * How a template says where the names of the class go.
     */
    static enum Syntax {

        /**
         * Text in which {@code @artifact.name@} and the other tokens are replaced. Nothing of it is run.
         */
        TOKENS,

        /**
         * Text in which {@code ${className}} and the other names in braces are replaced, and
         * {@code \$} is a dollar sign. Nothing of it is run: code, and an expression that is more
         * than a name, is refused.
         */
        PLACEHOLDERS
    }

    /** Where the template is below {@code src/main/templates} of a project */
    final String location
    final Syntax syntax
    /** Where the template of this program is below {@code META-INF/grails-forge-cli/templates}: at the same place, for all but the templates that share one in a project */
    final String resource

    private ArtefactTemplate(String location, Syntax syntax) {
        this(location, syntax, location)
    }

    private ArtefactTemplate(String location, Syntax syntax, String resource) {
        this.location = location
        this.syntax = syntax
        this.resource = resource
    }
}
