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

import grails.util.GrailsStringUtils;
import org.grails.taglib.GrailsTagException;

/**
 *
 * Example:
 *
 * <pre>
 * {@code 
 *  <gr:grep in="${thing}" filter="${Pattern.compile('[a-zA-Z]')}">
 *        <p>${it}</p>
 *  </gr:grep>
 * }
 * </pre>
 *
 * @author Graeme Rocher
 */
public class GroovyGrepTag extends GroovySyntaxTag {

    public static final String TAG_NAME = "grep";
    private static final String ATTRIBUTE_FILTER = "filter";

    @Override
    public boolean isKeepPrecedingWhiteSpace() {
        return false;
    }

    @Override
    public boolean isAllowPrecedingContent() {
        return true;
    }

    public void doStartTag() {
        String in = attributes.get(ATTRIBUTE_IN);
        if (GrailsStringUtils.isBlank(in)) {
            throw new GrailsTagException("Tag [" + TAG_NAME + "] missing required attribute [" + ATTRIBUTE_IN + "]", parser.getPageName(), parser.getCurrentOutputLineNumber());
        }

        String filter = attributes.get(ATTRIBUTE_FILTER);
        if (GrailsStringUtils.isBlank(filter)) {
            throw new GrailsTagException("Tag [" + TAG_NAME + "] missing required attribute [" + ATTRIBUTE_FILTER + "]", parser.getPageName(), parser.getCurrentOutputLineNumber());
        }

        StringBuilder builder = new StringBuilder();
        builder.append(in);
        builder.append(".grep(");
        filter = calculateExpression(filter);
        builder.append(filter);
        builder.append(")");
        doEachMethod(builder.toString());
    }

    public void doEndTag() {
        endEachMethod();
    }

    public String getName() {
        return TAG_NAME;
    }
}
