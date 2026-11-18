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
package org.apache.grails.core.cli.compat

import groovy.transform.CompileStatic

import org.apache.grails.core.cli.ApplicationCommand
import org.apache.grails.core.cli.ApplicationCommandTargetAware
import org.apache.grails.core.cli.ExecutionContext
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.OrderUtils

/**
 * Adapts a Grails 7 command contract to the Grails 8 CLI contract.
 */
@SuppressWarnings('deprecation')
@CompileStatic
class LegacyApplicationCommandAdapter implements ApplicationCommand, ApplicationCommandTargetAware, Ordered {

    private final grails.dev.commands.ApplicationCommand legacyCommand
    private final int order

    LegacyApplicationCommandAdapter(grails.dev.commands.ApplicationCommand legacyCommand) {
        this.legacyCommand = legacyCommand
        this.order = resolveOrder(legacyCommand)
    }

    @Override
    Object getTarget() {
        legacyCommand
    }

    /**
     * The order declared by the adapted Grails 7 command, so that commands ordered through the
     * Spring conventions keep deciding duplicate command names as they did on Grails 7.
     */
    @Override
    int getOrder() {
        order
    }

    @Override
    String getName() {
        legacyCommand.name
    }

    @Override
    String getDescription() {
        legacyCommand.description
    }

    @Override
    void setApplicationContext(ConfigurableApplicationContext applicationContext) {
        legacyCommand.applicationContext = applicationContext
    }

    @Override
    ConfigurableApplicationContext getApplicationContext() {
        legacyCommand.applicationContext
    }

    @Override
    boolean handle(ExecutionContext executionContext) {
        legacyCommand.handle(new grails.dev.commands.ExecutionContext(executionContext.commandLine))
    }

    private static int resolveOrder(grails.dev.commands.ApplicationCommand legacyCommand) {
        if (legacyCommand instanceof Ordered) {
            return ((Ordered) legacyCommand).order
        }
        OrderUtils.getOrder(legacyCommand.class, Ordered.LOWEST_PRECEDENCE)
    }
}
