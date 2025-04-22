/*
 * Copyright 2014 the original author or authors.
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
package org.grails.web.mapping.mvc;

import grails.core.GrailsApplication;
import grails.web.UrlConverter;
import grails.web.mapping.UrlMapping;
import grails.web.mapping.UrlMappingInfo;
import grails.web.mapping.UrlMappings;
import org.springframework.http.HttpMethod;

/**
 * A {@link grails.web.mapping.UrlMappings} implementation that matches URLs directly onto controller instances
 *
 * @author Graeme Rocher
 * @since 3.0
 */
public class GrailsControllerUrlMappings extends AbstractGrailsControllerUrlMappings {
    public GrailsControllerUrlMappings(GrailsApplication grailsApplication, UrlMappings urlMappingsHolderDelegate, UrlConverter urlConverter) {
        super(grailsApplication, urlMappingsHolderDelegate, urlConverter);
    }
    public GrailsControllerUrlMappings(GrailsApplication grailsApplication, UrlMappings urlMappingsHolderDelegate) {
        super(grailsApplication, urlMappingsHolderDelegate);
    }

    @Override
    public UrlMapping[] getUrlMappings() {
        return getUrlMappingsHolderDelegate().getUrlMappings();
    }

    @Override
    public UrlMappingInfo[] matchAll(String uri) {
        return collectControllerMappings( getUrlMappingsHolderDelegate().matchAll(uri) );
    }

    @Override
    public UrlMappingInfo[] matchAll(String uri, String httpMethod) {
        return collectControllerMappings( getUrlMappingsHolderDelegate().matchAll(uri, httpMethod) );
    }

    @Override
    public UrlMappingInfo[] matchAll(String uri, String httpMethod, String version) {
        return collectControllerMappings( getUrlMappingsHolderDelegate().matchAll(uri, httpMethod, version) );
    }

    @Override
    public UrlMappingInfo[] matchAll(String uri, HttpMethod httpMethod) {
        return collectControllerMappings(  getUrlMappingsHolderDelegate().matchAll(uri, httpMethod) );
    }

    @Override
    public UrlMappingInfo[] matchAll(String uri, HttpMethod httpMethod, String version) {
        return collectControllerMappings(  getUrlMappingsHolderDelegate().matchAll(uri, httpMethod, version) );
    }
}
