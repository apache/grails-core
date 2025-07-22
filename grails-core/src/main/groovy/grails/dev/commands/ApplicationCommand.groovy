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
package grails.dev.commands

import grails.util.Described
import grails.util.GrailsNameUtils
import grails.util.Named
import org.springframework.context.ConfigurableApplicationContext

/**
 * Represents a command that is run against the {@link org.springframework.context.ApplicationContext}
 *
 * @author Graeme Rocher
 * @since 3.0
 */
trait ApplicationCommand implements Named, Described {

    private ConfigurableApplicationContext applicationContext

    /**
     * Sets the application context of the command
     *
     * @param applicationContext The application context
     */
    void setApplicationContext(ConfigurableApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }

    ConfigurableApplicationContext getApplicationContext() {
        return this.applicationContext
    }

    @Override
    String getName() {
        return GrailsNameUtils.getScriptName( GrailsNameUtils.getLogicalName(getClass().getName(),"Command") )
    }

    @Override
    String getDescription() {
        getName()
    }

    /**
     * Handles the command
     *
     * @param executionContext The execution context
     * @return True if the command was successful
     */
    abstract boolean handle(ExecutionContext executionContext)

}
