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
 * @author Graeme Rocher
 */
public class GroovyWhileTag extends GroovySyntaxTag {

    public static final String TAG_NAME = "while";
    private static final String ATTRIBUTE_TEST = "test";

    public void doStartTag() {
        String test = attributes.get(ATTRIBUTE_TEST);
        if (GrailsStringUtils.isBlank(test)) {
            throw new GrailsTagException("Tag [" + TAG_NAME + "] missing required attribute [" +
                    ATTRIBUTE_TEST + "]", parser.getPageName(), parser.getCurrentOutputLineNumber());
        }
        out.print("while(");
        out.print(test);
        out.println(") {");
    }

    public void doEndTag() {
        out.println("}");
    }

    public String getName() {
        return TAG_NAME;
    }

    @Override
    public boolean isKeepPrecedingWhiteSpace() {
        return true;
    }

    @Override
    public boolean isAllowPrecedingContent() {
        return true;
    }
}
