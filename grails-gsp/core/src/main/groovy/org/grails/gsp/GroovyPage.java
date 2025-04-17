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
package org.grails.gsp;

import grails.core.GrailsApplication;
import grails.util.CollectionUtils;
import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.Script;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.grails.buffer.GrailsPrintWriter;
import org.grails.encoder.Encoder;
import org.grails.exceptions.ExceptionUtils;
import org.grails.gsp.jsp.JspTag;
import org.grails.gsp.jsp.JspTagLib;
import org.grails.gsp.jsp.TagLibraryResolver;
import org.grails.taglib.*;
import org.grails.taglib.encoder.OutputContext;
import org.grails.taglib.encoder.OutputEncodingStack;
import org.grails.taglib.encoder.OutputEncodingStackAttributes;
import org.grails.taglib.encoder.WithCodecHelper;

import java.io.Writer;
import java.lang.reflect.Field;
import java.util.*;

/**
 * <p>NOTE: Based on work done by on the GSP standalone project (https://gsp.dev.java.net/)
 * </p>
 * <p>Base class for a GroovyPage (at the moment there is nothing in here but could be useful for
 * providing utility methods etc.</p>
 *
 * @author Troy Heninger
 * @author Graeme Rocher
 * @author Lari Hotari
 */
public abstract class GroovyPage extends Script {
    private static final String APPLY_CODEC_TAG_NAME = "applyCodec";
    public static final String ENCODE_AS_ATTRIBUTE_NAME = "encodeAs";
    public static final Closure<?> EMPTY_BODY_CLOSURE = TagOutput.EMPTY_BODY_CLOSURE;

    private static final Log LOG = LogFactory.getLog(GroovyPage.class);

    public static final String OUT = "out";
    public static final String EXPRESSION_OUT = "expressionOut";
    public static final String EXPRESSION_OUT_STATEMENT = EXPRESSION_OUT; // "getCodecOut()";
    public static final String OUT_STATEMENT = OUT; // "getOut()";
    public static final String CODEC_VARNAME = "Codec";
    public static final String PLUGIN_CONTEXT_PATH = "pluginContextPath";
    public static final String EXTENSION = ".gsp";
    public static final String DEFAULT_NAMESPACE = "g";
    public static final String LINK_NAMESPACE = "link";
    public static final String TEMPLATE_NAMESPACE = "tmpl";
    public static final String PAGE_SCOPE = "pageScope";

    public static final Collection<String> RESERVED_NAMES = CollectionUtils.newSet(
            OUT,
            EXPRESSION_OUT,
            CODEC_VARNAME,
            PLUGIN_CONTEXT_PATH,
            PAGE_SCOPE);

    private static final String BINDING = "binding";
    private static final String BLANK_STRING = "";
    @SuppressWarnings("rawtypes")
    private Map jspTags = Collections.emptyMap();
    private TagLibraryResolver jspTagLibraryResolver;
    protected TagLibraryLookup gspTagLibraryLookup;
    private String[] htmlParts;
    private Set<Integer> htmlPartsSet;
    private GrailsPrintWriter out;
    private GrailsPrintWriter staticOut;
    private GrailsPrintWriter expressionOut;
    private OutputEncodingStack outputStack;
    protected OutputContext outputContext;
    private String pluginContextPath;
    private Encoder rawEncoder;

    private final List<Closure<?>> bodyClosures = new ArrayList<Closure<?>>(15);

    public GroovyPage() {
        init();
    }

    protected void init() {
        // do nothing
    }

    public final Writer getOut() {
        return out;
    }

    public final Writer getExpressionOut() {
        return expressionOut;
    }

    public void setOut(Writer newWriter) {
        throw new IllegalStateException("Setting out in page isn't allowed.");
    }

