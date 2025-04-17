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

import groovy.lang.Closure;

import java.io.Writer;
import java.util.Map;

/**
 * An interface that represents a JSP tag that can be invoked by Grails
 */
public interface JspTag {

    /**
     * Main method to invoke a tag library and output to the target write
     *
     * @param targetWriter The writer the tag should write to
     * @param attributes The tag attributes
     */
    void doTag(Writer targetWriter, Map<String,Object> attributes);

    /**
     * Invokes a tag with a closure representing the body of the tag
     * @param targetWriter The target writer to write to
     * @param attributes The tag attributes
     * @param body The body of the tag
     */
    void doTag(Writer targetWriter, Map<String,Object> attributes, Closure<?> body);

    /**
     * @return Return true if the tag class implements the TryCatchFinally interface
     */
    boolean isTryCatchFinallyTag();

    /**
     * @return Return true if the tag class implements the IterationTag interface
     */
    boolean isIterationTag();

    /**
     * @return Return true if the tag class implements the BodyTag interface
     */
    boolean isBodyTag();
}
