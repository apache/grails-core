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
package grails.util;

import grails.artefact.Enhanced;
import groovy.lang.AdaptingMetaClass;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.ExpandoMetaClassCreationHandle;
import groovy.lang.GroovySystem;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaProperty;
import groovy.util.ConfigObject;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.cglib.reflect.FastClass;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Utility methods for dealing with Grails class artifacts.
 *
 * @author Graeme Rocher
 */
public class GrailsClassUtils {

    private static final Log LOG = LogFactory.getLog(GrailsClassUtils.class);
    public static final Map<Class<?>, Class<?>> PRIMITIVE_TYPE_COMPATIBLE_CLASSES = new HashMap<Class<?>, Class<?>>();

    /**
     * Just add two entries to the class compatibility map
     * @param left
     * @param right
     */
    private static final void registerPrimitiveClassPair(Class<?> left, Class<?> right) {
        PRIMITIVE_TYPE_COMPATIBLE_CLASSES.put(left, right);
        PRIMITIVE_TYPE_COMPATIBLE_CLASSES.put(right, left);
    }

    static {
        registerPrimitiveClassPair(Boolean.class, boolean.class);
        registerPrimitiveClassPair(Integer.class, int.class);
        registerPrimitiveClassPair(Short.class, short.class);
        registerPrimitiveClassPair(Byte.class, byte.class);
        registerPrimitiveClassPair(Character.class, char.class);
        registerPrimitiveClassPair(Long.class, long.class);
        registerPrimitiveClassPair(Float.class, float.class);
        registerPrimitiveClassPair(Double.class, double.class);
    }

    /**
     * Return all interfaces that the given instance implements as array,
     * including ones implemented by superclasses.
     * @param instance the instance to analyze for interfaces
     * @return all interfaces that the given instance implements as array
     */
    public static Class[] getAllInterfaces(Object instance) {
        Assert.notNull(instance, "Instance must not be null");
        return getAllInterfacesForClass(instance.getClass());
    }

    /**
     * Return all interfaces that the given class implements as array,
     * including ones implemented by superclasses.
     * <p>If the class itself is an interface, it gets returned as sole interface.
     * @param clazz the class to analyze for interfaces
     * @return all interfaces that the given object implements as array
     */
    public static Class<?>[] getAllInterfacesForClass(Class<?> clazz) {
        return getAllInterfacesForClass(clazz, null);
    }

    /**
     * Return all interfaces that the given class implements as array,
     * including ones implemented by superclasses.
     * <p>If the class itself is an interface, it gets returned as sole interface.
     * @param clazz the class to analyze for interfaces
     * @param classLoader the ClassLoader that the interfaces need to be visible in
     * (may be {@code null} when accepting all declared interfaces)
     * @return all interfaces that the given object implements as array
     */
    public static Class<?>[] getAllInterfacesForClass(Class<?> clazz, ClassLoader classLoader) {
        Set<Class> ifcs = getAllInterfacesForClassAsSet(clazz, classLoader);
        return ifcs.toArray(new Class[ifcs.size()]);
    }

    /**
     * Return all interfaces that the given instance implements as Set,
     * including ones implemented by superclasses.
     * @param instance the instance to analyze for interfaces
     * @return all interfaces that the given instance implements as Set
     */
    public static Set<Class> getAllInterfacesAsSet(Object instance) {
        Assert.notNull(instance, "Instance must not be null");
        return getAllInterfacesForClassAsSet(instance.getClass());
    }

    /**
     * Return all interfaces that the given class implements as Set,
     * including ones implemented by superclasses.
     * <p>If the class itself is an interface, it gets returned as sole interface.
     * @param clazz the class to analyze for interfaces
     * @return all interfaces that the given object implements as Set
     */
    public static Set<Class> getAllInterfacesForClassAsSet(Class clazz) {
        return getAllInterfacesForClassAsSet(clazz, null);
    }

