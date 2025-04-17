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
package org.grails.gsp.compiler.tags;

import java.io.Writer;
import java.util.Map;

/**
 * Allows to tag implementation to be abstracted from the JSP custom tag spec.. hence allowing
 * them to be used in direct method calls etc.
 *
 * @author Graeme Rocher
 */
public interface GrailsTag {

    @SuppressWarnings("rawtypes")
    void init(Map tagContext);

    /**
     * Sets the writer that processes the tag
     * @param w
     */
    void setWriter(Writer w);

    /**
     * Sets the attributes of the tag
     * @param attributes
     */
    @SuppressWarnings("rawtypes")
    void setAttributes(Map attributes);

    /**
     * Sets an attribute of the tag
     * @param name
     * @param value
     */
    void setAttribute(String name, Object value);

    /**
     * Process the start tag
     */
    void doStartTag();

    /**
     * process the end tag
     */
    void doEndTag();

    /**
     * @return The name of the tag
     */
    String getName();
}
