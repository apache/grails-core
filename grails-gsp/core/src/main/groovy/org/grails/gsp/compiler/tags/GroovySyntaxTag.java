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
import org.grails.gsp.GroovyPage;
import org.grails.gsp.compiler.GroovyPageParser;
import org.grails.taglib.GrailsTagException;
import org.springframework.util.Assert;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * <p>A tag type that gets translated directly into Groovy syntax by the GSP parser.</p>
 *
 * <p>This is used for Java-implemented internal tags that the Parse class uses to directly inject code into the
 * generated GSP source. These tags can do more than custom taglibs as the operate at the code level, rather than at
 * the runtime view rendering level</p>
 *
 * @author Graeme Rocher
 */
public abstract class GroovySyntaxTag implements GrailsTag {

    private static final String ERROR_NO_VAR_WITH_STATUS = "When using <g:each> with a [status] attribute, you must also define a [var]. eg. <g:each var=\"myVar\">";
    private static final String ERROR_NO_VAR_WITH_COMPILE_STATIC = "When using <g:each> in CompileStatic mode, you must also define a [var]. eg. <g:each var=\"myVar\">";
    protected static final String ATTRIBUTE_IN = "in";
    protected static final String ATTRIBUTE_VAR = "var";
    protected static final String ATTRIBUTES_STATUS = "status";
    @SuppressWarnings("rawtypes")
    protected Map tagContext;
    protected PrintWriter out;
    protected Map<String, String> attributes = new HashMap<String, String>();
    protected GroovyPageParser parser;

    protected String foreachRenamedIt;

    @SuppressWarnings("rawtypes")
    public void init(Map context) {
        tagContext = context;
        parser = (GroovyPageParser) context.get(GroovyPageParser.class);
        Object outObj = context.get(GroovyPage.OUT);
        if (outObj instanceof PrintWriter) {
            out = (PrintWriter)context.get(GroovyPage.OUT);
        }
    }

    protected boolean isCompileStaticMode() {
        return parser != null && parser.isCompileStaticMode();
    }

    public void setWriter(Writer w) {
        Assert.isInstanceOf(PrintWriter.class, w, "A GroovySynax tag requires a java.io.PrintWriter instance");
        out = (PrintWriter)w;
    }

    @SuppressWarnings("rawtypes")
    public void setAttributes(Map attributes) {
        for (Iterator i = attributes.keySet().iterator(); i.hasNext();) {
            String attrName = (String) i.next();
            setAttribute(attrName,attributes.get(attrName));
        }
    }

    public void setAttribute(String name, Object value) {
        Assert.isInstanceOf(String.class, value, "A GroovySyntax tag requires only string valued attributes");

        attributes.put(name.substring(1,name.length()-1), (String)value);
    }

    /**
     * <p>Tags must return the correct value to indicate whether or not whitespace before this tag should be kept in the output.</p>
     * <p>This is for tags that must follow other tags, such as g:else or g:elseif that do not allow content between them and the
     * previous tag, and need to swallow the whitespace between them.</p>
     * @return true if any whitespace immediately before the tag should be kept in the output - false if it is to be discarded
     */
    public abstract boolean isKeepPrecedingWhiteSpace();

    /**
     * <p>Tags must return the correct value to indicate whether or not non-whitespace content is permitted before this tag.</p>
     * <p>This is for tags that must follow other tags, such as g:else or g:elseif that do not allow content between them and the
     * previous tag. It is simply used as a safety mechanism to trap incorrect usage of tags.</p>
     * TODO rework this and combine with isKeepPrecedingWhiteSpace as really they are used in the same situations
     * @return true if any content is allowed immediately before the tag - false if it is an error to have such content before it
     */
    public abstract boolean isAllowPrecedingContent();

    protected String calculateExpression(String expr) {
        Assert.isTrue(!GrailsStringUtils.isBlank(expr), "Argument [expr] cannot be null or blank");

        expr = expr.trim();
        if ((expr.startsWith("\"") && expr.endsWith("\"")) ||
                (expr.startsWith("\'") && expr.endsWith("\'"))) {
            expr = expr.substring(1,expr.length()-1);
            expr = expr.trim();
        }
        if (expr.startsWith("${") && expr.endsWith("}")) {
            expr = expr.substring(2,expr.length()-1);
            expr = expr.trim();
        }
        return expr;
    }

    /**
     * @param in
     */
    protected void doEachMethod(String in) {
        String var = attributes.get(ATTRIBUTE_VAR);
        String status = attributes.get(ATTRIBUTES_STATUS);
        var = extractAttributeValue(var);
        status = extractAttributeValue(status);

        boolean hasStatus = !GrailsStringUtils.isBlank(status);
        boolean hasVar = !GrailsStringUtils.isBlank(var);
        if (hasStatus && !hasVar) {
            throw new GrailsTagException(ERROR_NO_VAR_WITH_STATUS, parser.getPageName(), parser.getCurrentOutputLineNumber());
        }

        if (var.equals(status) && (hasStatus)) {
            throw new GrailsTagException("Attribute [" + ATTRIBUTE_VAR +
                    "] cannot have the same value as attribute [" + ATTRIBUTES_STATUS + "]", parser.getPageName(), parser.getCurrentOutputLineNumber());
        }

        if (hasStatus) {
            out.println("loop:{");
            out.println("int "+ status +" = 0");
        }
        if (!hasVar) {
            if (isCompileStaticMode()) {
                throw new GrailsTagException(ERROR_NO_VAR_WITH_COMPILE_STATIC, parser.getPageName(), parser.getCurrentOutputLineNumber());
            }
            var = "_it"+ Math.abs(System.identityHashCode(this));
            foreachRenamedIt = var;
        }

        String[] entryVars = null;
        if (hasVar && var.indexOf(',') > -1) {
            entryVars = var.split("\\s*,\\s*");
            var = "_entry" + Math.abs(System.identityHashCode(this));
        }

        out.print("for( " + var);
        out.print(" in "); // dot de-reference
        out.print(parser != null ? parser.getExpressionText(in, false) : extractAttributeValue(in));  // object
        out.print(" )"); // dot de-reference
        out.print(" {"); // start closure
        out.println();
        if (!hasVar) {
            out.println("changeItVariable(" + foreachRenamedIt +")" );
        } else if (entryVars != null) {
            out.println("def " + entryVars[0].trim() + "=" + var + ".getKey()");
            out.println("def " + entryVars[1].trim() + "=" + var + ".getValue()");
        }
    }

    protected void endEachMethod() {
        String status = attributes.get(ATTRIBUTES_STATUS);
        status = extractAttributeValue(status);
        boolean hasStatus = !GrailsStringUtils.isBlank(status);

        if (hasStatus) {
            out.println(status +"++");
            out.println("}");
        }
        out.println("}");
    }

    protected String extractAttributeValue(String attr) {
        if (GrailsStringUtils.isBlank(attr)) {
            return "";
        }
        if (((attr.startsWith("\"") && attr.endsWith("\"")) || (attr.startsWith("'") && attr.endsWith("'"))) && attr.length() > 1) {
            attr = attr.substring(1,attr.length()-1);
        }
        if (attr.endsWith("?") && attr.length() > 1) {
            attr = attr.substring(0,attr.length()-1);
        }
        return attr;
    }

    public String getForeachRenamedIt() {
        return foreachRenamedIt;
    }
}