    /**
     * Return all interfaces that the given class implements as Set,
     * including ones implemented by superclasses.
     * <p>If the class itself is an interface, it gets returned as sole interface.
     * @param clazz the class to analyze for interfaces
     * @param classLoader the ClassLoader that the interfaces need to be visible in
     * (may be {@code null} when accepting all declared interfaces)
     * @return all interfaces that the given object implements as Set
     */
    public static Set<Class> getAllInterfacesForClassAsSet(Class clazz, ClassLoader classLoader) {
        Assert.notNull(clazz, "Class must not be null");
        Set<Class> interfaces = new LinkedHashSet<Class>();
        while (clazz != null) {
            Class<?>[] ifcs = clazz.getInterfaces();
            for (Class<?> ifc : ifcs) {
                interfaces.add(ifc);
                interfaces.addAll(getAllInterfacesForClassAsSet(ifc, classLoader));
            }
            clazz = clazz.getSuperclass();
        }
        return interfaces;
    }


    /**
     * Check whether the given class is visible in the given ClassLoader.
     * @param clazz the class to check (typically an interface)
     * @param classLoader the ClassLoader to check against (may be {@code null},
     * in which case this method will always return {@code true})
     */
    public static boolean isVisible(Class<?> clazz, ClassLoader classLoader) {
        if (classLoader == null) {
            return true;
        }
        try {
            Class<?> actualClass = classLoader.loadClass(clazz.getName());
            return (clazz == actualClass);
            // Else: different interface class found...
        }
        catch (ClassNotFoundException ex) {
            // No interface class found...
            return false;
        }
    }

