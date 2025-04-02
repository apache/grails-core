/*
 * Copyright 2004-2005 Graeme Rocher
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
package org.grails.web.mapping;

import grails.web.mapping.UrlMappingData;
import grails.web.mapping.UrlMappingParser;

/**
 * A simple implementation of the UrlMappingParser interface. Most of the logical is encapsulated in the
 * DefaultUrlMappingData class.
 *
 * @see DefaultUrlMappingData
 *
 * @author Graeme Rocher
 * @since 0.5
 *
 *
 * <p>
 * Created: Mar 5, 2007
 * Time: 8:35:26 AM
 */
public class DefaultUrlMappingParser implements UrlMappingParser {
    public UrlMappingData parse(String url) {
        return new DefaultUrlMappingData(url);
    }
}
