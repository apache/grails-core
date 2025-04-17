/*
 * Copyright 2011 the original author or authors.
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
package org.grails.web.gsp;

import grails.core.GrailsDomainClass;
import grails.util.CacheEntry;
import grails.util.Environment;
import grails.util.GrailsNameUtils;
import grails.util.GrailsStringUtils;
import groovy.text.Template;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.grails.buffer.CodecPrintWriter;
import org.grails.buffer.FastStringWriter;
import org.grails.encoder.EncodedAppenderWriterFactory;
import org.grails.encoder.Encoder;
import org.grails.encoder.StreamingEncoder;
import org.grails.encoder.StreamingEncoderWriter;
import org.grails.gsp.GroovyPage;
import org.grails.gsp.GroovyPageBinding;
import org.grails.gsp.GroovyPageMetaInfo;
import org.grails.gsp.GroovyPagesTemplateEngine;
import org.grails.gsp.io.GroovyPageScriptSource;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.taglib.GrailsTagException;
import org.grails.taglib.TemplateVariableBinding;
import org.grails.taglib.encoder.OutputEncodingSettings;
import org.grails.taglib.encoder.WithCodecHelper;
import org.grails.web.gsp.io.GrailsConventionGroovyPageLocator;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.util.GrailsApplicationAttributes;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service that provides the actual implementation to RenderTagLib's render tag.
 *
 * This is an internal Grails service and should not be used by plugins directly.
 * The implementation was moved from RenderTagLib, ported to Java and then refactored.
 *
 * @author Lari Hotari
 * @author Graeme Rocher
 *
 * @since 2.0
 */
public class GroovyPagesTemplateRenderer implements InitializingBean {
    private GrailsConventionGroovyPageLocator groovyPageLocator;
    private GroovyPagesTemplateEngine groovyPagesTemplateEngine;
    private ConcurrentMap<String,CacheEntry<Template>> templateCache = new ConcurrentHashMap<String,CacheEntry<Template>>();
    private Object scaffoldingTemplateGenerator;
    private Map<String, Collection<String>> scaffoldedActionMap;
    private Map<String, GrailsDomainClass> controllerToScaffoldedDomainClassMap;
    private Method generateViewMethod;
    private boolean reloadEnabled;
    private boolean cacheEnabled = !Environment.isDevelopmentMode();

    public void afterPropertiesSet() throws Exception {
        if (scaffoldingTemplateGenerator != null) {
            // use reflection to locate method (would cause cyclic dependency otherwise)
            generateViewMethod = ReflectionUtils.findMethod(scaffoldingTemplateGenerator.getClass(), "generateView", new Class<?>[] {
                GrailsDomainClass.class, String.class, Writer.class});
        }
    }

    /**
     * Sets whether reloading is enabled
     *
     * @param reloadEnabled True if it is enabled
     */
    public void setReloadEnabled(boolean reloadEnabled) {
        this.reloadEnabled = reloadEnabled;
    }

    public void clearCache() {
        templateCache.clear();
    }

    public void render(GrailsWebRequest webRequest, TemplateVariableBinding pageScope, Map<String, Object> attrs, Object body, Writer out) throws IOException {
        Assert.state(groovyPagesTemplateEngine != null, "Property [groovyPagesTemplateEngine] must be set!");

        String templateName = getStringValue(attrs, "template");
        if (GrailsStringUtils.isBlank(templateName)) {
            throw new GrailsTagException("Tag [render] is missing required attribute [template]");
        }

        String uri = webRequest.getAttributes().getTemplateUri(templateName, webRequest.getRequest());
        String contextPath = getStringValue(attrs, "contextPath");
        String pluginName = getStringValue(attrs, "plugin");
        final Object controller = webRequest.getAttribute(GrailsApplicationAttributes.CONTROLLER, GrailsWebRequest.SCOPE_REQUEST);
        Template t = findAndCacheTemplate(controller, pageScope, templateName, contextPath, pluginName, uri);
        if (t == null) {
            if (getBooleanValue(attrs, "optional")) {
                return; // allow missing templates if optional == "true"
            }
            throw new GrailsTagException("Template not found for name [" + templateName + "] and path [" + uri + "]");
        }

        makeTemplate(webRequest, t, attrs, body, out);
    }

