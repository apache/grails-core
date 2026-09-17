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
package grails.gsp.boot

import groovy.transform.CompileStatic
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.Resource

import grails.config.Config
import grails.core.ArtefactHandler
import grails.core.ArtefactInfo
import grails.core.GrailsClass
import org.grails.config.PropertySourcesConfig
import org.grails.core.AbstractGrailsApplication
import org.grails.datastore.mapping.model.MappingContext

@CompileStatic
class StandaloneGrailsApplication extends AbstractGrailsApplication {

    Config getConfig() {
        if (config == null) {
            if (parentContext != null) {
                org.springframework.core.env.Environment environment = parentContext.getEnvironment()
                if (environment instanceof ConfigurableEnvironment) {
                    MutablePropertySources propertySources = ((ConfigurableEnvironment) environment).getPropertySources()
                    this.config = new PropertySourcesConfig(propertySources)
                } else {
                    this.config = new PropertySourcesConfig()
                }
            } else {
                this.config = new PropertySourcesConfig()
            }
            setConfig(this.config)
        }
        return config
    }

    @Override
    Class[] getAllClasses() {
        return new Class[0]
    }

    @Override
    Class[] getAllArtefacts() {
        return new Class[0]
    }

    @Override
    MappingContext getMappingContext() {
        return null
    }

    @Override
    void setMappingContext(MappingContext mappingContext) {}

    @Override
    void refresh() {}

    @Override
    void rebuild() {}

    @Override
    Resource getResourceForClass(Class theClazz) {
        return null
    }

    @Override
    boolean isArtefact(Class theClazz) {
        return false
    }

    @Override
    boolean isArtefactOfType(String artefactType, Class theClazz) {
        return false
    }

    @Override
    boolean isArtefactOfType(String artefactType, String className) {
        return false
    }

    @Override
    GrailsClass getArtefact(String artefactType, String name) {
        return null
    }

    @Override
    ArtefactHandler getArtefactType(Class theClass) {
        return null
    }

    @Override
    ArtefactInfo getArtefactInfo(String artefactType) {
        return null
    }

    @Override
    GrailsClass[] getArtefacts(String artefactType) {
        return new GrailsClass[0]
    }

    @Override
    GrailsClass getArtefactForFeature(String artefactType, Object featureID) {
        return null
    }

    @Override
    GrailsClass addArtefact(String artefactType, Class artefactClass) {
        return null
    }

    @Override
    GrailsClass addArtefact(String artefactType, GrailsClass artefactGrailsClass) {
        return null
    }

    @Override
    void registerArtefactHandler(ArtefactHandler handler) {}

    @Override
    boolean hasArtefactHandler(String type) {
        return false
    }

    @Override
    ArtefactHandler[] getArtefactHandlers() {
        return new ArtefactHandler[0]
    }

    @Override
    void initialise() {}

    @Override
    boolean isInitialised() {
        return false
    }

    @Override
    GrailsClass getArtefactByLogicalPropertyName(String type, String logicalName) {
        return null
    }

    @Override
    void addArtefact(Class artefact) {}

    @Override
    void addOverridableArtefact(Class artefact) {}

    @Override
    ArtefactHandler getArtefactHandler(String type) {
        return null
    }

}
