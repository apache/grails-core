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

package org.grails.transaction;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.Ordered;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.util.ClassUtils;

import grails.config.Config;
import org.grails.config.PropertySourcesConfig;

/**
 *  A {@link BeanDefinitionRegistryPostProcessor} for using the "Best Effort 1 Phase Commit" (BE1PC) in Grails
 *  applications when there are multiple data sources.
 *
 *  When the context contains multiple transactionManager beans, the bean with the name "transactionManager"
 *  will be renamed to "$primaryTransactionManager" and a new ChainedTransactionManager bean will be added with the name
 *  "transactionManager". All transactionManager beans will be registered in the ChainedTransactionManager bean.
 *
 *  The post processor checks if the previous transactionManager bean is an instance of {@link JtaTransactionManager}.
 *  In that case it will not do anything since it's assumed that JTA/XA is handling transactions spanning multiple datasources.
 *
 *  For performance reasons an additional dataSource can be marked as non-transactional by adding a property 'transactional = false' in
 *  it's dataSource configuration. This will leave the dataSource out of the transactions initiated by Grails transactions.
 *  This is the default behaviour in Grails versions before Grails 2.3.6 .
 *
 *
 * @author Lari Hotari
 * @since 2.3.6
 *
 */
public class ChainedTransactionManagerPostProcessor implements BeanDefinitionRegistryPostProcessor, Ordered {
    private static final String TRANSACTIONAL = "transactional";
    private static final String DEFAULT_TRANSACTION_MANAGER_BEAN_NAME_WHITELIST_PATTERN = "(?i).*transactionManager(_.+)?";
    private static final String DEFAULT_TRANSACTION_MANAGER_INTERNAL_BEAN_NAME_BLACKLIST_PATTERN = "(?i)chainedTransactionManagerPostProcessor|transactionManagerPostProcessor|.*PostProcessor";
    public static final String DATA_SOURCE_SETTING = "dataSource";
    public static final String DATA_SOURCES_SETTING = "dataSources";
    public static final String DATA_SOURCES_PREFIX = "dataSources.";
    private String beanNameWhitelistPattern = DEFAULT_TRANSACTION_MANAGER_BEAN_NAME_WHITELIST_PATTERN;
    private String beanNameBlacklistPattern = null;
    private String beanNameInternalBlacklistPattern = DEFAULT_TRANSACTION_MANAGER_INTERNAL_BEAN_NAME_BLACKLIST_PATTERN;
    private static final Pattern SUFFIX_PATTERN = Pattern.compile("^transactionManager_(.+)$");
    private static final String PRIMARY_TRANSACTION_MANAGER = "$primaryTransactionManager";
    private static final String TRANSACTION_MANAGER = "transactionManager";
    private static final String READONLY = "readOnly";

    private Config config;

    private Map<String, Map> dsConfigs;
    private static String[] transactionManagerBeanNames = null;

    public ChainedTransactionManagerPostProcessor(Config config) {
        this(config, null, null);
    }

    public ChainedTransactionManagerPostProcessor(Config config, String whitelistPattern, String blacklistPattern) {
        transactionManagerBeanNames = null;
        this.config = config;
        if (whitelistPattern != null) {
            beanNameWhitelistPattern = whitelistPattern;
        }
        if (blacklistPattern != null) {
            beanNameBlacklistPattern = blacklistPattern;
        }
    }

    public ChainedTransactionManagerPostProcessor() {
        this(new PropertySourcesConfig(), null, null);
    }


    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {

    }