    /**
     * Returns true if the specified property in the specified class is of the specified type
     *
     * @param clazz The class which contains the property
     * @param propertyName The property name
     * @param type The type to check
     *
     * @return A boolean value
     */
    public static boolean isPropertyOfType(Class<?> clazz, String propertyName, Class<?> type) {
        try {
            Class<?> propType = getPropertyType(clazz, propertyName);
            return propType != null && propType.equals(type);
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Returns the value of the specified property and type from an instance of the specified Grails class
     *
     * @param clazz The name of the class which contains the property
     * @param propertyName The property name
     * @param propertyType The property type
     *
     * @return The value of the property or null if none exists
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Object getPropertyValueOfNewInstance(Class clazz, String propertyName, Class<?> propertyType) {
        // validate
        if (clazz == null || !StringUtils.hasText(propertyName)) {
            return null;
        }

        try {
            return getPropertyOrStaticPropertyOrFieldValue(BeanUtils.instantiateClass(clazz), propertyName);
        }
        catch (BeanInstantiationException e) {
            return null;
        }
    }

    /**
     * Returns the value of the specified property and type from an instance of the specified Grails class
     *
     * @param clazz The name of the class which contains the property
     * @param propertyName The property name
     *
     * @return The value of the property or null if none exists
     */
    public static Object getPropertyValueOfNewInstance(Class<?> clazz, String propertyName) {
        // validate
        if (clazz == null || !StringUtils.hasText(propertyName)) {
            return null;
        }

        try {
            return getPropertyOrStaticPropertyOrFieldValue(BeanUtils.instantiateClass(clazz), propertyName);
        }
        catch (BeanInstantiationException e) {
            return null;
        }
    }

    /**
     * Retrieves a PropertyDescriptor for the specified instance and property value
     *
     * @param instance The instance
     * @param propertyValue The value of the property
     * @return The PropertyDescriptor
     */
    public static PropertyDescriptor getPropertyDescriptorForValue(Object instance, Object propertyValue) {
        if (instance == null || propertyValue == null) {
            return null;
        }

        PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(instance.getClass());
        for (PropertyDescriptor pd : descriptors) {
            if (isAssignableOrConvertibleFrom(pd.getPropertyType(), propertyValue.getClass())) {
                Object value;
                try {
                    ReflectionUtils.makeAccessible(pd.getReadMethod());
                    value = pd.getReadMethod().invoke(instance);
                }
                catch (Exception e) {
                    throw new FatalBeanException("Problem calling readMethod of " + pd, e);
                }
                if (propertyValue.equals(value)) {
                    return pd;
                }
            }
        }
        return null;
    }

    /**
     * Returns the type of the given property contained within the specified class
     *
     * @param clazz The class which contains the property
     * @param propertyName The name of the property
     *
     * @return The property type or null if none exists
     */
    public static Class<?> getPropertyType(Class<?> clazz, String propertyName) {
        if (clazz == null || !StringUtils.hasText(propertyName)) {
            return null;
        }

        try {
            PropertyDescriptor desc=BeanUtils.getPropertyDescriptor(clazz, propertyName);
            if (desc != null) {
                return desc.getPropertyType();
            }
            return null;
        }
        catch (Exception e) {
            // if there are any errors in instantiating just return null for the moment
            return null;
        }
    }

    /**
     * Retrieves all the properties of the given class for the given type
     *
     * @param clazz The class to retrieve the properties from
     * @param propertyType The type of the properties you wish to retrieve
     *
     * @return An array of PropertyDescriptor instances
     */
    public static PropertyDescriptor[] getPropertiesOfType(Class<?> clazz, Class<?> propertyType) {
        if (clazz == null || propertyType == null) {
            return new PropertyDescriptor[0];
        }

        Set<PropertyDescriptor> properties = new HashSet<PropertyDescriptor>();
        PropertyDescriptor descriptor = null;
        try {
            PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(clazz);
            for (int i = 0; i < descriptors.length; i++) {
                descriptor = descriptors[i];
                Class<?> currentPropertyType = descriptor.getPropertyType();
                if (isTypeInstanceOfPropertyType(propertyType, currentPropertyType)) {
                    properties.add(descriptor);
                }
            }
        }
        catch (Exception e) {
            if(descriptor == null) {
                LOG.error(String.format("Got exception while checking property descriptors for class %s", clazz.getName()), e);
            } else {
                LOG.error(String.format("Got exception while checking PropertyDescriptor.propertyType for field %s.%s", clazz.getName(), descriptor.getName()), e);
            }
            // if there are any errors in instantiating just return null for the moment
            return new PropertyDescriptor[0];
        }
        return properties.toArray(new PropertyDescriptor[properties.size()]);
    }

    private static boolean isTypeInstanceOfPropertyType(Class<?> type, Class<?> propertyType) {
        return propertyType.isAssignableFrom(type) && !propertyType.equals(Object.class);
    }

    /**
     * Retrieves all the properties of the given class which are assignable to the given type
     *
     * @param clazz             The class to retrieve the properties from
     * @param propertySuperType The type of the properties you wish to retrieve
     * @return An array of PropertyDescriptor instances
     */
    public static PropertyDescriptor[] getPropertiesAssignableToType(Class<?> clazz, Class<?> propertySuperType) {
        if (clazz == null || propertySuperType == null) return new PropertyDescriptor[0];

        Set<PropertyDescriptor> properties = new HashSet<PropertyDescriptor>();
        PropertyDescriptor descriptor = null;
        try {
            PropertyDescriptor[] descriptors = BeanUtils.getPropertyDescriptors(clazz);
            for (int i = 0; i < descriptors.length; i++) {
                descriptor = descriptors[i];
                Class<?> currentPropertyType = descriptor.getPropertyType();
                if (propertySuperType.isAssignableFrom(descriptor.getPropertyType())) {
                    properties.add(descriptor);
                }
            }
        }
        catch (Exception e) {
            if(descriptor == null) {
                LOG.error(String.format("Got exception while checking property descriptors for class %s", clazz.getName()), e);
            } else {
                LOG.error(String.format("Got exception while checking PropertyDescriptor.propertyType for field %s.%s", clazz.getName(), descriptor.getName()), e);
            }
            return new PropertyDescriptor[0];
        }
        return properties.toArray(new PropertyDescriptor[properties.size()]);
    }

    /**
     * Retrieves a property of the given class of the specified name and type
     * @param clazz The class to retrieve the property from
     * @param propertyName The name of the property
     * @param propertyType The type of the property
     *
     * @return A PropertyDescriptor instance or null if none exists
     */
    public static PropertyDescriptor getProperty(Class<?> clazz, String propertyName, Class<?> propertyType) {
        if (clazz == null || propertyName == null || propertyType == null) {
            return null;
        }

        try {
            PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(clazz, propertyName);
            if (pd != null && pd.getPropertyType().equals(propertyType)) {
                return pd;
            }
            return null;
        }
        catch (Exception e) {
            // if there are any errors in instantiating just return null for the moment
            return null;
        }
    }

    /**
     * Retrieves a property of the given class of the specified name and type
     * @param clazz The class to retrieve the property from
     * @param propertyName The name of the property
     *
     * @return A PropertyDescriptor instance or null if none exists
     */
    public static PropertyDescriptor getProperty(Class<?> clazz, String propertyName) {
        if (clazz == null || propertyName == null) {
            return null;
        }

        try {
            return BeanUtils.getPropertyDescriptor(clazz, propertyName);
        }
        catch (Exception e) {
            // if there are any errors in instantiating just return null for the moment
            return null;
        }
    }

    /**
     * Convenience method for converting a collection to an Object[]
     * @param c The collection
     * @return  An object array
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static Object[] collectionToObjectArray(Collection c) {
        if (c == null) return new Object[0];
        return c.toArray(new Object[c.size()]);
    }

    /**
     * Detect if left and right types are matching types. In particular,
     * test if one is a primitive type and the other is the corresponding
     * Java wrapper type. Primitive and wrapper classes may be passed to
     * either arguments.
     *
     * @param leftType
     * @param rightType
     * @return true if one of the classes is a native type and the other the object representation
     * of the same native type
     */
    @SuppressWarnings("rawtypes")
    public static boolean isMatchBetweenPrimativeAndWrapperTypes(Class leftType, Class rightType) {
        if (leftType == null) {
            throw new NullPointerException("Left type is null!");
        }
        if (rightType == null) {
            throw new NullPointerException("Right type is null!");
        }
        Class<?> r = PRIMITIVE_TYPE_COMPATIBLE_CLASSES.get(leftType);
        return r == rightType;
    }

    /**
     * <p>Tests whether or not the left hand type is compatible with the right hand type in Groovy
     * terms, i.e. can the left type be assigned a value of the right hand type in Groovy.</p>
     * <p>This handles Java primitive type equivalence and uses isAssignableFrom for all other types,
     * with a bit of magic for native types and polymorphism i.e. Number assigned an int.
     * If either parameter is null an exception is thrown</p>
     *
     * @param leftType The type of the left hand part of a notional assignment
     * @param rightType The type of the right hand part of a notional assignment
     * @return true if values of the right hand type can be assigned in Groovy to variables of the left hand type.
     */
    public static boolean isGroovyAssignableFrom(Class<?> leftType, Class<?> rightType) {
        if (leftType == null) {
            throw new NullPointerException("Left type is null!");
        }
        if (rightType == null) {
            throw new NullPointerException("Right type is null!");
        }
        if (leftType == Object.class) {
            return true;
        }
        if (leftType == rightType) {
            return true;
        }
        // check for primitive type equivalence
        Class<?> r = PRIMITIVE_TYPE_COMPATIBLE_CLASSES.get(leftType);
        boolean result = r == rightType;

        if (!result) {
            // If no primitive <-> wrapper match, it may still be assignable
            // from polymorphic primitives i.e. Number -> int (AKA Integer)
            if (rightType.isPrimitive()) {
                // see if incompatible
                r = PRIMITIVE_TYPE_COMPATIBLE_CLASSES.get(rightType);
                if (r != null) {
                    result = leftType.isAssignableFrom(r);
                }
            }
            else {
                // Otherwise it may just be assignable using normal Java polymorphism
                result = leftType.isAssignableFrom(rightType);
            }
        }
        return result;
    }

    /**
     * <p>Work out if the specified property is readable and static. Java introspection does not
     * recognize this concept of static properties but Groovy does. We also consider public static fields
     * as static properties with no getters/setters</p>
     *
     * @param clazz The class to check for static property
     * @param propertyName The property name
     * @return true if the property with name propertyName has a static getter method
     */
    @SuppressWarnings("rawtypes")
    public static boolean isStaticProperty(Class clazz, String propertyName) {
        Method getter = BeanUtils.findDeclaredMethod(clazz, getGetterName(propertyName), (Class[])null);
        if (getter != null) {
            return isPublicStatic(getter);
        }

        try {
            Field f = clazz.getDeclaredField(propertyName);
            if (f != null) {
                return isPublicStatic(f);
            }
        }
        catch (NoSuchFieldException ignored) {
            // ignored
        }

        return false;
    }

    /**
     * Determine whether the method is declared public static
     * @param m
     * @return true if the method is declared public static
     */
    public static boolean isPublicStatic(Method m) {
        final int modifiers = m.getModifiers();
        return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers);
    }

    /**
     * Determine whether the field is declared public static
     * @param f
     * @return true if the field is declared public static
     */
    public static boolean isPublicStatic(Field f) {
        final int modifiers = f.getModifiers();
        return Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers);
    }

