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
package org.grails.plugins;

import junit.framework.TestCase;

/**
 * NOTE: This test covers the old plug-in system. The system has been heavily re-worked. Please see
 * PluginSystemTests in src/groovy
 *
 * @author graemerocher
 *
 */
public class PluginTests {

    public void testTempPluginTests() {
        // does nothing. All of the below is commented out because it relates to
        // the original plugin proposal which has now been removed. However there
        // may still be a place for a test with this name
    }
 /*   GenericApplicationContext appCtx;

    protected void setUp() throws Exception {
        if (appCtx != null) {
            return;
        }
        appCtx = new GenericApplicationContext();

        Resource[] resources = new Resource[2];
        resources[0] = new ClassPathResource("org/codehaus/groovy/grails/plugins/grails-app/services/TestService.groovy");
        resources[1] = new ClassPathResource("org/codehaus/groovy/grails/plugins/grails-app/conf/PooledApplicationDataSource.groovy");

        GrailsApplication application = new DefaultGrailsApplication(resources);
        GrailsPluginLoader.loadPlugins(appCtx, application, "classpath*:org/codehaus/groovy/grails/plugins/*.xml");

        appCtx.refresh();
    }

    public void testAwareBeanPostProcessorsAreRegistered() {
        Map grailsApplicationAwares = appCtx.getBeansOfType(GrailsApplicationAware.class);
        assertEquals(2, grailsApplicationAwares.size());

        Map classLoaderAwares = appCtx.getBeansOfType(ClassLoaderAware.class);
        assertEquals(1, classLoaderAwares.size());
    }

    public void testTestBeanGetClass() {
        TestBean bean = (TestBean)appCtx.getBean("myTestBean1", TestBean.class);
        assertSame(GenericApplicationContext.class, bean.getMyClass());
    }

    public void testGrailsExceptionResolverRequired() {
        GrailsExceptionResolver exceptionResolver = (GrailsExceptionResolver)appCtx.getBean("exceptionHandler", GrailsExceptionResolver.class);
    }

    public void testCommonsMultipartResolverRequired() {
        CommonsMultipartResolver multipartResolver = (CommonsMultipartResolver)appCtx.getBean("multipartResolver", CommonsMultipartResolver.class);
    }

    public void testReloadableResourceBundleMessageSourceRequired() {
        ReloadableResourceBundleMessageSource messageSource = (ReloadableResourceBundleMessageSource)appCtx.getBean("messageSource", ReloadableResourceBundleMessageSource.class);
    }

    public void testLocaleChangeInterceptorRequired() {
        LocaleChangeInterceptor interceptor = (LocaleChangeInterceptor) appCtx.getBean("localeChangeInterceptor", LocaleChangeInterceptor.class);
    }

    public void testCookieLocaleResolverRequired() {
        CookieLocaleResolver localeResolver = (CookieLocaleResolver) appCtx.getBean("localeResolver", CookieLocaleResolver.class);
    }

    public void testTestServiceRequired() {
        Object testService = appCtx.getBean("TestServiceService");
        AopUtils.isAopProxy(testService);
    }

//    public void testHibernateDialectDetectorRequired() {
//        HibernateDialectDetectorFactoryBean dialectDetector = (HibernateDialectDetectorFactoryBean) appCtx.getBean("&dialectDetector", HibernateDialectDetectorFactoryBean.class);
//    }

    public static class MyGrailsApplicationAware implements GrailsApplicationAware {
        public void setGrailsApplication(GrailsApplication grailsApplication) {

        }
    }

    public static class MyClassLoaderAware implements ClassLoaderAware {
        public void setCompilerConfiguration(ClassLoader classLoader) {

        }
    }

    public static class MyAwarePlugin implements GrailsPlugin {
        public void doWithApplicationContext(GenericApplicationContext applicationContext, GrailsApplication application) {
            RootBeanDefinition bd = new RootBeanDefinition(MyGrailsApplicationAware.class);

            applicationContext.registerBeanDefinition(MyGrailsApplicationAware.class.getName(), bd);

            RootBeanDefinition bd2 = new RootBeanDefinition(MyClassLoaderAware.class);

            applicationContext.registerBeanDefinition(MyClassLoaderAware.class.getName(), bd2);
        }
    }

    public static class TestBean {
        private Class myClass;

        public Class getMyClass() {
            return myClass;
        }

        public void setMyClass(Class myClass) {
            this.myClass = myClass;
        }
    }

    public static class MyClassEditorPlugin implements GrailsPlugin {
        public void doWithApplicationContext(GenericApplicationContext applicationContext, GrailsApplication application) {
            RootBeanDefinition bd = new RootBeanDefinition(TestBean.class);
            MutablePropertyValues mpv = new MutablePropertyValues();
            mpv.addPropertyValue("myClass", "org.springframework.context.support.GenericApplicationContext");
            bd.setPropertyValues(mpv);

            applicationContext.registerBeanDefinition("myTestBean1", bd);
        }
    }*/
}
