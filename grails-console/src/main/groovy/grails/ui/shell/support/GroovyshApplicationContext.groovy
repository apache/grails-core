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
package grails.ui.shell.support

import groovy.transform.CompileStatic

import org.apache.groovy.groovysh.GroovyshOptions
import org.apache.groovy.groovysh.Main
import org.springframework.context.support.GenericApplicationContext

import grails.core.GrailsApplication

/**
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class GroovyshApplicationContext extends GenericApplicationContext {

    @Override
    protected void finishRefresh() {
        super.finishRefresh()
        startConsole()
    }

    protected void startConsole() {
        Main.start(shellOptions().build())
    }

    /**
     * Returns the options the shell starts with: this context as {@code ctx} and the Grails application in
     * its binding, on the terminal of the JVM. A subclass can add to them, for example a terminal of its own.
     *
     * @return the options of the shell
     * @since 9.0
     */
    protected GroovyshOptions.Builder shellOptions() {
        GroovyshOptions.builder().bindings([
                ctx: this,
                (GrailsApplication.APPLICATION_ID): getBean(GrailsApplication)
        ])
    }
}