    /**
     * Calculate the name for a getter method to retrieve the specified property
     * @param propertyName
     * @return The name for the getter method for this property, if it were to exist, i.e. getConstraints
     */
    public static String getGetterName(String propertyName) {
        return GrailsNameUtils.getGetterName(propertyName);
    }

    /**
     * <p>Get a static field value.</p>
     *
     * @param clazz The class to check for static property
     * @param name The field name
     * @return The value if there is one, or null if unset OR there is no such field
     */
    public static Object getStaticFieldValue(Class<?> clazz, String name) {
        Field field = ReflectionUtils.findField(clazz, name);
        if (field != null) {
            ReflectionUtils.makeAccessible(field);
            try {
                return field.get(clazz);
            } catch (IllegalAccessException ignored) {}
        }
        return null;
    }

    /**
     * <p>Get a static property value, which has a public static getter or is just a public static field.</p>
     *
     * @param clazz The class to check for static property
     * @param name The property name
     * @return The value if there is one, or null if unset OR there is no such property
     */
    public static Object getStaticPropertyValue(Class<?> clazz, String name) {
        Method getter = BeanUtils.findDeclaredMethod(clazz, getGetterName(name), (Class[])null);
        try {
            if (getter != null) {
                ReflectionUtils.makeAccessible(getter);
                return getter.invoke(clazz);
            }
            return getStaticFieldValue(clazz, name);
        }
        catch (Exception ignored) {
            // ignored
        }
        return null;
    }

