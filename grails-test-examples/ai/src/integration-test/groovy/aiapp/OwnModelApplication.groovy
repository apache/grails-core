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
package aiapp

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic

import grails.boot.GrailsApp
import grails.boot.config.ApplicationArtefactScanner
import org.apache.grails.testing.ai.ScriptedChatModel

/**
 * The application with a chat model of its own, defined in {@code doWithSpring} the way an application
 * defines one in {@code resources.groovy}: after the auto-configuration conditions have been evaluated.
 */
@CompileStatic
class OwnModelApplication extends Application {

    static void main(String[] args) {
        GrailsApp.run(OwnModelApplication, args)
    }

    /**
     * The artefact scan is anchored on the application class, which here lives with the tests, so anchor
     * it on the application's own class to get its controllers and services.
     */
    @Override
    Collection<Class> classes() {
        ApplicationArtefactScanner.scanApplicationClasses(Application, packageNames())
    }

    @Override
    @CompileDynamic
    Closure doWithSpring() {
        return { ->
            ownChatModel(ScriptedChatModel)
        }
    }

}