    public void initRun(Writer target, OutputContext outputContext, GroovyPageMetaInfo metaInfo) {
        OutputEncodingStackAttributes.Builder attributesBuilder = new OutputEncodingStackAttributes.Builder();
        if (metaInfo != null) {
            setJspTags(metaInfo.getJspTags());
            setJspTagLibraryResolver(metaInfo.getJspTagLibraryResolver());
            setGspTagLibraryLookup(metaInfo.getTagLibraryLookup());
            setHtmlParts(metaInfo.getHtmlParts());
            setPluginContextPath(metaInfo.getPluginPath());
            attributesBuilder.outEncoder(metaInfo.getOutEncoder());
            attributesBuilder.staticEncoder(metaInfo.getStaticEncoder());
            attributesBuilder.expressionEncoder(metaInfo.getExpressionEncoder());
            attributesBuilder.defaultTaglibEncoder(metaInfo.getTaglibEncoder());
            applyModelFieldsFromBinding(metaInfo.getModelFields());
        }
        attributesBuilder.allowCreate(true).topWriter(target).autoSync(false).pushTop(true);
        attributesBuilder.outputContext(outputContext);
        attributesBuilder.inheritPreviousEncoders(false);
        outputStack = OutputEncodingStack.currentStack(attributesBuilder.build());

        out = outputStack.getOutWriter();
        staticOut = outputStack.getStaticWriter();
        expressionOut = outputStack.getExpressionWriter();

        this.outputContext = outputContext;
        if (outputContext != null) {
            outputContext.setCurrentWriter(out);
            GrailsApplication grailsApplication = outputContext.getGrailsApplication();
            if (grailsApplication != null) {
                rawEncoder = WithCodecHelper.lookupEncoder(grailsApplication, "Raw");
            }
        }

        setVariableDirectly(OUT, out);
        setVariableDirectly(EXPRESSION_OUT, expressionOut);
    }

    private void applyModelFieldsFromBinding(Iterable<Field> modelFields) {
        for(Field field : modelFields) {
            try {
                Object value = getProperty(field.getName());
                if (value != null) {
                    field.set(this, value);
                }
            } catch (IllegalAccessException e) {
                throw new GroovyPagesException("Error setting model field '" + field.getName() + "'", e, -1, getGroovyPageFileName());
            }
        }
    }

    public Object raw(Object value) {
        if (rawEncoder == null) {
            return InvokerHelper.invokeMethod(value, "encodeAsRaw", null);
        }
        return rawEncoder.encode(value);
    }

    @SuppressWarnings("unchecked")
    private void setVariableDirectly(String name, Object value) {
        Binding binding = getBinding();
        if (binding instanceof AbstractTemplateVariableBinding) {
            ((AbstractTemplateVariableBinding)binding).setVariableDirectly(name, value);
        } else {
            binding.getVariables().put(name, value);
        }
    }

    public String getPluginContextPath() {
        return pluginContextPath != null ? pluginContextPath : BLANK_STRING;
    }

    public void setPluginContextPath(String pluginContextPath) {
        this.pluginContextPath = pluginContextPath;
    }

    public void cleanup() {
        outputStack.pop(true);
    }

    public final void createClosureForHtmlPart(int partNumber, int bodyClosureIndex) {
        final String htmlPart = htmlParts[partNumber];
        setBodyClosure(bodyClosureIndex, new TagOutput.ConstantClosure(htmlPart));
    }

    public final void setBodyClosure(int index, Closure<?> bodyClosure) {
        while (index >= bodyClosures.size()) {
            bodyClosures.add(null);
        }

        bodyClosures.set(index, bodyClosure);
    }

    public final Closure<?> getBodyClosure(int index) {
        if (index >= 0) {
            return bodyClosures.get(index);
        }
        return null;
    }

    /**
     * Sets the JSP tag library resolver to use to resolve JSP tags
     *
     * @param jspTagLibraryResolver The JSP tag resolve
     */
    public void setJspTagLibraryResolver(TagLibraryResolver jspTagLibraryResolver) {
        this.jspTagLibraryResolver = jspTagLibraryResolver;
    }

