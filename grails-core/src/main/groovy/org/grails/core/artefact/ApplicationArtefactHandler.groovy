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
package org.grails.core.artefact

import grails.boot.config.GrailsAutoConfiguration
import grails.core.ArtefactHandlerAdapter
import grails.core.DefaultGrailsClass
import grails.core.GrailsClass
import groovy.transform.CompileStatic
import org.codehaus.groovy.ast.ClassNode
import org.grails.compiler.injection.GrailsASTUtils

/**
 * An {@link grails.core.ArtefactHandler} that identifies the Application class
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class ApplicationArtefactHandler extends ArtefactHandlerAdapter {

    public static final ClassNode AUTO_CONFIGURATION_CLASS_NODE = new ClassNode(GrailsAutoConfiguration)
    public static final String TYPE = "Application"

    ApplicationArtefactHandler() {
        super(TYPE, GrailsClass, DefaultGrailsClass, null, false)
    }

    @Override
    boolean isArtefact(ClassNode classNode) {
        classNode.nameWithoutPackage.endsWith('Application') && GrailsASTUtils.isAssignableFrom(AUTO_CONFIGURATION_CLASS_NODE, classNode)
    }

    @Override
    boolean isArtefactClass(@SuppressWarnings("rawtypes") Class clazz) {
        GrailsAutoConfiguration.isAssignableFrom(clazz) && clazz.simpleName.endsWith('Application')
    }
}
