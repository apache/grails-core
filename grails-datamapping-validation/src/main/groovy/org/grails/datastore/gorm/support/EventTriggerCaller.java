package org.grails.datastore.gorm.support;

import groovy.lang.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.ReflectionUtils;

public abstract class EventTriggerCaller {
    private static final Log LOG = LogFactory.getLog(EventTriggerCaller.class);
    private static final Object[] EMPTY_ARRAY = {};
    private boolean invertBooleanReturnValue=true;
    private static final EventTriggerCaller noopCaller = new NoopCaller();
    
    public static EventTriggerCaller buildCaller(String eventMethodName, Class<?> clazz) {
        return buildCaller(eventMethodName, clazz, null, null);
    }
    
    public static EventTriggerCaller buildCaller(String eventMethodName, Class<?> clazz, MetaClass metaClass, Class<?>[] preferredArgumentTypes) {
        EventTriggerCaller caller = resolveMethodCaller(eventMethodName, clazz, preferredArgumentTypes);
        if(caller==null) {
            caller=resolveFieldClosureCaller(eventMethodName, clazz);
        }
        if(caller==null) {
            caller=resolveMetaClassCallers(eventMethodName, clazz, metaClass);
        }
        return caller;
    }

    private static EventTriggerCaller resolveMetaClassCallers(String eventMethodName, Class<?> clazz, MetaClass metaClass) {
        if(metaClass==null) {
            metaClass=GroovySystem.getMetaClassRegistry().getMetaClass(clazz);
        }
        EventTriggerCaller caller = resolveMetaMethodCaller(eventMethodName, metaClass);
        if(caller==null) {
            caller = resolveMetaPropertyClosureCaller(eventMethodName, metaClass);
        }
        return caller;
    }

    private static EventTriggerCaller resolveMetaPropertyClosureCaller(String eventMethodName, MetaClass metaClass) {
        MetaProperty metaProperty = metaClass.getMetaProperty(eventMethodName);
        if (metaProperty != null) {
            return new MetaPropertyClosureCaller(metaProperty);
        }
        return null;
    }

    private static EventTriggerCaller resolveMetaMethodCaller(String eventMethodName, MetaClass metaClass) {
        MetaMethod metaMethod = metaClass.getMetaMethod(eventMethodName, EMPTY_ARRAY);
        if (metaMethod != null) {
            return new MetaMethodCaller(metaMethod);
        }
        return null;
    }

    private static EventTriggerCaller resolveFieldClosureCaller(String eventMethodName, Class<?> clazz) {
        Field field = ReflectionUtils.findField(clazz, eventMethodName);
        if (field != null) {
            ReflectionUtils.makeAccessible(field);
            return new FieldClosureCaller(field);
        }
        return null;
    }

    private static EventTriggerCaller resolveMethodCaller(String eventMethodName, Class<?> clazz, Class<?>[] preferredArgumentTypes) {
        Method method = ReflectionUtils.findMethod(clazz, eventMethodName, preferredArgumentTypes);
        if (method == null && preferredArgumentTypes != null) {
            method = ReflectionUtils.findMethod(clazz, eventMethodName);
            if(method == null) {
                method = ReflectionUtils.findMethod(clazz, eventMethodName, (Class<?>[])null);
            }
        }
        if (method != null) {
            ReflectionUtils.makeAccessible(method);
            return new MethodCaller(method);
        }
        return null;
    }
    
    public static EventTriggerCaller wrapNullInNoopCaller(EventTriggerCaller caller) {
        return caller != null ? caller : noopCaller;
    }
    
    public final boolean call(Object entity) {
        return call(entity, EMPTY_ARRAY);
    }

    public abstract boolean call(Object entity, Object[] argumentArray);
    
    public boolean isNoOperationCaller() {
        return false;
    }
    
    public boolean asBoolean() {
        return !isNoOperationCaller();
    }

    boolean resolveReturnValue(Object retval) {
        if (retval instanceof Boolean) {
            boolean returnValue = (Boolean)retval;
            return invertBooleanReturnValue ? !returnValue : returnValue;
        }
        return false;
    }

    public boolean isInvertBooleanReturnValue() {
        return invertBooleanReturnValue;
    }

    public void setInvertBooleanReturnValue(boolean invertBooleanReturnValue) {
        this.invertBooleanReturnValue = invertBooleanReturnValue;
    }
    
    private static class NoopCaller extends EventTriggerCaller {
        @Override
        public boolean call(Object entity, Object[] argumentArray) {
            return false;
        }
        
        public boolean isNoOperationCaller() {
            return true;
        }
    }
    
    private static class MethodCaller extends EventTriggerCaller {
        Method method;
        int numberOfParameters;

        MethodCaller(Method method) {
            this.method = method;
            this.numberOfParameters = method.getParameterTypes().length;
        }

        @Override
        public boolean call(Object entity, Object[] argumentArray) {
            Object[] arguments = new Object[numberOfParameters];
            if(argumentArray != null) {
                for(int i=0;i < argumentArray.length && i < arguments.length;i++) {
                    arguments[i] = argumentArray[i];
                }
            }
            Object retval = ReflectionUtils.invokeMethod(method, entity, arguments);
            return resolveReturnValue(retval);
        }
    }

    private static class MetaMethodCaller extends EventTriggerCaller {
        MetaMethod method;
        int numberOfParameters;

        MetaMethodCaller(MetaMethod method) {
            this.method = method;
            this.numberOfParameters = method.getParameterTypes().length;
        }

        @Override
        public boolean call(Object entity, Object[] argumentArray) {
            Object retval = method.doMethodInvoke(entity, numberOfParameters > 0 ? argumentArray : EMPTY_ARRAY);
            return resolveReturnValue(retval);
        }
    }

    private static abstract class ClosureCaller extends EventTriggerCaller {
        boolean cloneFirst = false;

        Object callClosure(Object entity, Closure<?> callable, Object[] argumentArray) {
            if (cloneFirst) {
                callable = (Closure<?>)callable.clone();
            }
            callable.setResolveStrategy(Closure.DELEGATE_FIRST);
            callable.setDelegate(entity);
            return callable.call(callable.getMaximumNumberOfParameters() > 0 ? argumentArray : EMPTY_ARRAY);
        }
    }

    private static class FieldClosureCaller extends ClosureCaller {
        Field field;

        FieldClosureCaller(Field field) {
            this.field = field;
            if (Modifier.isStatic(field.getModifiers())) {
                cloneFirst = true;
            }
        }

        @Override
        public boolean call(Object entity, Object[] argumentArray) {
            Object fieldval = ReflectionUtils.getField(field, entity);
            if (fieldval instanceof Closure) {
                return resolveReturnValue(callClosure(entity, (Closure<?>) fieldval, argumentArray));
            }
            LOG.error("Field " + field + " is not Closure or method.");
            return false;
        }
    }

    private static class MetaPropertyClosureCaller extends ClosureCaller {
        MetaProperty metaProperty;

        MetaPropertyClosureCaller(MetaProperty metaProperty) {
            this.metaProperty = metaProperty;
            if (Modifier.isStatic(metaProperty.getModifiers())) {
                cloneFirst = true;
            }
        }

        @Override
        public boolean call(Object entity, Object[] argumentArray) {
            Object fieldval = metaProperty.getProperty(entity);
            if (fieldval instanceof Closure) {
                return resolveReturnValue(callClosure(entity, (Closure<?>) fieldval, argumentArray));
            }
            LOG.error("Field " + metaProperty + " is not Closure.");
            return false;
        }
    }
}