    // required for binary compatibility: GRAILS-11598
    private Template findAndCacheTemplate(Object controller, GrailsWebRequest webRequest, GroovyPageBinding pageScope, String templateName,
            String contextPath, String pluginName, String uri) throws IOException {
        return findAndCacheTemplate(controller, pageScope, templateName, contextPath, pluginName, uri);
    }

    private Template findAndCacheTemplate(Object controller, TemplateVariableBinding pageScope, String templateName,
            String contextPath, String pluginName, final String uri) throws IOException {

        String templatePath = GrailsStringUtils.isNotEmpty(contextPath) ? GrailsResourceUtils.appendPiecesForUri(contextPath, templateName) : templateName;
        final GroovyPageScriptSource scriptSource;
        if (pluginName == null) {
            scriptSource = groovyPageLocator.findTemplateInBinding(controller, templatePath, pageScope);
        }  else {
            scriptSource = groovyPageLocator.findTemplateInBinding(controller, pluginName, templatePath, pageScope);
        }

        String cacheKey;
        if (scriptSource == null) {
            cacheKey = contextPath + pluginName + uri;
        } else {
            if(pluginName != null) {
                cacheKey = contextPath + pluginName + scriptSource.getURI();
            }
            else {
                cacheKey = scriptSource.getURI();
            }
        }

        return CacheEntry.getValue(templateCache, cacheKey, reloadEnabled ? GroovyPageMetaInfo.LASTMODIFIED_CHECK_INTERVAL : -1, null,
                new Callable<CacheEntry<Template>>() {
                    public CacheEntry<Template> call() {
                        return new CacheEntry<Template>() {
                            boolean allowCaching = cacheEnabled;
                            boolean neverExpire = false;

                            @Override
                            protected boolean hasExpired(long timeout, Object cacheRequestObject) {
                                return neverExpire ? false : (allowCaching ? super.hasExpired(timeout, cacheRequestObject) : true);
                            }
                            
                            @Override
                            public boolean isInitialized() {
                                return allowCaching ? super.isInitialized() : false;
                            }
                            
                            @Override
                            public void setValue(Template val) {
                                if(allowCaching) {
                                    super.setValue(val);
                                }
                            }

                            @Override
                            protected Template updateValue(Template oldValue, Callable<Template> updater, Object cacheRequestObject)
                                    throws Exception {
                                Template t = null;
                                if (scriptSource != null) {
                                    t = groovyPagesTemplateEngine.createTemplate(scriptSource);
                                }
                                if (t == null && scaffoldingTemplateGenerator != null) {
                                    t = generateScaffoldedTemplate(GrailsWebRequest.lookup(), uri);
                                    // always enable caching for generated
                                    // scaffolded template
                                    allowCaching = true;
                                    // never expire scaffolded entry since scaffolding plugin flushes the whole cache on any change
                                    neverExpire = true;
                                }
                                return t;
                            }
                        };
                    }
                }, true, null);
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private void makeTemplate(GrailsWebRequest webRequest, Template t, Map<String, Object> attrs, Object body, Writer out) throws IOException {

        Writer newOut = wrapWriterWithEncoder(webRequest, attrs, out);
        boolean writerWrapped = (newOut != out);
        out = newOut;

        String var = getStringValue(attrs, "var");
        Map b = new LinkedHashMap<String, Object>();
        b.put("body", body);
        if (attrs.get("model") instanceof Map) {
            b.putAll((Map)attrs.get("model"));
        }
        if (attrs.containsKey("bean")) {
            if (GrailsStringUtils.isNotBlank(var)) {
                b.put(var, attrs.get("bean"));
            }
            else {
                b.put("it", attrs.get("bean"));
            }
        }
        if (attrs.containsKey("collection")) {
            String key = null;
            Iterator<?> iterator = InvokerHelper.asIterator(attrs.get("collection"));
            while (iterator.hasNext()) {
                Object it = iterator.next();
                if (key == null && GrailsStringUtils.isBlank(var) && it != null) {
                    key = GrailsNameUtils.getPropertyName(it.getClass());
                }
                Map itmap = new LinkedHashMap<String, Object>();
                itmap.putAll(b);
                if (GrailsStringUtils.isNotBlank(var)) {
                    itmap.put(var, it);
                }
                else {
                    itmap.put("it", it);
                    itmap.put(key, it);
                }
                t.make(itmap).writeTo(out);
            }
        } else {
            t.make(b).writeTo(out);
        }

        if (writerWrapped) {
            out.flush();
        }
    }

    private Writer wrapWriterWithEncoder(GrailsWebRequest webRequest, Map<String, Object> attrs, Writer out) {
        Object encodeAs = attrs.get(GroovyPage.ENCODE_AS_ATTRIBUTE_NAME);
        if (encodeAs != null) {
            Map<String, Object> codecSettings= WithCodecHelper.makeSettingsCanonical(encodeAs);
            String codecForTaglibs = (String)codecSettings.get(OutputEncodingSettings.TAGLIB_CODEC_NAME);
            if (codecForTaglibs != null) {
                Encoder encoder = WithCodecHelper.lookupEncoder(webRequest.getAttributes().getGrailsApplication(), codecForTaglibs);
                if (out instanceof EncodedAppenderWriterFactory) {
                    out = ((EncodedAppenderWriterFactory)out).getWriterForEncoder(encoder, webRequest.getEncodingStateRegistry());
                } else if (encoder instanceof StreamingEncoder) {
                    out=new StreamingEncoderWriter(out, (StreamingEncoder)encoder, webRequest.getEncodingStateRegistry());
                } else {
                    out = new CodecPrintWriter(out, encoder, webRequest.getEncodingStateRegistry());
                }
            }
        }
        return out;
    }

    private Template generateScaffoldedTemplate(GrailsWebRequest webRequest, String uri) throws IOException {
        Template t = null;
        Collection<String> controllerActions = scaffoldedActionMap.get(webRequest.getControllerName());
        if (controllerActions != null && controllerActions.contains(webRequest.getActionName())) {
            GrailsDomainClass domainClass = controllerToScaffoldedDomainClassMap.get(webRequest.getControllerName());
            if (domainClass != null) {
                int i = uri.lastIndexOf('/');
                String scaffoldedtemplateName = i > -1 ? uri.substring(i) : uri;
                if (scaffoldedtemplateName.toLowerCase().endsWith(".gsp")) {
                    scaffoldedtemplateName = scaffoldedtemplateName.substring(0, scaffoldedtemplateName.length() - 4);
                }
                FastStringWriter sw = new FastStringWriter();
                ReflectionUtils.invokeMethod(generateViewMethod, scaffoldingTemplateGenerator, domainClass, scaffoldedtemplateName, sw);
                t = groovyPagesTemplateEngine.createTemplate(new ByteArrayResource(sw.toString().getBytes("UTF-8"), uri), false);
            }
        }
        return t;
    }

    private String getStringValue(Map<String, Object> attrs, String key) {
        Object val = attrs.get(key);
        if (val == null) return "";
        return String.valueOf(val);
    }

    private boolean getBooleanValue(Map<String, Object> attrs, String key) {
        String val = getStringValue(attrs, key);
        if (val.isBlank()) return false;
        return Boolean.parseBoolean(val);
    }

    public void setGroovyPageLocator(GrailsConventionGroovyPageLocator locator) {
        groovyPageLocator = locator;
    }

    public void setGroovyPagesTemplateEngine(GroovyPagesTemplateEngine engine) {
        groovyPagesTemplateEngine = engine;
    }

    public void setScaffoldingTemplateGenerator(Object generator) {
        scaffoldingTemplateGenerator = generator;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void setScaffoldedActionMap(Map map) {
        scaffoldedActionMap = map;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void setControllerToScaffoldedDomainClassMap(Map map) {
        controllerToScaffoldedDomainClassMap = map;
    }

    public boolean isCacheEnabled() {
        return cacheEnabled;
    }

    public void setCacheEnabled(boolean cacheEnabled) {
        this.cacheEnabled = cacheEnabled;
    }
}
