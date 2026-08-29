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

package org.grails.datastore.gorm.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;

import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.reflect.AstUtils;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.datastore.mapping.reflect.NameUtils;

/**
 * Utilities for resolving property names from domain classes etc.
 *
 * @author Graeme Rocher
 * @since 6.1
 */
public class AstPropertyResolveUtils {

    /**
     * Key under which the resolved property map is stashed as {@code ClassNode} metadata via
     * {@link #getPropertiesFromCache}.
     * <p>
     * Earlier versions of this class cached resolved properties in a single static, process-wide
     * {@code Map} keyed by class name (later by {@code ClassNode} identity). Both designs share a
     * problem: a static map is never emptied, so every {@code ClassNode} ever looked up - and, for
     * a primary node, the {@code GroovyClassLoader}/{@code CompileUnit} it pins via
     * {@link ClassNode#getModule()} - is retained for the lifetime of the JVM. In a long-lived
     * process that repeatedly compiles Groovy (a Gradle daemon reusing its Groovy compiler across
     * builds, a dev-mode recompile loop), that is an unbounded classloader leak.
     * <p>
     * Storing the resolved properties as metadata on the {@code ClassNode} itself instead avoids
     * both hazards this class has previously had to fix:
     * <ul>
     *     <li>No collision is possible between distinct {@code ClassNode} instances that happen to
     *     share a name (e.g. classes compiled without a package, or the same source compiled twice
     *     in separate {@code GroovyClassLoader}s) - each instance owns its own metadata storage, so
     *     there is no shared key space to collide on in the first place. This holds for the resolved
     *     <em>property map</em> built here.</li>
     *     <li>No leak is possible - the cached data is only reachable through the {@code ClassNode}
     *     it describes, so it becomes eligible for garbage collection at the same time as the node
     *     (and the compilation/classloader it belongs to) rather than being pinned forever by a
     *     static field.</li>
     * </ul>
     * This does <strong>not</strong> extend to whether a given {@code ClassNode} is treated as a
     * domain class in the first place: {@link AstUtils#isDomainClass(ClassNode)} is
     * {@code @Memoized}, and Groovy's default memoize is keyed by argument <em>equality</em>
     * ({@code ClassNode#equals}/{@code hashCode} both reduce to the class name), not identity. Two
     * distinct, same-named {@code ClassNode}s can therefore still receive the same domain-class
     * verdict from each other via that memo cache, independent of the property cache here. That is
     * a narrower, pre-existing exposure in {@code isDomainClass} itself - this class neither causes
     * nor fixes it, and it is tracked separately rather than folded into this cache's guarantees.
     * Per {@link ClassNode#getModule()}'s own convention, the cache is stored on
     * {@link ClassNode#redirect()} - the node a placeholder/generics-parameterized reference
     * ultimately stands in for - so that looking a class up through different reference nodes still
     * shares one cache entry. {@code redirect()} can in principle be reassigned after construction
     * (e.g. a forward-reference placeholder later pointed at the real, resolved node by the
     * compiler's resolve phase) - but {@link ClassNode#setRedirect(ClassNode)} explicitly refuses to
     * do so for a <em>primary</em> node ({@code GroovyBugError}), and every {@code ClassNode} this
     * utility's real callers pass in - one actively being compiled from source during an AST
     * transform - is primary, so for them {@code redirect()} is simply {@code this} for the entire
     * object's life; there is no reassignment to account for. The one place a non-primary,
     * redirect-able node does appear ({@link ClassHelper#make(Class, boolean) ClassHelper.make(c,
     * false)}) sets its redirect target eagerly, at construction, before any caller could observe or
     * cache through it in an unredirected state. Even so, if some future, currently-unforeseen path
     * ever did cache through a node before it was redirected, that would be harmless rather than a
     * source of stale data: the entry becomes unreachable the moment the redirect is set (a later
     * lookup through the same original reference resolves to the different, real node and computes
     * fresh there instead), so it is simply never read again and becomes eligible for garbage
     * collection with the abandoned node.
     * <p>
     * {@code classNode.isResolved()} gates one part of what gets cached (see
     * {@link #populatePropertiesForClassNode}) and, in principle, its result can depend on when it
     * is checked - it delegates through {@code redirect} (see {@link ClassNode#isResolved()}). Given
     * the above, that delegation is immaterial for the primary nodes this cache actually serves: a
     * terminal node's {@code isResolved()} reduces to {@code clazz != null} (or, for array/component
     * types, {@code componentType.isResolved()}) - and {@code clazz} has no setter anywhere in
     * {@code ClassNode} outside its {@code ClassNode(Class)} constructor (verified against the
     * Groovy 5.0.7 sources), so that value is fixed for the object's entire life.
     * <p>
     * Access is synchronized per {@code ClassNode} ({@code synchronized (cacheHolder)} in
     * {@link #getPropertiesFromCache}) because {@link ClassNode}'s node-metadata storage
     * ({@code NodeMetaDataHandler}, backed by {@code ListHashMap}) is explicitly documented as not
     * thread-safe, and while a given {@code ClassNode} representing a class actively being compiled
     * is normally touched by only the one thread compiling it, that is not true of every node this
     * utility can be called with: some callers resolve a property's declared type as a plain
     * {@code ClassNode} for a common JDK type (e.g. an {@code Object}- or {@code def}-typed
     * property), and {@link ClassHelper#make(Class)} returns a small, fixed set of interned,
     * JVM-wide-shared singleton nodes for exactly those types (e.g. {@link ClassHelper#OBJECT_TYPE},
     * {@code STRING_TYPE}). Two unrelated, concurrently-running compilations that both happen to
     * resolve such a type would otherwise race on the same node's metadata map with no protection at
     * all. Synchronizing per node makes concurrent calls into this class safe with respect to each
     * other and keeps the common case (one thread, one node) effectively uncontended. It cannot, by
     * itself, protect against unrelated code elsewhere in the compiler writing a <em>different</em>
     * metadata key to the same shared node concurrently without also synchronizing on that node -
     * this class has no way to compel that. That residual risk belongs to {@code ClassNode}'s
     * metadata storage in general, not to anything specific to the cache here.
     * <p>
     * Those interned singleton nodes ({@link ClassNode#isPrimaryClassNode()} returns {@code false}
     * for them, since {@link ClassHelper#make(Class)} builds them via the {@code ClassNode(Class)}
     * constructor) are never written to by this class at all: {@link #getPropertiesFromCache}
     * recomputes for them on every call instead of caching. Caching there would mean writing this
     * class's own metadata key into a node no single compilation owns, growing the residual risk
     * above from "someone else's key might race with a read" to "this class's own writes pollute a
     * JVM-wide-shared node forever" - not a real cost, since the properties of a JDK type are cheap
     * to recompute and there are only a handful of these nodes in practice.
     * <p>
     * {@link #computeProperties} can run arbitrary user code for a resolved domain class - it
     * forces the class's static initializer and invokes user-written static getters via
     * {@link ClassPropertyFetcher}. Running that while holding the per-node monitor would risk
     * deadlocking against unrelated code the caller has no visibility into, so
     * {@link #getPropertiesFromCache} computes outside the lock and only takes the monitor to check
     * for and, if needed, publish the result. Two threads can therefore both compute for the same
     * as-yet-uncached node; the second to acquire the monitor discards its own (equivalent) result
     * and returns whatever the first published, so only one map is ever visible to callers.
     */
    private static final String PROPERTIES_CACHE_KEY = AstPropertyResolveUtils.class.getName() + ".properties";

