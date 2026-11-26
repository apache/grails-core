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

package org.apache.grails.security.acl.cli

import groovy.transform.CompileStatic

import org.apache.grails.core.cli.StandaloneTool
import org.apache.grails.core.cli.ToolSupport

/**
 * Copies the ACL domain classes of the plugin into the project, where they can be customized, for example to
 * configure the Hibernate 2nd-level cache. The package and the names cannot be chosen: the classes of the plugin
 * refer to them, and a class of the application replaces the class of the plugin that has its name.
 *
 * <p>Usage: {@code grails s2-create-acl-domains}, or {@code ./gradlew runTool -Pargs="s2-create-acl-domains"}.
 * The command takes no arguments. A class that is already in the project is kept as it is.</p>
 *
 * @since 9.0
 */
@CompileStatic
class S2CreateAclDomainsTool implements StandaloneTool, ToolSupport {

    private static final List<String> DOMAIN_CLASSES = ['AclClass', 'AclEntry', 'AclObjectIdentity', 'AclSid']

    private static final String DOMAIN_DIRECTORY = 'grails-app/domain/grails/plugin/springsecurity/acl'

    String description = 'Copies ACL domain classes to the project'

    /**
     * The command writes files and reads nothing of the application, so its {@code BootStrap} classes are not run.
     */
    boolean skipBootstrap = true

    @Override
    boolean handle() {
        for (String name : DOMAIN_CLASSES) {
            render(template: template('_' + name + '.groovy'),
                    destination: file(DOMAIN_DIRECTORY + '/' + name + '.groovy'),
                    overwrite: false)
        }
        true
    }
}
