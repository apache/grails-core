/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.core.artefact;

import grails.core.ArtefactHandlerAdapter;
import grails.core.GrailsServiceClass;
import org.grails.core.DefaultGrailsServiceClass;

/**
 * @author Marc Palmer (marc@anyware.co.uk)
 */
public class ServiceArtefactHandler extends ArtefactHandlerAdapter {

    public static final String TYPE = "Service";
    public static final String PLUGIN_NAME = "services";

    public ServiceArtefactHandler() {
        super(TYPE, GrailsServiceClass.class, DefaultGrailsServiceClass.class,
                DefaultGrailsServiceClass.SERVICE, false);
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

}
