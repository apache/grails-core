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
 * Example:
 * <pre>
 * {@code
 *  <gr:collect in="${thing}" expr="it.length() == 3">
 *        <p>${it}</p>
 *  </gr:collect>
 * }
 * </pre>
 *
 * @author Graeme Rocher
 * @since 19-Jan-2006
 */
public class GroovyCollectTag extends GroovySyntaxTag {

    public static final String TAG_NAME = "collect";
    private static final String ATTRIBUTE_EXPR = "expr";

    @Override
    public boolean isKeepPrecedingWhiteSpace() {
        return true;
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

        String expr = attributes.get(ATTRIBUTE_EXPR);
        if (GrailsStringUtils.isBlank(expr)) {
            throw new GrailsTagException("Tag [" + TAG_NAME + "] missing required attribute [" + ATTRIBUTE_EXPR + "]", parser.getPageName(), parser.getCurrentOutputLineNumber());
        }

        StringBuilder builder = new StringBuilder();
        builder.append(in);
        builder.append(".collect {");
        expr = calculateExpression(expr);
        builder.append(expr);
        builder.append("}");

        doEachMethod(builder.toString());
    }

    public void doEndTag() {
        out.println("}");
    }

    public String getName() {
        return TAG_NAME;
    }
}