    /**
     * Sets the GSP tag library lookup class
     *
     * @param gspTagLibraryLookup The class used to lookup a GSP tag library
     */
    public void setGspTagLibraryLookup(TagLibraryLookup gspTagLibraryLookup) {
        this.gspTagLibraryLookup = gspTagLibraryLookup;
    }

    /**
     * Obtains a reference to the JSP tag library resolver instance
     *
     * @return The JSP TagLibraryResolver instance
     */
    TagLibraryResolver getTagLibraryResolver() {
        return jspTagLibraryResolver;
    }

    /**
     * In the development environment this method is used to evaluate expressions and improve error reporting
     *
     * @param exprText   The expression text
     * @param lineNumber The line number
     * @param outerIt    The other reference to the variable 'it'
     * @param evaluator  The expression evaluator
     * @return The result
     */
    public Object evaluate(String exprText, int lineNumber, Object outerIt, Closure<?> evaluator) {
        try {
            return evaluator.call(outerIt);
        } catch (Exception e) {
            throw new GroovyPagesException("Error evaluating expression [" + exprText + "] on line [" +
                 lineNumber + "]: " + e.getMessage(), e, lineNumber, getGroovyPageFileName());
        }
    }

    public abstract String getGroovyPageFileName();

    @Override
    public Object getProperty(String property) {
        if (OUT.equals(property)) return out;
        if (EXPRESSION_OUT.equals(property)) return expressionOut;
        // in GSP we assume if a property doesn't exist that
        // it is null rather than throw an error this works nicely
        // with the Groovy Truth
        if (BINDING.equals(property)) return getBinding();

        return resolveProperty(property);
    }

    protected Object resolveProperty(String property) {
        Object value = getBinding().getVariable(property);
        if (value != null) {
            return value;
        }

        // check if a taglib can be found
        value = lookupTagDispatcher(property);
        if (value == null) {
            value = lookupJspTagLib(property);
        }

        if (value != null) {
            // cache lookup for next execution
            setVariableDirectly(property, value);
        }

        return value;
    }

    protected Object lookupTagDispatcher(String namespace) {
        return gspTagLibraryLookup != null ? gspTagLibraryLookup.lookupNamespaceDispatcher(namespace) : null;
    }

    protected JspTagLib lookupJspTagLib(String jspTagLibName) {
        String uri = (String) jspTags.get(jspTagLibName);
        if (uri != null) {
            TagLibraryResolver tagResolver = getTagLibraryResolver();
            return tagResolver.resolveTagLibrary(uri);
        }
        return null;
    }

    /* Static call for jsp tags resolution
     *
     * @param uri   tag uri
     * @param name  tag name
     * @return resolved tag if any
     */
    public JspTag getJspTag(String uri, String name) {
        if (jspTagLibraryResolver == null) {
            return null;
        }

        JspTagLib tagLib = jspTagLibraryResolver.resolveTagLibrary(uri);
        return tagLib != null ? tagLib.getTag(name) : null;
    }

