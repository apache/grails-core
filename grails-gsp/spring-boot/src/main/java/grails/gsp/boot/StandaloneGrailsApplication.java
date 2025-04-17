/* Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.gsp.boot;

import grails.config.Config;

import grails.core.ArtefactHandler;
import grails.core.ArtefactInfo;
import grails.core.GrailsClass;
import org.grails.config.PropertySourcesConfig;
import org.grails.core.AbstractGrailsApplication;
import org.grails.datastore.mapping.model.MappingContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.Resource;

public class StandaloneGrailsApplication extends AbstractGrailsApplication {

    public Config getConfig() {
        if (config == null) {
            if (parentContext != null) {
                org.springframework.core.env.Environment environment = parentContext.getEnvironment();
                if (environment instanceof ConfigurableEnvironment) {
                    MutablePropertySources propertySources = ((ConfigurableEnvironment) environment).getPropertySources();
                    this.config = new PropertySourcesConfig(propertySources);
                } else {
                    this.config = new PropertySourcesConfig();
                }
            } else {
                this.config = new PropertySourcesConfig();
            }
            setConfig(this.config);
        }
        return config;
    }

    @Override
    public Class[] getAllClasses() {
        return new Class[0];
    }

    @Override
    public Class[] getAllArtefacts() {
        return new Class[0];
    }

    @Override
    public MappingContext getMappingContext() {
        return null;
    }

    @Override
    public void setMappingContext(MappingContext mappingContext) {}

    @Override
    public void refresh() {}

    @Override
    public void rebuild() {}

    @Override
    public Resource getResourceForClass(Class theClazz) {
        return null;
    }

    @Override
    public boolean isArtefact(Class theClazz) {
        return false;
    }

    @Override
    public boolean isArtefactOfType(String artefactType, Class theClazz) {
        return false;
    }

    @Override
    public boolean isArtefactOfType(String artefactType, String className) {
        return false;
    }

    @Override
    public GrailsClass getArtefact(String artefactType, String name) {
        return null;
    }

    @Override
    public ArtefactHandler getArtefactType(Class theClass) {
        return null;
    }

    @Override
    public ArtefactInfo getArtefactInfo(String artefactType) {
        return null;
    }

    @Override
    public GrailsClass[] getArtefacts(String artefactType) {
        return new GrailsClass[0];
    }

    @Override
    public GrailsClass getArtefactForFeature(String artefactType, Object featureID) {
        return null;
    }

    @Override
    public GrailsClass addArtefact(String artefactType, Class artefactClass) {
        return null;
    }

    @Override
    public GrailsClass addArtefact(String artefactType, GrailsClass artefactGrailsClass) {
        return null;
    }

    @Override
    public void registerArtefactHandler(ArtefactHandler handler) {}

    @Override
    public boolean hasArtefactHandler(String type) {
        return false;
    }

    @Override
    public ArtefactHandler[] getArtefactHandlers() {
        return new ArtefactHandler[0];
    }

    @Override
    public void initialise() {}

    @Override
    public boolean isInitialised() {
        return false;
    }

    @Override
    public GrailsClass getArtefactByLogicalPropertyName(String type, String logicalName) {
        return null;
    }

    @Override
    public void addArtefact(Class artefact) {}

    @Override
    public void addOverridableArtefact(Class artefact) {}

    @Override
    public ArtefactHandler getArtefactHandler(String type) {
        return null;
    }
}