    /**
     * Resolves the type of of the given property
     *
     * @param classNode The class node
     * @param propertyName The property
     * @return The type
     */
    public static ClassNode getPropertyType(ClassNode classNode, String propertyName) {
        if (propertyName == null || propertyName.length() == 0) {
            return null;
        }
        Map<String, ClassNode> cachedProperties = getPropertiesFromCache(classNode);
        if (cachedProperties.containsKey(propertyName)) {
            return cachedProperties.get(propertyName);
        }
        ClassNode type = null;
        PropertyNode property = classNode.getProperty(propertyName);
        if (property != null) {
            type = property.getType();
        } else {
            MethodNode methodNode = classNode.getMethod(NameUtils.getGetterName(propertyName), new Parameter[0]);
            if (methodNode != null) {
                type = methodNode.getReturnType();
            } else {
                FieldNode fieldNode = classNode.getDeclaredField(propertyName);
                if (fieldNode != null) {
                    type = fieldNode.getType();
                }
            }
        }
        return type;
    }

    /**
     * Resolves the property names for the given class node
     *
     * @param classNode The class node
     * @return The property names
     */
    public static List<String> getPropertyNames(ClassNode classNode) {
        Map<String, ClassNode> cachedProperties = getPropertiesFromCache(classNode);
        return new ArrayList<>(cachedProperties.keySet());
    }

    private static Map<String, ClassNode> getPropertiesFromCache(ClassNode classNode) {
        ClassNode cacheHolder = classNode.redirect();
        if (!cacheHolder.isPrimaryClassNode()) {
            return computeProperties(cacheHolder);
        }
        synchronized (cacheHolder) {
            Map<String, ClassNode> cached = cacheHolder.getNodeMetaData(PROPERTIES_CACHE_KEY);
            if (cached != null) {
                return cached;
            }
        }
        Map<String, ClassNode> computed = computeProperties(cacheHolder);
        synchronized (cacheHolder) {
            Map<String, ClassNode> cached = cacheHolder.getNodeMetaData(PROPERTIES_CACHE_KEY);
            if (cached != null) {
                return cached;
            }
            cacheHolder.putNodeMetaData(PROPERTIES_CACHE_KEY, computed);
            return computed;
        }
    }