    /**
     * Attempts to invokes a dynamic tag
     *
     * @param tagName          The name of the tag
     * @param tagNamespace     The taglib's namespace
     * @param lineNumber       GSP source lineNumber
     * @param attrs            The tags attributes
     * @param bodyClosureIndex The index of the body variable
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public final void invokeTag(String tagName, String tagNamespace, int lineNumber, Map attrs, int bodyClosureIndex) {
        Closure body = getBodyClosure(bodyClosureIndex);

        // TODO custom namespace stuff needs to be generalized and pluggable
        if (tagNamespace.equals(TEMPLATE_NAMESPACE) || tagNamespace.equals(LINK_NAMESPACE)) {
            final String tmpTagName = tagName;
            final Map tmpAttrs = attrs;
            Object encodeAs = tmpAttrs.remove(ENCODE_AS_ATTRIBUTE_NAME);
            if (tagNamespace.equals(TEMPLATE_NAMESPACE)) {
                tagName = "render";
                attrs = CollectionUtils.newMap("model", tmpAttrs, "template", tmpTagName);
            } else if (tagNamespace.equals(LINK_NAMESPACE)) {
                tagName = "link";
                attrs = CollectionUtils.newMap("mapping", tmpTagName);
                if (!tmpAttrs.isEmpty()) {
                    attrs.put("params", tmpAttrs);
                }
            }
            if (encodeAs != null) {
                attrs.put(ENCODE_AS_ATTRIBUTE_NAME, encodeAs);
            }
            tagNamespace = DEFAULT_NAMESPACE;
        }

        try {
            GroovyObject tagLib = getTagLib(tagNamespace, tagName);
            if (tagLib != null || (gspTagLibraryLookup != null && gspTagLibraryLookup.hasNamespace(tagNamespace))) {
                if (tagLib != null) {
                    boolean returnsObject = gspTagLibraryLookup.doesTagReturnObject(tagNamespace, tagName);
                    Object tagLibClosure = tagLib.getProperty(tagName);
                    if (tagLibClosure instanceof Closure) {
                        Map<String, Object> encodeAsForTag = gspTagLibraryLookup.getEncodeAsForTag(tagNamespace, tagName);
                        invokeTagLibClosure(tagName, tagNamespace, (Closure)tagLibClosure, attrs, body, returnsObject, encodeAsForTag);
                    } else {
                        throw new GrailsTagException("Tag [" + tagName + "] does not exist in tag library [" + tagLib.getClass().getName() + "]", getGroovyPageFileName(), lineNumber);
                    }
                } else {
                    throw new GrailsTagException("Tag [" + tagName + "] does not exist. No tag library found for namespace: " + tagNamespace, getGroovyPageFileName(), lineNumber);
                }
            } else {
                staticOut.append('<').append(tagNamespace).append(':').append(tagName);
                for (Object o : attrs.entrySet()) {
                    Map.Entry entry = (Map.Entry) o;
                    staticOut.append(' ');
                    staticOut.append(entry.getKey()).append('=');
                    String value = String.valueOf(entry.getValue());
                    // handle attribute value quotes & possible escaping " -> &quot;
                    boolean containsQuotes = (value.indexOf('"') > -1);
                    boolean containsSingleQuote = (value.indexOf('\'') > -1);
                    if (containsQuotes && !containsSingleQuote) {
                        staticOut.append('\'').append(value).append('\'');
                    } else if (containsQuotes & containsSingleQuote) {
                        staticOut.append('\"').append(value.replaceAll("\"", "&quot;")).append('\"');
                    } else {
                        staticOut.append('\"').append(value).append('\"');
                    }
                }
                
                if (body == null) {
                    staticOut.append("/>");
                } else {
                    staticOut.append('>');
                    Object bodyOutput = body.call();
                    if (bodyOutput != null) staticOut.print(bodyOutput);
                    staticOut.append("</").append(tagNamespace).append(':').append(tagName).append('>');
                }

            }
        } catch (Throwable e) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Full exception for problem at " + getGroovyPageFileName() + ":" + lineNumber, e);
            }

            // The capture* tags are internal tags and not to be displayed to the user
            // hence we don't wrap the exception and simple rethrow it
            if (tagName.matches("capture(Body|Head|Meta|Title|Component)")) {
                RuntimeException rte = ExceptionUtils.getFirstRuntimeException(e);
                if (rte == null) {
                    throwRootCause(tagName, tagNamespace, lineNumber, e);
                } else {
                    throw rte;
                }
            } else {
                throwRootCause(tagName, tagNamespace, lineNumber, e);
            }
        }
    }

    private void invokeTagLibClosure(String tagName, String tagNamespace, Closure<?> tagLibClosure, Map<?, ?> attrs, Closure<?> body,
            boolean returnsObject, Map<String, Object> defaultEncodeAs) {
        Closure<?> tag = (Closure<?>)tagLibClosure.clone();

        if (!(attrs instanceof GroovyPageAttributes)) {
            attrs = new GroovyPageAttributes(attrs);
        }
        ((GroovyPageAttributes)attrs).setGspTagSyntaxCall(true);

        boolean encodeAsPushedToStack=false;
        try {
            Map<String, Object> codecSettings = TagOutput.createCodecSettings(tagNamespace, tagName, attrs, defaultEncodeAs);
            if (codecSettings != null) {
                outputStack.push(WithCodecHelper.createOutputStackAttributesBuilder(codecSettings, outputContext.getGrailsApplication()).build());
                encodeAsPushedToStack=true;
            }
            Object tagresult = null;
            switch (tag.getParameterTypes().length) {
                case 1:
                    tagresult = tag.call(new Object[]{attrs});
                    outputTagResult(returnsObject, tagresult);
                    if (body != null && body != TagOutput.EMPTY_BODY_CLOSURE) {
                        body.call();
                    }
                    break;
                case 2:
                    tagresult = tag.call(new Object[]{attrs, (body != null) ? body : TagOutput.EMPTY_BODY_CLOSURE});
                    outputTagResult(returnsObject, tagresult);
                    break;
            }
        } finally {
            if (encodeAsPushedToStack) outputStack.pop();
        }
    }

    private void outputTagResult(boolean returnsObject, Object tagresult) {
        if (returnsObject && tagresult != null && !(tagresult instanceof Writer)) {
            if (tagresult instanceof String && isHtmlPart((String)tagresult)) {
                staticOut.print(tagresult);
            } else {
                outputStack.getTaglibWriter().print(tagresult);
            }
        }
    }

    private void throwRootCause(String tagName, String tagNamespace, int lineNumber, Throwable e) {
        Throwable cause = ExceptionUtils.getRootCause(e);
        if (cause instanceof GrailsTagException) {
            // catch and rethrow with context
            throw new GrailsTagException(cause.getMessage(), getGroovyPageFileName(), lineNumber);
        }

        throw new GrailsTagException("Error executing tag <" + tagNamespace + ":" + tagName +
                ">: " + e.getMessage(), e, getGroovyPageFileName(), lineNumber);
    }

    private GroovyObject getTagLib(String namespace, String tagName) {
        return TagOutput.lookupCachedTagLib(gspTagLibraryLookup, namespace, tagName);
    }

    /**
     * Return whether the given name cannot be used within the binding of a GSP
     *
     * @param name True if it can't
     * @return A boolean true or false
     */
    public final static boolean isReservedName(String name) {
        return RESERVED_NAMES.contains(name);
    }