    /**
     * <p>Looks for a property of the reference instance with a given name.</p>
     * <p>If found its value is returned. We follow the Java bean conventions with augmentation for groovy support
     * and static fields/properties. We will therefore match, in this order:
     * </p>
     * <ol>
     * <li>Standard public bean property (with getter or just public field, using normal introspection)
     * <li>Public static property with getter method
     * <li>Public static field
     * </ol>
     *
     * @return property value or null if no property found
     */
    public static Object getPropertyOrStaticPropertyOrFieldValue(Object obj, String name) throws BeansException {
        BeanWrapper ref = new BeanWrapperImpl(obj);
        return getPropertyOrStaticPropertyOrFieldValue(ref, obj, name);
    }

    public static Object getPropertyOrStaticPropertyOrFieldValue(BeanWrapper ref, Object obj, String name) {
        if (ref.isReadableProperty(name)) {
            return ref.getPropertyValue(name);
        }
        // Look for public fields
        if (isPublicField(obj, name)) {
            return getFieldValue(obj, name);
        }

        // Look for statics
        Class<?> clazz = obj.getClass();
        if (isStaticProperty(clazz, name)) {
            return getStaticPropertyValue(clazz, name);
        }
        return null;
    }

    /**
     * Get the value of a declared field on an object
     *
     * @param obj
     * @param name
     * @return The object value or null if there is no such field or access problems
     */
    public static Object getFieldValue(Object obj, String name) {
        Class<?> clazz = obj.getClass();
        try {
            Field f = clazz.getDeclaredField(name);
            return f.get(obj);
        }
        catch (Exception e) {
            return null;
        }
    }

