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

package org.apache.grails.testing.cleanup.core

import javax.sql.DataSource

import spock.lang.Specification

import org.spockframework.runtime.extension.IMethodInvocation
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.MethodInfo
import org.spockframework.runtime.model.SpecInfo

import org.springframework.context.ApplicationContext

class DatabaseCleanupInterceptorSpec extends Specification {

    def "interceptCleanupMethod proceeds and performs cleanup when classLevelCleanup is true"() {
        given:
        def dataSource = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> true
            cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, true, mapping, resolver)

        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getFeature() >> Mock(FeatureInfo) {
                getName() >> 'test feature'
            }
        }

        when:
        interceptor.interceptCleanupMethod(invocation)

        then: 'invocation is proceeded first'
        1 * invocation.proceed()
    }

    def "interceptCleanupMethod skips cleanup when classLevelCleanup is false and method is not annotated"() {
        given:
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = Mock(ApplicationContext)

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, false, mapping, resolver)

        def unannotatedMethod = NonAnnotatedTestClass.getDeclaredMethod('someTest')
        def methodInfo = Mock(MethodInfo) {
            isAnnotationPresent(DatabaseCleanup) >> false
            getReflection() >> unannotatedMethod
        }
        def feature = Mock(FeatureInfo) {
            getFeatureMethod() >> methodInfo
            getName() >> 'someTest'
        }
        def invocation = Mock(IMethodInvocation) {
            getFeature() >> feature
        }

        when:
        interceptor.interceptCleanupMethod(invocation)

        then:
        1 * invocation.proceed()
        0 * cleaner.cleanup(_, _)
    }

    def "interceptCleanupMethod performs cleanup when classLevelCleanup is false but method is annotated"() {
        given:
        def dataSource = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> true
            cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, false, mapping, resolver)

        def annotatedMethod = AnnotatedMethodTestClass.getDeclaredMethod('annotatedTest')
        def annotation = annotatedMethod.getAnnotation(DatabaseCleanup)
        def methodInfo = Mock(MethodInfo) {
            isAnnotationPresent(DatabaseCleanup) >> true
            getAnnotation(DatabaseCleanup) >> annotation
            getReflection() >> annotatedMethod
        }
        def feature = Mock(FeatureInfo) {
            getFeatureMethod() >> methodInfo
            getName() >> 'annotatedTest'
        }
        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getFeature() >> feature
        }

        when:
        interceptor.interceptCleanupMethod(invocation)

        then:
        1 * invocation.proceed()
        1 * cleaner.cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()
    }

    def "interceptCleanupMethod uses method-level datasource names for method-level annotation"() {
        given:
        def dataSource1 = Mock(DataSource)
        def dataSource2 = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource1, 'otherDs': dataSource2]
        }
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource1) >> true
            cleanup(appCtx, dataSource1) >> new DatabaseCleanupStats()
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, false, mapping, resolver)

        def annotatedMethod = AnnotatedMethodWithDatasource.getDeclaredMethod('annotatedTest')
        def annotation = annotatedMethod.getAnnotation(DatabaseCleanup)
        def methodInfo = Mock(MethodInfo) {
            isAnnotationPresent(DatabaseCleanup) >> true
            getAnnotation(DatabaseCleanup) >> annotation
            getReflection() >> annotatedMethod
        }
        def feature = Mock(FeatureInfo) {
            getFeatureMethod() >> methodInfo
            getName() >> 'annotatedTest'
        }
        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getFeature() >> feature
        }

        when:
        interceptor.interceptCleanupMethod(invocation)

        then:
        1 * invocation.proceed()
        // Only dataSource should be cleaned, not otherDs
        0 * cleaner.cleanup(appCtx, dataSource2)
    }

    def "interceptCleanupMethod uses method-level explicit type mapping"() {
        given:
        def dataSource = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }
        def h2Cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def pgCleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'postgresql'
        }
        def context = new DatabaseCleanupContext([h2Cleaner, pgCleaner])
        context.applicationContext = appCtx

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, false, mapping, resolver)

        def annotatedMethod = AnnotatedMethodWithExplicitType.getDeclaredMethod('annotatedTest')
        def annotation = annotatedMethod.getAnnotation(DatabaseCleanup)
        def methodInfo = Mock(MethodInfo) {
            isAnnotationPresent(DatabaseCleanup) >> true
            getAnnotation(DatabaseCleanup) >> annotation
            getReflection() >> annotatedMethod
        }
        def feature = Mock(FeatureInfo) {
            getFeatureMethod() >> methodInfo
            getName() >> 'annotatedTest'
        }
        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getFeature() >> feature
        }

        when:
        interceptor.interceptCleanupMethod(invocation)

        then:
        1 * invocation.proceed()
        1 * h2Cleaner.cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()
        0 * pgCleaner.cleanup(_, _)
    }

    def "interceptCleanupSpecMethod proceeds and performs cleanup when classLevelCleanup is true"() {
        given:
        def dataSource = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> true
            cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, true, mapping, resolver)

        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getSpec() >> Mock(SpecInfo) {
                getName() >> 'TestSpec'
            }
        }

        when:
        interceptor.interceptCleanupSpecMethod(invocation)

        then:
        1 * invocation.proceed()
    }

    def "interceptCleanupSpecMethod proceeds but skips cleanup when classLevelCleanup is false"() {
        given:
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = Mock(ApplicationContext)

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, false, mapping, resolver)

        def invocation = Mock(IMethodInvocation)

        when:
        interceptor.interceptCleanupSpecMethod(invocation)

        then:
        1 * invocation.proceed()
        0 * cleaner.cleanup(_, _)
    }

    def "ApplicationContext is resolved from instance property"() {
        given:
        def dataSource = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> true
            cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()
        }
        def context = new DatabaseCleanupContext([cleaner])
        // Do not set applicationContext - let the interceptor resolve it

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, true, mapping, resolver)

        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getFeature() >> Mock(FeatureInfo) {
                getName() >> 'test feature'
            }
        }

        when:
        interceptor.interceptCleanupMethod(invocation)

        then:
        1 * invocation.proceed()

        and: 'applicationContext was resolved and set on the context'
        context.applicationContext.is(appCtx)
    }

    def "interceptCleanupMethod throws when ApplicationContext cannot be resolved"() {
        given:
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
        }
        def context = new DatabaseCleanupContext([cleaner])
        // Do not set applicationContext - let the interceptor try to resolve it

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, true, mapping, resolver)

        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithNoContext()
            getFeature() >> Mock(FeatureInfo) {
                getName() >> 'test feature'
            }
        }

        when:
        interceptor.interceptCleanupMethod(invocation)

        then:
        1 * invocation.proceed()

        and:
        def ex = thrown(IllegalStateException)
        ex.message.contains('Could not resolve ApplicationContext')
    }

      def "interceptCleanupMethod prints formatted stats when debug property is set"() {
        given:
        System.setProperty(DatabaseCleanupStats.DEBUG_PROPERTY, 'true')

        def stats = new DatabaseCleanupStats()
        stats.tableRowCounts['BOOK'] = 5L
        stats.tableRowCounts['AUTHOR'] = 3L

        def dataSource = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> true
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, true, mapping, resolver)

        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getFeature() >> Mock(FeatureInfo) {
                getName() >> 'test feature'
            }
        }

        def originalOut = System.out
        def baos = new ByteArrayOutputStream()
        System.out = new PrintStream(baos)

        when:
        interceptor.interceptCleanupMethod(invocation)

        then:
        1 * invocation.proceed()
        1 * cleaner.cleanup(appCtx, dataSource) >> stats

        and: 'overall timing information is printed'
        String output = baos.toString()
        output.contains('Overall Cleanup Timing')
        output.contains('Start Time:')
        output.contains('End Time:')
        output.contains('Duration:')

        and: 'individual datasource stats are printed'
        output.contains('Database Cleanup Stats (datasource: dataSource)')
        output.contains('BOOK')
        output.contains('AUTHOR')

        cleanup:
        System.out = originalOut
        System.clearProperty(DatabaseCleanupStats.DEBUG_PROPERTY)
    }

    def "interceptCleanupMethod does not print stats when debug property is not set"() {
        given:
        // Ensure the property is not set
        System.clearProperty(DatabaseCleanupStats.DEBUG_PROPERTY)

        def stats = new DatabaseCleanupStats()
        stats.tableRowCounts['BOOK'] = 5L

        def dataSource = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> true
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, true, mapping, resolver)

        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getFeature() >> Mock(FeatureInfo) {
                getName() >> 'test feature'
            }
        }

        def originalOut = System.out
        def baos = new ByteArrayOutputStream()
        System.out = new PrintStream(baos)

        when:
        interceptor.interceptCleanupMethod(invocation)

        then:
        1 * invocation.proceed()
        1 * cleaner.cleanup(appCtx, dataSource) >> stats

        and: 'no formatted stats output to stdout'
        String output = baos.toString()
        !output.contains('Database Cleanup Stats')

        cleanup:
        System.out = originalOut
    }

    def "cleanup still runs even if test method fails"() {
        given:
        def dataSource = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> true
            cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, true, mapping, resolver)

        def testFailure = new RuntimeException('Test failed')
        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getFeature() >> Mock(FeatureInfo) {
                getName() >> 'test feature'
            }
            proceed() >> { throw testFailure }
        }

        when:
        interceptor.interceptCleanupMethod(invocation)

        then: 'cleanup was performed before exception is re-thrown'
        1 * cleaner.cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()

        and: 'the original test exception is re-thrown'
        RuntimeException ex = thrown(RuntimeException)
        ex.is(testFailure)
    }

    def "cleanup still runs even if cleanupSpec method fails"() {
        given:
        def dataSource = Mock(DataSource)
        def appCtx = Mock(ApplicationContext) {
            getBeansOfType(DataSource) >> ['dataSource': dataSource]
        }
        def cleaner = Mock(DatabaseCleaner) {
            databaseType() >> 'h2'
            supports(dataSource) >> true
            cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()
        }
        def context = new DatabaseCleanupContext([cleaner])
        context.applicationContext = appCtx

        def mapping = DatasourceCleanupMapping.parse(new String[0])
        def resolver = new DefaultApplicationContextResolver()
        def interceptor = new DatabaseCleanupInterceptor(context, true, mapping, resolver)

        def specFailure = new RuntimeException('Cleanup spec failed')
        def invocation = Mock(IMethodInvocation) {
            getInstance() >> new InstanceWithAppCtx(applicationContext: appCtx)
            getSpec() >> Mock(SpecInfo) {
                getName() >> 'TestSpec'
            }
            proceed() >> { throw specFailure }
        }

        when:
        interceptor.interceptCleanupSpecMethod(invocation)

        then: 'cleanup was performed before exception is re-thrown'
        1 * cleaner.cleanup(appCtx, dataSource) >> new DatabaseCleanupStats()

        and: 'the original spec exception is re-thrown'
        RuntimeException ex = thrown(RuntimeException)
        ex.is(specFailure)
    }

    // --- Helper classes ---

    static class InstanceWithAppCtx {
        ApplicationContext applicationContext
    }

    static class InstanceWithNoContext {
        String name = 'test'
    }

    static class NonAnnotatedTestClass {
        void someTest() {}
    }

    static class AnnotatedMethodTestClass {
        @DatabaseCleanup
        void annotatedTest() {}
    }

    static class AnnotatedMethodWithDatasource {
        @DatabaseCleanup(['dataSource'])
        void annotatedTest() {}
    }

    static class AnnotatedMethodWithExplicitType {
        @DatabaseCleanup(['dataSource:h2'])
        void annotatedTest() {}
    }
}