    public final void printHtmlPart(final int partNumber) {
        staticOut.write(htmlParts[partNumber]);
    }

    /**
     * Sets the JSP tags used by this GroovyPage instance
     *
     * @param jspTags The JSP tags used
     */
    @SuppressWarnings("rawtypes")
    public void setJspTags(Map jspTags) {
        this.jspTags = jspTags;
    }

    public String[] getHtmlParts() {
        return htmlParts;
    }

    protected boolean isHtmlPart(String htmlPart) {
        return htmlPartsSet != null && htmlPart != null && htmlPartsSet.contains(System.identityHashCode(htmlPart));
    }

    public void setHtmlParts(String[] htmlParts) {
        this.htmlParts = htmlParts;
        this.htmlPartsSet = new HashSet<Integer>();
        if (htmlParts != null) {
            for(String htmlPart : htmlParts) {
                if (htmlPart != null) {
                    htmlPartsSet.add(System.identityHashCode(htmlPart));
                }
            }
        }
    }

    public final OutputEncodingStack getOutputStack() {
        return outputStack;
    }

    public OutputContext getOutputContext() {
        return outputContext;
    }

    public final void createTagBody(int bodyClosureIndex, Closure<?> bodyClosure) {
        TagBodyClosure tagBody = new TagBodyClosure(this, outputContext, bodyClosure, true);
        setBodyClosure(bodyClosureIndex, tagBody);
    }

    public void changeItVariable(Object value) {
        setVariableDirectly("it", value);
    }
}
