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

import grails.web.UrlConverter;

import java.util.Set;

/**
 * Represents a controller class in Grails.
 *
 * @author Steven Devijver
 * @author Graeme Rocher
 *
 * @since 1.0
 */
public interface GrailsControllerClass extends InjectableGrailsClass {

    /**
     * The name of the index action.
     */
    String INDEX_ACTION = "index";

    /**
     * The name of the before interceptor property.
     */
    String BEFORE_INTERCEPTOR = "beforeInterceptor";

    /**
     * The name of the after interceptor property.
     */
    String AFTER_INTERCEPTOR = "afterInterceptor";

    /**
     * The general name to use when referring to controller artefacts.
     */
    String CONTROLLER = "controller";

    /**
     * The general name to use when referring to action artefacts.
     */
    String ACTION = "action";

    /**
     * The general name to use when referring to action view.
     */
    String VIEW = "view";

    /**
     * The name of the namespace property
     */
    String NAMESPACE_PROPERTY = "namespace";


    /**
     * @return The action names
     */
    Set<String> getActions();

    /**
     * @return the namespace of this controller, null if none was specified
     */
    String getNamespace();

    /**
     * @return The scope of the controller, defaults to singleton
     */
    String getScope();

    /**
     * @return Whether the scope is singleton
     */
    boolean isSingleton();


    /**
     * Returns the default action for this Controller.
     *
     * @return The default action
     */
    String getDefaultAction();

    /**
     * Initialize the controller class
     */
    void initialize();
    /**
     * Tests if a controller maps to a given URI.
     *
     * @return true if controller maps to URI
     */
    boolean mapsToURI(String uri);
    /**
     * Invokes a controller action on the given controller instance
     *
     * @param controller The controller instance
     * @param action The action
     * @return The result of the action
     * @throws Throwable Thrown when an error occurs invoking the action
     */
    Object invoke(Object controller, String action) throws Throwable;


    /**
     * Register a new {@link grails.web.UrlConverter} with the controller
     *
     * @param urlConverter The {@link grails.web.UrlConverter} to register
     */
    void registerUrlConverter(UrlConverter urlConverter);

    String actionUriToViewName(String actionUri);
}