    private static Map<String, ClassNode> computeProperties(ClassNode classNode) {
        Map<String, ClassNode> newProperties = new HashMap<>();
        boolean isDomainClass = AstUtils.isDomainClass(classNode);
        if (isDomainClass) {
            newProperties.put(GormProperties.IDENTITY, ClassHelper.make(Long.class).getPlainNodeReference());
            newProperties.put(GormProperties.VERSION, ClassHelper.make(Long.class).getPlainNodeReference());
        }
        ClassNode currentNode = classNode;
        while (currentNode != null && !currentNode.equals(ClassHelper.OBJECT_TYPE)) {
            populatePropertiesForClassNode(currentNode, newProperties, isDomainClass, !isDomainClass);
            currentNode = currentNode.getSuperClass();
        }
        return newProperties;
    }

    private static void populatePropertiesForClassNode(ClassNode classNode, Map<String, ClassNode> cachedProperties, boolean isDomainClass, boolean allowAbstract) {
        List<MethodNode> methods = classNode.getMethods();
        for (MethodNode method : methods) {
            String methodName = method.getName();
            if (AstUtils.isGetter(method)) {
                if (!allowAbstract && method.isAbstract()) continue;
                String propertyName = NameUtils.getPropertyNameForGetterOrSetter(methodName);
                if (GormProperties.META_CLASS.equals(propertyName)) continue;
                if (isDomainClass && (GormProperties.HAS_MANY.equals(propertyName) || GormProperties.BELONGS_TO.equals(propertyName) || GormProperties.HAS_ONE.equals(propertyName))) {
                    FieldNode field = classNode.getField(propertyName);
                    if (field != null) {
                        populatePropertiesForInitialExpression(cachedProperties, field.getInitialExpression());
                    }
                } else if (!method.isStatic()) {
                    cachedProperties.put(propertyName, method.getReturnType());
                }
            }
        }
        List<PropertyNode> properties = classNode.getProperties();
        for (PropertyNode property : properties) {

            String propertyName = property.getName();
            if (propertyName.equals(GormProperties.META_CLASS)) continue;
            if (isDomainClass && (GormProperties.HAS_MANY.equals(propertyName) || GormProperties.BELONGS_TO.equals(propertyName) || GormProperties.HAS_ONE.equals(propertyName))) {
                Expression initialExpression = property.getInitialExpression();
                populatePropertiesForInitialExpression(cachedProperties, initialExpression);
            } else {
                cachedProperties.put(propertyName, property.getType());
            }
        }

        if (isDomainClass && classNode.isResolved()) {
            ClassPropertyFetcher propertyFetcher = ClassPropertyFetcher.forClass(classNode.getTypeClass());
            cachePropertiesForAssociationMetadata(cachedProperties, propertyFetcher, GormProperties.HAS_MANY);
            cachePropertiesForAssociationMetadata(cachedProperties, propertyFetcher, GormProperties.BELONGS_TO);
            cachePropertiesForAssociationMetadata(cachedProperties, propertyFetcher, GormProperties.HAS_ONE);
        }

    }

    private static void cachePropertiesForAssociationMetadata(Map<String, ClassNode> cachedProperties, ClassPropertyFetcher propertyFetcher, String associationMetadataName) {
        if (propertyFetcher.isReadableProperty(associationMetadataName)) {
            Object propertyValue = propertyFetcher.getPropertyValue(associationMetadataName);
            if (propertyValue instanceof Map) {
                Map hasManyMap = (Map) propertyValue;
                for (Object propertyName : hasManyMap.keySet()) {
                    Object val = hasManyMap.get(propertyName);
                    if (val instanceof Class) {
                        cachedProperties.put(propertyName.toString(), ClassHelper.make((Class) val).getPlainNodeReference());
                    }
                }
            }
        }
    }

    private static void populatePropertiesForInitialExpression(Map<String, ClassNode> cachedProperties, Expression initialExpression) {
        if (initialExpression instanceof MapExpression) {
            MapExpression me = (MapExpression) initialExpression;
            List<MapEntryExpression> mapEntryExpressions = me.getMapEntryExpressions();
            for (MapEntryExpression mapEntryExpression : mapEntryExpressions) {
                Expression keyExpression = mapEntryExpression.getKeyExpression();
                Expression valueExpression = mapEntryExpression.getValueExpression();
                if (valueExpression instanceof ClassExpression) {
                    cachedProperties.put(keyExpression.getText(), valueExpression.getType());
                }
            }
        }
    }

}
