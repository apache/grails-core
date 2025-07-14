/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.core;

import grails.util.GrailsNameUtils;
import groovy.lang.Closure;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.grails.compiler.injection.GrailsASTUtils;
import org.grails.core.exceptions.GrailsRuntimeException;
import org.grails.io.support.GrailsResourceUtils;
import org.grails.io.support.Resource;
import org.grails.io.support.UrlResource;
import org.springframework.core.Ordered;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.net.URL;

/**
 * Adapter for the {@link grails.core.ArtefactHandler} interface
 *
 * @author Marc Palmer (marc@anyware.co.uk)
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("deprecation")
public abstract class ArtefactHandlerAdapter implements ArtefactHandler {

    protected String type;
    protected Class<?> grailsClassType;
    protected Class<?> grailsClassImpl;
    protected boolean allowAbstract;

    protected String artefactSuffix;

    public ArtefactHandlerAdapter(String type, Class<? extends GrailsClass> grailsClassType, Class<?> grailsClassImpl, String artefactSuffix) {
        this.artefactSuffix = artefactSuffix;
        this.type = type;
        this.grailsClassType = grailsClassType;
        this.grailsClassImpl = grailsClassImpl;
    }

    public ArtefactHandlerAdapter(String type, Class<? extends GrailsClass> grailsClassType, Class<?> grailsClassImpl,
                                  String artefactSuffix, boolean allowAbstract) {
        this.artefactSuffix = artefactSuffix;
        this.type = type;
        this.grailsClassType = grailsClassType;
        this.grailsClassImpl = grailsClassImpl;
        this.allowAbstract = allowAbstract;
    }

    public String getPluginName() {
        return GrailsNameUtils.getPropertyName(type);
    }

    public String getType() {
        return type;
    }

    /**
     * Default implementation of {@link grails.core.ArtefactHandler#isArtefact(org.codehaus.groovy.ast.ClassNode)} which returns true if the ClassNode passes the
     * {@link #isArtefactResource(org.grails.io.support.Resource)} method and the name of the ClassNode ends with the {@link #artefactSuffix}
     *
     * @param classNode The ClassNode instance
     * @return True if the ClassNode is an artefact of this type
     */
    @Override
    public boolean isArtefact(ClassNode classNode) {
        int modifiers = classNode.getModifiers();

        URL url = GrailsASTUtils.getSourceUrl(classNode);
        if (url == null) {
            return false;
        }
        try {
            UrlResource resource = new UrlResource(url);
            if (!isArtefactResource(resource)) {
                return false;
            }
        } catch (IOException e) {
            return false;
        }

        if (isValidArtefactClassNode(classNode, modifiers)) {
            String name = classNode.getName();
            if (name != null && this.artefactSuffix != null && name.endsWith(artefactSuffix)) {
                return true;
            }
        }
        return false;
    }

    protected boolean isValidArtefactClassNode(ClassNode classNode, int modifiers) {
        return !classNode.isEnum() && !classNode.isInterface() && !(classNode instanceof InnerClassNode);
    }

    /**
     * Subclasses can override to narrow down whether the given resource is an artefact of this type. The default is to consider all files under "grails-app" to be a resource
     *
     * @param resource The resource
     * @return True if it is a Grails artefact
     */
    protected boolean isArtefactResource(Resource resource) throws IOException {
        return GrailsResourceUtils.isGrailsResource(resource);
    }

    public final boolean isArtefact(@SuppressWarnings("rawtypes") Class aClass) {
        if (aClass == null) {
            return false;
        }

        try {
            if (isArtefactClass(aClass)) {
                return true;
            }
        } catch (Throwable t) {
            throw new GrailsRuntimeException("Failed to introspect class: " + aClass, t);
        }

        return false;
    }

    /**
     * <p>Checks that class's name ends in the suffix specified for this handler.</p>
     * <p>Override for more complex criteria</p>
     *
     * @param clazz The class to check
     * @return true if it is an artefact of this type
     */
    public boolean isArtefactClass(@SuppressWarnings("rawtypes") Class clazz) {
        if (clazz == null) {
            return false;
        }

        boolean ok = clazz.getName().endsWith(artefactSuffix) && !Closure.class.isAssignableFrom(clazz);
        if (ok && !allowAbstract) {
            ok &= !Modifier.isAbstract(clazz.getModifiers());
        }
        return ok;
    }

    /**
     * <p>Creates new GrailsClass derived object using the type supplied in constructor. May not perform
     * optimally but is a convenience.</p>
     *
     * @param artefactClass Creates a new artefact for the given class
     * @return An instance of the GrailsClass interface representing the artefact
     */
    public GrailsClass newArtefactClass(@SuppressWarnings("rawtypes") Class artefactClass) {
        try {
            Constructor<?> c = grailsClassImpl.getDeclaredConstructor(new Class[]{Class.class});
            // TODO GRAILS-720 plugin class instance created here first
            return (GrailsClass) c.newInstance(new Object[]{artefactClass});
        } catch (NoSuchMethodException e) {
            throw new GrailsRuntimeException("Unable to locate constructor with Class parameter for " + artefactClass, e);
        } catch (IllegalAccessException e) {
            throw new GrailsRuntimeException("Unable to locate constructor with Class parameter for " + artefactClass, e);
        } catch (InvocationTargetException e) {
            throw new GrailsRuntimeException("Error instantiated artefact class [" + artefactClass + "] of type [" + grailsClassImpl + "]: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        } catch (InstantiationException e) {
            throw new GrailsRuntimeException("Error instantiated artefact class [" + artefactClass + "] of type [" + grailsClassImpl + "]: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()), e);
        }
    }

    /**
     * Sets up the relationships between the domain classes, this has to be done after
     * the intial creation to avoid looping.
     */
    public void initialize(ArtefactInfo artefacts) {
        // do nothing
    }

    public GrailsClass getArtefactForFeature(Object feature) {
        return null;
    }

    public boolean isArtefactGrailsClass(GrailsClass artefactGrailsClass) {
        return grailsClassType.isAssignableFrom(artefactGrailsClass.getClass());
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
