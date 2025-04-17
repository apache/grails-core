package org.grails.taglib

import grails.core.gsp.GrailsTagLibClass
import grails.util.GrailsClassUtils
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.codehaus.groovy.reflection.CachedMethod
import org.codehaus.groovy.runtime.metaclass.MethodSelectionException
import org.grails.taglib.encoder.OutputContextLookupHelper
import org.springframework.context.ApplicationContext

class TagLibraryMetaUtils {
    // used for testing (GroovyPageUnitTestMixin.mockTagLib) and "nonEnhancedTagLibClasses" in GroovyPagesGrailsPlugin
    private final static Object[] EMPTY_OBJECT_ARRAY = new Object[0]

    @CompileStatic
    static void enhanceTagLibMetaClass(final GrailsTagLibClass taglib, TagLibraryLookup gspTagLibraryLookup) {
        final MetaClass mc = taglib.getMetaClass()
        final String namespace = taglib.namespace ?: TagOutput.DEFAULT_NAMESPACE
        enhanceTagLibMetaClass(mc, gspTagLibraryLookup, namespace)
    }

    @CompileStatic
    public static void enhanceTagLibMetaClass(MetaClass mc, TagLibraryLookup gspTagLibraryLookup, String namespace) {
        registerTagMetaMethods(mc, gspTagLibraryLookup, namespace)
        registerNamespaceMetaProperties(mc, gspTagLibraryLookup)
    }

    @CompileStatic
    static void registerNamespaceMetaProperties(MetaClass mc, TagLibraryLookup gspTagLibraryLookup) {
        for(String ns : gspTagLibraryLookup.getAvailableNamespaces()) {
            registerNamespaceMetaProperty(mc, gspTagLibraryLookup, ns)
        }
    }
    
    @CompileStatic
    static void registerNamespaceMetaProperty(MetaClass metaClass, TagLibraryLookup gspTagLibraryLookup, String namespace) {
        if(!metaClass.hasProperty(namespace) && !doesMethodExist(metaClass, GrailsClassUtils.getGetterName(namespace), [] as Class[])) {
            registerPropertyMissingForTag(metaClass, namespace, gspTagLibraryLookup.lookupNamespaceDispatcher(namespace))
        }
    }

    @CompileStatic
    static registerMethodMissingForTags(MetaClass metaClass, TagLibraryLookup gspTagLibraryLookup, String namespace, String name, boolean addAll=true, boolean overrideMethods=true) {
        GroovyObject mc = (GroovyObject)metaClass;
        
        if(overrideMethods || !doesMethodExist(metaClass, name, [Map, Closure] as Class[])) {
            mc.setProperty(name) {Map attrs, Closure body ->
                TagOutput.captureTagOutput(gspTagLibraryLookup, namespace, name, attrs, body, OutputContextLookupHelper.lookupOutputContext())
            }
        }
        if(overrideMethods || !doesMethodExist(metaClass, name, [Map, CharSequence] as Class[])) {
            mc.setProperty(name) {Map attrs, CharSequence body ->
                TagOutput.captureTagOutput(gspTagLibraryLookup, namespace, name, attrs, new TagOutput.ConstantClosure(body), OutputContextLookupHelper.lookupOutputContext())
            }
        }
        if(overrideMethods || !doesMethodExist(metaClass, name, [Map] as Class[])) {
            mc.setProperty(name) {Map attrs ->
                TagOutput.captureTagOutput(gspTagLibraryLookup, namespace, name, attrs, null, OutputContextLookupHelper.lookupOutputContext())
            }
        }
        if (addAll) {
            if(overrideMethods || !doesMethodExist(metaClass, name, [Closure] as Class[])) {
                mc.setProperty(name) {Closure body ->
                    TagOutput.captureTagOutput(gspTagLibraryLookup, namespace, name, [:], body, OutputContextLookupHelper.lookupOutputContext())
                }
            }
            if(overrideMethods || !doesMethodExist(metaClass, name, [] as Class[])) {
                mc.setProperty(name) {->
                    TagOutput.captureTagOutput(gspTagLibraryLookup, namespace, name, [:], null, OutputContextLookupHelper.lookupOutputContext())
                }
            }
        }
    }

    static registerMethodMissingForTags(MetaClass mc, ApplicationContext ctx,
                                        GrailsTagLibClass tagLibraryClass, String name) {
        TagLibraryLookup gspTagLibraryLookup = ctx.getBean("gspTagLibraryLookup")
        String namespace = tagLibraryClass.namespace ?: TagOutput.DEFAULT_NAMESPACE
        registerMethodMissingForTags(mc, gspTagLibraryLookup, namespace, name)
    }

    @CompileStatic
    static void registerPropertyMissingForTag(MetaClass metaClass, String name, Object result) {
        GroovyObject mc = (GroovyObject)metaClass;
        mc.setProperty(GrailsClassUtils.getGetterName(name)) {-> result }
    }
    
