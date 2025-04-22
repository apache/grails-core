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
package grails.core;

import org.springframework.context.ApplicationContext;

/**
 * @author Graeme Rocher
 * @since 0.4
 */
public interface ApplicationAttributes {
    String APPLICATION = "org.codehaus.groovy.grails.APPLICATION";
    String APPLICATION_CONTEXT = "org.codehaus.groovy.grails.APPLICATION_CONTEXT";
    String PARENT_APPLICATION_CONTEXT = "org.codehaus.groovy.grails.PARENT_APPLICATION_CONTEXT";
    String REQUEST_SCOPE_ID = "org.codehaus.groovy.grails.GRAILS_APPLICATION_ATTRIBUTES";
    String PLUGIN_MANAGER = "org.codehaus.groovy.grails.GRAILS_PLUGIN_MANAGER";

    /**
     * @return The application context for servlet
     */
    ApplicationContext getApplicationContext();

    /**
     * @return Retrieves the grails application instance
     */
    GrailsApplication getGrailsApplication();
}