    protected void registerAdditionalTransactionManagers(BeanDefinitionRegistry registry, BeanDefinition chainedTransactionManagerBeanDefinition, ManagedList<RuntimeBeanReference> transactionManagerRefs) {
        String[] allBeanNames = getTransactionManagerBeanNames(registry);
        for (String beanName : allBeanNames) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
            if(!TRANSACTION_MANAGER.equals(beanName) && !PRIMARY_TRANSACTION_MANAGER.equals(beanName) && isValidTransactionManagerBeanDefinition(beanName, beanDefinition)) {
                String suffix = resolveDataSourceSuffix(beanName);
                if (!isNotTransactional(suffix)) {
                    transactionManagerRefs.add(new RuntimeBeanReference(beanName));
                }
            }
        }
    }


    protected static String[] getTransactionManagerBeanNames(BeanDefinitionRegistry registry) {
        if(transactionManagerBeanNames == null) {

            if(registry instanceof ListableBeanFactory) {
                transactionManagerBeanNames =  ((ListableBeanFactory)registry).getBeanNamesForType(PlatformTransactionManager.class, false, false);
            }
            else {
                transactionManagerBeanNames = registry.getBeanDefinitionNames();
            }
        }
        return transactionManagerBeanNames;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (registry.containsBeanDefinition(TRANSACTION_MANAGER) && countChainableTransactionManagerBeans(registry) > 1 && !hasJtaOrChainedTransactionManager(registry)) {
            BeanDefinition chainedTransactionManagerBeanDefinition = addChainedTransactionManager(registry);
            ManagedList<RuntimeBeanReference> transactionManagerRefs = createTransactionManagerBeanReferences(chainedTransactionManagerBeanDefinition);
            registerAdditionalTransactionManagers(registry, chainedTransactionManagerBeanDefinition, transactionManagerRefs);
        }
    }

    protected ManagedList<RuntimeBeanReference> createTransactionManagerBeanReferences(
            BeanDefinition chainedTransactionManagerBeanDefinition) {
        ManagedList<RuntimeBeanReference> transactionManagerRefs = new ManagedList<RuntimeBeanReference>();
        ConstructorArgumentValues constructorValues=chainedTransactionManagerBeanDefinition.getConstructorArgumentValues();
        constructorValues.addIndexedArgumentValue(0, transactionManagerRefs);
        transactionManagerRefs.add(new RuntimeBeanReference(PRIMARY_TRANSACTION_MANAGER));
        return transactionManagerRefs;
    }

    protected BeanDefinition addChainedTransactionManager(BeanDefinitionRegistry registry) {
        renameBean(TRANSACTION_MANAGER, PRIMARY_TRANSACTION_MANAGER, registry);
        BeanDefinition beanDefinition = new RootBeanDefinition(ChainedTransactionManager.class);
        beanDefinition.setPrimary(true);
        registry.registerBeanDefinition(TRANSACTION_MANAGER, beanDefinition);
        return beanDefinition;
    }

    protected boolean hasJtaOrChainedTransactionManager(BeanDefinitionRegistry registry) {
        Class<?> transactionManagerBeanClass = resolveTransactionManagerClass(registry);
        if (transactionManagerBeanClass == null) {
            return false;
        }
        boolean isJtaTransactionManager = JtaTransactionManager.class.isAssignableFrom(transactionManagerBeanClass);
        boolean isChainedTransactionManager = ChainedTransactionManager.class.isAssignableFrom(transactionManagerBeanClass);
        return isJtaTransactionManager || isChainedTransactionManager;
    }

    protected Class<?> resolveTransactionManagerClass(BeanDefinitionRegistry registry) {
        if(!registry.containsBeanDefinition(TRANSACTION_MANAGER)) {
            return null;
        }
        BeanDefinition transactionManagerBeanDefinition = registry.getBeanDefinition(TRANSACTION_MANAGER);
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Class<?> transactionManagerBeanClass = ClassUtils.resolveClassName(transactionManagerBeanDefinition.getBeanClassName(), classLoader);
        return transactionManagerBeanClass;
    }

    protected int countChainableTransactionManagerBeans(BeanDefinitionRegistry registry) {
        int transactionManagerBeanCount=0;
        for (String beanName : getTransactionManagerBeanNames(registry)) {
            BeanDefinition beanDefinition = registry.getBeanDefinition(beanName);
            if(isValidTransactionManagerBeanDefinition(beanName, beanDefinition)) {
                String suffix = resolveDataSourceSuffix(beanName);
                if (beanName.equals(TRANSACTION_MANAGER) || !isNotTransactional(suffix)) {
                    transactionManagerBeanCount++;
                }
            }
        }
        return transactionManagerBeanCount;
    }

    protected boolean isValidTransactionManagerBeanDefinition(String beanName, BeanDefinition beanDefinition) {
        return beanName.matches(beanNameWhitelistPattern) && (beanNameBlacklistPattern==null || !beanName.matches(beanNameBlacklistPattern)) && !beanName.matches(beanNameInternalBlacklistPattern);
    }

    protected boolean isNotTransactional(String suffix) {
        if (suffix == null || config == null) {
            return false;
        }
        Boolean transactional = config.getProperty(DATA_SOURCES_PREFIX + suffix + "." + TRANSACTIONAL, Boolean.class, null);
        if(transactional == null) {
            Boolean isReadOnly =  config.getProperty(DATA_SOURCES_PREFIX + suffix + "." + READONLY, Boolean.class, null);
            if (isReadOnly != null && isReadOnly == true) {
                transactional = false;
            }
        }
        if(transactional != null){
            return !transactional;
        }
        else {
            return false;
        }
    }

    protected String resolveDataSourceSuffix(String transactionManagerBeanName) {
        if(TRANSACTION_MANAGER.equals(transactionManagerBeanName)) {
            return "";
        } else {
            Matcher matcher=SUFFIX_PATTERN.matcher(transactionManagerBeanName);
            if(matcher.matches()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private static boolean renameBean(String oldName, String newName, BeanDefinitionRegistry registry) {
        if(!registry.containsBeanDefinition(oldName)) {
            return false;
        }
        // remove link to child beans
        Set<String> previousChildBeans = new LinkedHashSet<String>();
        for (String bdName : getTransactionManagerBeanNames(registry)) {
            if (!oldName.equals(bdName)) {
                BeanDefinition bd = registry.getBeanDefinition(bdName);
                if (oldName.equals(bd.getParentName())) {
                    bd.setParentName(null);
                    previousChildBeans.add(bdName);
                }
            }
        }
        BeanDefinition oldBeanDefinition = registry.getBeanDefinition(oldName);
        oldBeanDefinition.setPrimary(false);
        registry.removeBeanDefinition(oldName);
        registry.registerBeanDefinition(newName, oldBeanDefinition);
        // re-link possible child beans to new parent name
        for(String bdName : previousChildBeans) {
            BeanDefinition bd = registry.getBeanDefinition(bdName);
            bd.setParentName(newName);
        }
        return true;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 200;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public String getBeanNameWhitelistPattern() {
        return beanNameWhitelistPattern;
    }

    public void setBeanNameWhitelistPattern(String beanNameWhitelistPattern) {
        this.beanNameWhitelistPattern = beanNameWhitelistPattern;
    }

    public String getBeanNameBlacklistPattern() {
        return beanNameBlacklistPattern;
    }

    public void setBeanNameBlacklistPattern(String beanNameBlacklistPattern) {
        this.beanNameBlacklistPattern = beanNameBlacklistPattern;
    }

    public String getBeanNameInternalBlacklistPattern() {
        return beanNameInternalBlacklistPattern;
    }

    public void setBeanNameInternalBlacklistPattern(String beanNameInternalBlacklistPattern) {
        this.beanNameInternalBlacklistPattern = beanNameInternalBlacklistPattern;
    }
}