    @CompileStatic
    static void registerTagMetaMethods(MetaClass emc, TagLibraryLookup lookup, String namespace) {
        for(String tagName : lookup.getAvailableTags(namespace)) {
            boolean addAll = !(namespace == TagOutput.DEFAULT_NAMESPACE && tagName == 'hasErrors')
            registerMethodMissingForTags(emc, lookup, namespace, tagName, addAll, false)
        }
        if (namespace != TagOutput.DEFAULT_NAMESPACE) {
            registerTagMetaMethods(emc, lookup, TagOutput.DEFAULT_NAMESPACE)
        }
    }
    
    @CompileStatic
    protected static boolean doesMethodExist(final MetaClass mc, final String methodName, final Class[] parameterTypes, boolean staticScope=false, boolean onlyReal=false) {
        boolean methodExists = false
        try {
            MetaMethod existingMethod = mc.pickMethod(methodName, parameterTypes)
            if(existingMethod && existingMethod.isStatic()==staticScope && (!onlyReal || isRealMethod(existingMethod)) && parameterTypes.length==existingMethod.parameterTypes.length)  {
                methodExists = true
            }
        } catch (MethodSelectionException mse) {
            // the metamethod already exists with multiple signatures, must check if the exact method exists
            methodExists = mc.methods.contains { MetaMethod existingMethod ->
                existingMethod.name == methodName && existingMethod.isStatic()==staticScope && (!onlyReal || isRealMethod(existingMethod)) && ((!parameterTypes && !existingMethod.parameterTypes) || Arrays.equals(parameterTypes, existingMethod.getNativeParameterTypes()))
            }
        }
    }
        
    @CompileStatic
    private static boolean isRealMethod(MetaMethod existingMethod) {
        existingMethod instanceof CachedMethod
    }

    private static Object[] makeObjectArray(Object args) {
        args instanceof Object[] ? (Object[])args : [args] as Object[]
    }

    @CompileStatic(TypeCheckingMode.SKIP) // workaround for GROOVY-6147 bug
    static Object methodMissingForTagLib(MetaClass mc, Class type, TagLibraryLookup gspTagLibraryLookup, String namespace, String name, Object argsParam, boolean addMethodsToMetaClass) {
        Object[] args = makeObjectArray(argsParam)
        final GroovyObject tagBean = gspTagLibraryLookup.lookupTagLibrary(namespace, name)
        if (tagBean != null) {
            MetaClass tagBeanMc = tagBean.getMetaClass()
            final MetaMethod method=tagBeanMc.respondsTo(tagBean, name, args).find{ it }
            if (method != null) {
                if (addMethodsToMetaClass) {
                    // add all methods with the same name to metaclass at once to prevent "wrong number of arguments" exception
                    for (MetaMethod m in tagBeanMc.respondsTo(tagBean, name)) {
                        addTagLibMethodToMetaClass(tagBean, m, mc)
                    }
                }
                return method.invoke(tagBean, args)
            }
        }
        throw new MissingMethodException(name, type, args)
    }

    static addTagLibMethodToMetaClass(final GroovyObject tagBean, final MetaMethod method, final MetaClass mc) {
        Class[] paramTypes = method.nativeParameterTypes
        Closure methodMissingClosure = null
        switch(paramTypes.length) {
            case 0:
                methodMissingClosure = {->
                    method.invoke(tagBean, EMPTY_OBJECT_ARRAY)
                }
                break
            case 1:
                if (paramTypes[0] == Closure) {
                    methodMissingClosure = { Closure body ->
                        method.invoke(tagBean, body)
                    }
                } else if (paramTypes[0]==Map) {
                    methodMissingClosure = { Map attrs->
                        method.invoke(tagBean, attrs)
                    }
                } else {
                    methodMissingClosure = { Object attrs->
                        method.invoke(tagBean, attrs)
                    }
                }
                break
            case 2:
                if (paramTypes[0] == Map) {
                    if (paramTypes[1] == Closure) {
                        methodMissingClosure = { Map attrs, Closure body ->
                            method.invoke(tagBean, attrs, body)
                        }
                    } else if (paramTypes[1] == CharSequence) {
                        methodMissingClosure = { Map attrs, CharSequence body ->
                            method.invoke(tagBean, attrs, body)
                        }
                    } else if (paramTypes[1] == String) {
                        methodMissingClosure = { Map attrs, String body ->
                            method.invoke(tagBean, attrs, body)
                        }
                    } else {
                        methodMissingClosure = { Map attrs, Object body ->
                            method.invoke(tagBean, attrs, body)
                        }
                    }
                }
                break
        }
        if (methodMissingClosure != null) {
            synchronized(mc) {
                ((GroovyObject)mc).setProperty(method.name, methodMissingClosure)
            }
        }
    }
}
