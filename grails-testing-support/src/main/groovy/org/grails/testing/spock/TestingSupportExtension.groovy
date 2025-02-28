package org.grails.testing.spock

import grails.testing.spring.AutowiredTest
import groovy.transform.CompileStatic
import org.grails.testing.GrailsUnitTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.spockframework.runtime.extension.IGlobalExtension
import org.spockframework.runtime.model.MethodInfo
import org.spockframework.runtime.model.MethodKind
import org.spockframework.runtime.model.SpecInfo

import java.lang.annotation.Annotation
import java.lang.reflect.Method
import java.lang.reflect.Modifier

@CompileStatic
class TestingSupportExtension implements IGlobalExtension {

    AutowiredInterceptor autowiredInterceptor = new AutowiredInterceptor()
    CleanupContextInterceptor cleanupContextInterceptor = new CleanupContextInterceptor()

    @Override
    void visitSpec(SpecInfo spec) {
        if (AutowiredTest.isAssignableFrom(spec.reflection)) {
            spec.addSetupInterceptor(autowiredInterceptor)
        }
        if (GrailsUnitTest.isAssignableFrom(spec.reflection)) {
            spec.addCleanupSpecInterceptor(cleanupContextInterceptor)
        }
        for (Method method : (spec.getReflection().declaredMethods)) {
            if (method.isAnnotationPresent(BeforeEach.class)) {
                spec.setupMethods.add(0, createJUnitFixtureMethod(spec, method, MethodKind.SETUP, BeforeEach.class))
            }
            if (method.isAnnotationPresent(AfterEach.class)) {
                spec.addCleanupMethod(createJUnitFixtureMethod(spec, method, MethodKind.CLEANUP, AfterEach.class))
            }
            if (method.isAnnotationPresent(BeforeAll.class)) {
                spec.setupSpecMethods.add(0, createJUnitFixtureMethod(spec, method, MethodKind.SETUP_SPEC, BeforeAll.class))
            }
            if (method.isAnnotationPresent(AfterAll.class)) {
                spec.addCleanupSpecMethod(createJUnitFixtureMethod(spec, method, MethodKind.CLEANUP_SPEC, AfterAll.class))
            }
        }
    }

    private static MethodInfo createMethod(SpecInfo specInfo, Method method, MethodKind kind, String name) {
        MethodInfo methodInfo = new MethodInfo()
        methodInfo.parent = specInfo
        methodInfo.name = name
        methodInfo.reflection = method
        methodInfo.kind = kind
        return methodInfo
    }

    private static MethodInfo createJUnitFixtureMethod(SpecInfo specInfo, Method method, MethodKind kind, Class<? extends Annotation> annotation) {
        MethodInfo methodInfo = createMethod(specInfo, method, kind, method.name)
        methodInfo.excluded = isOverriddenJUnitFixtureMethod(specInfo, method, annotation)
        return methodInfo
    }

    private static boolean isOverriddenJUnitFixtureMethod(SpecInfo specInfo, Method method, Class<? extends Annotation> annotation) {
        if (Modifier.isPrivate(method.modifiers)) return false

        for (Class<?> currClass = specInfo.class; currClass != specInfo.class.superclass; currClass = currClass.superclass) {
            for (Method currMethod : currClass.declaredMethods) {
                if (!currMethod.isAnnotationPresent(annotation)) continue
                if (currMethod.name != method.name) continue
                if (!Arrays.deepEquals(currMethod.parameterTypes, method.parameterTypes)) continue
                return true
            }
        }

        return false
    }
}
