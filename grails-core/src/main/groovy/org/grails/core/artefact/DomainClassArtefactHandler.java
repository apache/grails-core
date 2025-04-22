/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.core.artefact;

import grails.artefact.Artefact;
import grails.core.ArtefactHandlerAdapter;
import grails.core.GrailsApplication;
import grails.core.GrailsClass;
import grails.core.GrailsDomainClass;
import grails.core.support.GrailsApplicationAware;
import groovy.lang.Closure;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.grails.compiler.injection.GrailsASTUtils;
import org.grails.core.DefaultGrailsDomainClass;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.io.support.Resource;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;

/**
 * Evaluates the conventions that define a domain class in Grails.
 *
 * @author Graeme Rocher
 * @author Marc Palmer (marc@anyware.co.uk)
 */
public class DomainClassArtefactHandler extends ArtefactHandlerAdapter implements GrailsApplicationAware, Ordered {

    public static final String TYPE = "Domain";
    public static final String PLUGIN_NAME = "domainClass";
    private  static final String ENTITY_ANN_NAME = "Entity";
    private static final String GRAILS_PACKAGE_PREFIX = "grails.";
    private static final String JAKARTA_PERSISTENCE = "jakarta.persistence";

    public DomainClassArtefactHandler() {
        super(TYPE, GrailsDomainClass.class, DefaultGrailsDomainClass.class, null, true);
    }

    public void setGrailsApplication(GrailsApplication grailsApplication) {
        // no-op
    }

    @Override
    public String getPluginName() {
        return PLUGIN_NAME;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public GrailsClass newArtefactClass(Class artefactClass) {
        return new DefaultGrailsDomainClass(artefactClass);
    }

    @SuppressWarnings("rawtypes")
    public GrailsClass newArtefactClass(Class artefactClass, MappingContext mappingContext) {
        return new DefaultGrailsDomainClass(artefactClass, mappingContext);
    }

    @Override
    protected boolean isArtefactResource(Resource resource) throws IOException {
        return super.isArtefactResource(resource) && GrailsResourceUtils.isDomainClass(resource.getURL());
    }

    @Override
    protected boolean isValidArtefactClassNode(ClassNode classNode, int modifiers) {
        return !classNode.isEnum() && !(classNode instanceof InnerClassNode);
    }


    @Override
    public boolean isArtefact(ClassNode classNode) {
        if(classNode == null) return false;
        if(!isValidArtefactClassNode(classNode, classNode.getModifiers())) return false;

        URL url = GrailsASTUtils.getSourceUrl(classNode);
        if(url != null) {
            return GrailsResourceUtils.isDomainClass(url);
        }
        else {
            return super.isArtefact(classNode);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    public boolean isArtefactClass(Class clazz) {
        return isDomainClass(clazz);
    }

    public static boolean isDomainClass(Class<?> clazz, boolean allowProxyClass) {
        boolean retval = isDomainClass(clazz);
        if(!retval && allowProxyClass && clazz != null && clazz.getSimpleName().contains("$")) {
            retval = isDomainClass(clazz.getSuperclass());
        }
        return retval;
    }
    
    public static boolean isDomainClass(Class<?> clazz) {
        return clazz != null && doIsDomainClassCheck(clazz);

    }

    private static boolean doIsDomainClassCheck(Class<?> clazz) {
        // it's not a closure
        if (Closure.class.isAssignableFrom(clazz)) {
            return false;
        }

        if (clazz.isEnum()) return false;
        Artefact artefactAnn = null;
        try {
            artefactAnn = clazz.getAnnotation(Artefact.class);
        } catch (ArrayStoreException e) {
            // happens if a reference to a class that no longer exists is there
        }

        if( artefactAnn != null && artefactAnn.value().equals(DomainClassArtefactHandler.TYPE) ) {
            return true;
        }

        Annotation[] annotations = null;
        try {
            annotations = clazz.getAnnotations();
        } catch (ArrayStoreException e) {
            // happens if a reference to a class that no longer exists is there
        }

        if (annotations != null) {
            for (Annotation annotation : annotations) {
                Class<? extends Annotation> annType = annotation.annotationType();
                String annName = annType.getSimpleName();

                String pkgName = annType.getPackage().getName();
                if(ENTITY_ANN_NAME.equals(annName) && pkgName.startsWith(GRAILS_PACKAGE_PREFIX) || pkgName.startsWith(JAKARTA_PERSISTENCE)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