    /**
     * Work out if the specified object has a public field with the name supplied.
     *
     * @param obj
     * @param name
     * @return true if a public field with the name exists
     */
    public static boolean isPublicField(Object obj, String name) {
        Class<?> clazz = obj.getClass();
        try {
            Field f = clazz.getDeclaredField(name);
            return Modifier.isPublic(f.getModifiers());
        }
        catch (NoSuchFieldException e) {
            return false;
        }
    }

    /**
     * Checks whether the specified property is inherited from a super class
     *
     * @param clz The class to check
     * @param propertyName The property name
     * @return true if the property is inherited
     */
    @SuppressWarnings("rawtypes")
    public static boolean isPropertyInherited(Class clz, String propertyName) {
        if (clz == null) return false;
        Assert.isTrue(StringUtils.hasText(propertyName), "Argument [propertyName] cannot be null or blank");

        Class<?> superClass = clz.getSuperclass();

        PropertyDescriptor pd = BeanUtils.getPropertyDescriptor(superClass, propertyName);
        if (pd != null && pd.getReadMethod() != null) {
            return true;
        }
        return false;
    }

    /**
     * Check whether the specified method is a property getter
     *
     * @param method The method
     * @return true if the method is a property getter
     */
    public static boolean isPropertyGetter(Method method) {
        return !Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()) && GrailsNameUtils.isGetter(method.getName(), method.getReturnType(), method.getParameterTypes());
    }

    /**
     * Creates a concrete collection for the suppied interface
     * @param interfaceType The interface
     * @return ArrayList for List, TreeSet for SortedSet, HashSet for Set etc.
     */
    @SuppressWarnings("rawtypes")
    public static Collection createConcreteCollection(Class interfaceType) {
        Collection elements;
        if (interfaceType.equals(List.class) || interfaceType.equals(Collection.class)) {
            elements = new ArrayList();
        }
        else if (interfaceType.equals(SortedSet.class)) {
            elements = new TreeSet();
        }
        else {
            elements = new HashSet();
        }
        return elements;
    }

    /**
     * Returns true if the name of the method specified and the number of arguments make it a javabean property setter.
     * The name is assumed to be a valid Java method name, that is not verified.
     *
     * @param name The name of the method
     * @param args The arguments
     * @return true if it is a javabean property setter
     */
    @SuppressWarnings("rawtypes")
    public static boolean isSetter(String name, Class[] args) {
        if (!StringUtils.hasText(name) || args == null)return false;

        if (name.startsWith("set")) {
            if (args.length != 1) return false;
            return GrailsNameUtils.isPropertyMethodSuffix(name.substring(3));
        }

        return false;
    }

    @SuppressWarnings("rawtypes")
    public static MetaClass getExpandoMetaClass(Class clazz) {
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        Assert.isTrue(registry.getMetaClassCreationHandler() instanceof ExpandoMetaClassCreationHandle,
                "Grails requires an instance of [ExpandoMetaClassCreationHandle] to be set in Groovy's MetaClassRegistry! (current is : "+registry.getMetaClassCreationHandler()+")");

        MetaClass mc = registry.getMetaClass(clazz);
        AdaptingMetaClass adapter = null;
        if (mc instanceof AdaptingMetaClass) {
            adapter = (AdaptingMetaClass) mc;
            mc = ((AdaptingMetaClass)mc).getAdaptee();
        }

        if (!(mc instanceof ExpandoMetaClass)) {
            // removes cached version
            registry.removeMetaClass(clazz);
            mc = registry.getMetaClass(clazz);
            if (adapter != null) {
                adapter.setAdaptee(mc);
            }
        }
        Assert.isTrue(mc instanceof ExpandoMetaClass,"BUG! Method must return an instance of [ExpandoMetaClass]!");
        return mc;
    }

    /**
     * Returns true if the specified clazz parameter is either the same as, or is a superclass or superinterface
     * of, the specified type parameter. Converts primitive types to compatible class automatically.
     *
     * @param clazz
     * @param type
     * @return true if the class is a taglib
     * @see java.lang.Class#isAssignableFrom(Class)
     */
    public static boolean isAssignableOrConvertibleFrom(Class<?> clazz, Class<?> type) {
        if (type == null || clazz == null) {
            return false;
        }
        if (type.isPrimitive()) {
            // convert primitive type to compatible class
            Class<?> primitiveClass = GrailsClassUtils.PRIMITIVE_TYPE_COMPATIBLE_CLASSES.get(type);
            if (primitiveClass == null) {
                // no compatible class found for primitive type
                return false;
            }
            return clazz.isAssignableFrom(primitiveClass);
        }
        return clazz.isAssignableFrom(type);
    }

    /**
     * Retrieves a boolean value from a Map for the given key
     *
     * @param key The key that references the boolean value
     * @param map The map to look in
     * @return A boolean value which will be false if the map is null, the map doesn't contain the key or the value is false
     */
    public static boolean getBooleanFromMap(String key, Map<?, ?> map) {
        boolean defaultValue = false;
        return getBooleanFromMap(key, map, defaultValue);
    }

    /**
     * Retrieves a boolean value from a Map for the given key
     *
     * @param key The key that references the boolean value
     * @param map The map to look in
     * @return A boolean value which will be false if the map is null, the map doesn't contain the key or the value is false
     */
    public static boolean getBooleanFromMap(String key, Map<?, ?> map, boolean defaultValue) {
        if (map == null) return defaultValue;
        if (map.containsKey(key)) {
            Object o = map.get(key);
            if (o == null) {
                return defaultValue;
            }
            if (o instanceof Boolean) {
                return (Boolean)o;
            }
            return Boolean.valueOf(o.toString());
        }
        return defaultValue;
    }

    /**
     * Locates the name of a property for the given value on the target object using Groovy's meta APIs.
     * Note that this method uses the reference so the incorrect result could be returned for two properties
     * that refer to the same reference. Use with caution.
     *
     * @param target The target
     * @param obj The property value
     * @return The property name or null
     */
    public static String findPropertyNameForValue(Object target, Object obj) {
        MetaClass mc = GroovySystem.getMetaClassRegistry().getMetaClass(target.getClass());
        List<MetaProperty> metaProperties = mc.getProperties();
        for (MetaProperty metaProperty : metaProperties) {
            if (isAssignableOrConvertibleFrom(metaProperty.getType(), obj.getClass())) {
                Object val = metaProperty.getProperty(target);
                if (val != null && val.equals(obj)) {
                    return metaProperty.getName();
                }
            }
        }
        return null;
    }

    /**
     * Retrieves the name of a setter for the specified property name
     * @param propertyName The property name
     * @return The setter equivalent
     */
    public static String getSetterName(String propertyName) {
        return GrailsNameUtils.getSetterName(propertyName);
    }

    /**
     * Returns true if the name of the method specified and the number of arguments make it a javabean property getter.
     * The name is assumed to be a valid Java method name, that is not verified.
     *
     * @param name The name of the method
     * @param args The arguments
     * @return true if it is a javabean property getter
     * @deprecated use {@link #isGetter(String, Class, Class[])} instead because this method has a defect for "is.." method with Boolean return types.
     */
    @Deprecated
    public static boolean isGetter(String name, Class<?>[] args) {
        return GrailsNameUtils.isGetter(name, boolean.class, args);
    }

    /**
     * Returns true if the name of the method specified and the number of arguments make it a javabean property getter.
     * The name is assumed to be a valid Java method name, that is not verified.
     *
     * @param name The name of the method
     * @param returnType The return type of the method
     * @param args The arguments
     * @return true if it is a javabean property getter
     */
    public static boolean isGetter(String name, Class returnType, Class<?>[] args) {
        return GrailsNameUtils.isGetter(name, returnType, args);
    }

    /**
     * Returns a property name equivalent for the given getter name or null if it is not a valid getter. If not null
     * or empty the getter name is assumed to be a valid identifier.
     *
     * @param getterName The getter name
     * @return The property name equivalent
     * @deprecated Use {@link #getPropertyForGetter(String, Class)} instead because this method has a defect for "is.." method with Boolean return types.
     */
    @Deprecated
    public static String getPropertyForGetter(String getterName) {
        return GrailsNameUtils.getPropertyForGetter(getterName);
    }

    /**
     * Returns a property name equivalent for the given getter name and return type or null if it is not a valid getter. If not null
     * or empty the getter name is assumed to be a valid identifier.
     *
     * @param getterName The getter name
     * @param returnType The type the method returns
     * @return The property name equivalent
     */
    public static String getPropertyForGetter(String getterName, Class returnType) {
        return GrailsNameUtils.getPropertyForGetter(getterName, returnType);
    }

    /**
     * Returns a property name equivalent for the given getter name and return type or null if it is not a valid getter. If not null
     * or empty the getter name is assumed to be a valid identifier.
     *
     * @param getterName The getter name
     * @param returnType The class name the method returns
     * @return The property name equivalent
     */
    public static String getPropertyForGetter(String getterName, String returnType) {
        return GrailsNameUtils.getPropertyForGetter(getterName, returnType);
    }

    /**
     * Returns a property name equivalent for the given setter name or null if it is not a valid setter. If not null
     * or empty the setter name is assumed to be a valid identifier.
     *
     * @param setterName The setter name, must be null or empty or a valid identifier name
     * @return The property name equivalent
     */
    public static String getPropertyForSetter(String setterName) {
        return GrailsNameUtils.getPropertyForSetter(setterName);
    }

    /**
     * Returns whether the specified class is either within one of the specified packages or
     * within a subpackage of one of the packages
     *
     * @param theClass The class
     * @param packageList The list of packages
     * @return true if it is within the list of specified packages
     */
    public static boolean isClassBelowPackage(Class<?> theClass, List<?> packageList) {
        String classPackage = theClass.getPackage().getName();
        for (Object packageName : packageList) {
            if (packageName != null) {
                if (classPackage.startsWith(packageName.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static Object instantiateFromConfig(ConfigObject config, String configKey, String defaultClassName)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException, LinkageError {
        return instantiateFromFlatConfig(config.flatten(), configKey, defaultClassName);
    }

    public static Object instantiateFromFlatConfig(Map<String, Object> flatConfig, String configKey, String defaultClassName)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException, LinkageError {
        String className = defaultClassName;
        Object configName = flatConfig.get(configKey);
        if (configName instanceof CharSequence) {
            className = configName.toString();
        }
        return ClassUtils.forName(className, ClassUtils.getDefaultClassLoader()).newInstance();
    }

    /**
     * Checks to see if a class is marked with @grails.artefact.Enhanced and if the enhancedFor
     * attribute of the annotation contains a specific feature name
     *
     * @param controllerClass The class to inspect
     * @param featureName The name of a feature to check for
     * @return true if controllerClass is marked with Enhanced and the enhancedFor attribute includes featureName, otherwise returns false
     * @see Enhanced
     * @see Enhanced#enhancedFor()
     */
    public static Boolean hasBeenEnhancedForFeature(final Class<?> controllerClass, final String featureName) {
        boolean hasBeenEnhanced = false;
        final Enhanced enhancedAnnotation = controllerClass.getAnnotation(Enhanced.class);
        if(enhancedAnnotation != null) {
            final String[] enhancedFor = enhancedAnnotation.enhancedFor();
            if(enhancedFor != null) {
                hasBeenEnhanced = GrailsArrayUtils.contains(enhancedFor, featureName);
            }
        }
        return hasBeenEnhanced;
    }

    public static FastClass fastClass(Class superClass) {
        FastClass.Generator gen = new FastClass.Generator();
        gen.setType(superClass);
        gen.setClassLoader(superClass.getClassLoader());
        gen.setUseCache( !Environment.isReloadingAgentEnabled() );
        return gen.create();
    }
}
