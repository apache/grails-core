/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.gsp.jsp;

/**
 * An interface that represents a Jsp taglib
 *
 * @author Graeme Rocher
 */
public interface JspTagLib {

    /**
     * @return The URI of the tag library
     */
    String getURI();

    /**
     * Obtains a reference to a JspTag instance contained within the library
     *
     * @param name The name of the tag
     * @return A JspTag instance or null if it doesn't exist
     */
    JspTag getTag(String name);

}
