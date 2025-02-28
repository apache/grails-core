package org.grails.transaction

import grails.spring.BeanBuilder
import org.grails.config.PropertySourcesConfig
import spock.lang.Specification

class ChainedTransactionManagerPostProcessorSpec extends Specification {
    void "transactionManager bean should get replaced when there are multiple transaction manager beans"() {
        given:
        def bb = new BeanBuilder()
        def config = new PropertySourcesConfig()
        bb.beans {
            chainedTransactionManagerPostProcessor(ChainedTransactionManagerPostProcessor, config)
            transactionManager(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager")
            transactionManager_ds1(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager_ds1")
            transactionManager_ds2(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager_ds2")
        }
        when:
        def applicationContext = bb.createApplicationContext()
        def transactionManager = applicationContext.getBean("transactionManager")
        def tmNames = transactionManager.transactionManagers.collect { it.name }
        then:
        transactionManager instanceof ChainedTransactionManager
        transactionManager.transactionManagers.size()==3
        applicationContext.getBean('$primaryTransactionManager').name == 'transactionManager'
        tmNames.containsAll(['transactionManager', 'transactionManager_ds1', 'transactionManager_ds2'])
    }
    
    void "transactionManager bean should get replaced when are only 2 transaction manager beans"() {
        given:
        def bb = new BeanBuilder()
        def config = new PropertySourcesConfig()
        bb.beans {
            chainedTransactionManagerPostProcessor(ChainedTransactionManagerPostProcessor, config)
            transactionManager(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager")
            transactionManager_ds1(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager_ds1")
        }
        when:
        def applicationContext = bb.createApplicationContext()
        def transactionManager = applicationContext.getBean("transactionManager")
        def tmNames = transactionManager.transactionManagers.collect { it.name }
        then:
        transactionManager instanceof ChainedTransactionManager
        transactionManager.transactionManagers.size()==2
        applicationContext.getBean('$primaryTransactionManager').name == 'transactionManager'
        tmNames.containsAll(['transactionManager', 'transactionManager_ds1'])
    }

    void "transactionManager bean should not get replaced when there is only one transactionManager"() {
        given:
        def bb = new BeanBuilder()
        def config = new PropertySourcesConfig()
        bb.beans {
            chainedTransactionManagerPostProcessor(ChainedTransactionManagerPostProcessor, config)
            transactionManagerPostProcessor(TransactionManagerPostProcessor)
            transactionManager(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager")
        }
        when:
        def applicationContext = bb.createApplicationContext()
        def transactionManager = applicationContext.getBean("transactionManager")
        then:
        !(transactionManager instanceof ChainedTransactionManager)
    }
    
    void "transactionManager bean should not get replaced when additional datasources aren't transactional by map syntax"() {
        given:
        def bb = new BeanBuilder()
        def config = new PropertySourcesConfig()
        config['dataSources.ds1.transactional'] = false
        config['dataSources.ds2.transactional'] = false
        bb.beans {
            chainedTransactionManagerPostProcessor(ChainedTransactionManagerPostProcessor, config)
            transactionManager(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager")
            transactionManager_ds1(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager_ds1")
            transactionManager_ds2(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager_ds2")
        }
        when:
        def applicationContext = bb.createApplicationContext()
        def transactionManager = applicationContext.getBean("transactionManager")
        then:
        !(transactionManager instanceof ChainedTransactionManager)
    }

    void "transactionManager bean should not get replaced when additional datasources aren't transactional by dot syntax"() {
        given:
        def bb = new BeanBuilder()
        def config = new PropertySourcesConfig()
        config.dataSources.ds1.transactional = false
        config.dataSources.ds2.transactional = false
        bb.beans {
            chainedTransactionManagerPostProcessor(ChainedTransactionManagerPostProcessor, config)
            transactionManager(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager")
            transactionManager_ds1(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager_ds1")
            transactionManager_ds2(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager_ds2")
        }
        when:
        def applicationContext = bb.createApplicationContext()
        def transactionManager = applicationContext.getBean("transactionManager")
        then:
        !(transactionManager instanceof ChainedTransactionManager)
    }

    
    void "transactionManager bean should get replaced when one of the additional datasources is transactional"() {
        given:
        def bb = new BeanBuilder()
        def config = new PropertySourcesConfig()
        config['dataSources.ds1.transactional'] = false
        config['dataSources.ds2.transactional'] = true
        bb.beans {
            chainedTransactionManagerPostProcessor(ChainedTransactionManagerPostProcessor, config)
            transactionManager(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager")
            transactionManager_ds1(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager_ds1")
            transactionManager_ds2(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager_ds2")
        }
        when:
        def applicationContext = bb.createApplicationContext()
        def transactionManager = applicationContext.getBean("transactionManager")
        def tmNames = transactionManager.transactionManagers.collect { it.name }
        then:
        transactionManager instanceof ChainedTransactionManager
        transactionManager.transactionManagers.size()==2
        applicationContext.getBean('$primaryTransactionManager').name == 'transactionManager'
        tmNames.containsAll(['transactionManager', 'transactionManager_ds2'])
    }
    
    void "it should be possible to blacklist transaction manager beans that shouldn't be added to the chained transaction manager"() {
        given:
        def bb = new BeanBuilder()
        def config = new PropertySourcesConfig()
        bb.beans {
            def blacklistPattern='customTransactionManager'
            chainedTransactionManagerPostProcessor(ChainedTransactionManagerPostProcessor, config, null, blacklistPattern)
            transactionManager(ChainedTransactionManagerTests.TestPlatformTransactionManager, "transactionManager")
            customTransactionManager(ChainedTransactionManagerTests.TestPlatformTransactionManager, "customTransactionManager")
        }
        when:
        def applicationContext = bb.createApplicationContext()
        def transactionManager = applicationContext.getBean("transactionManager")
        then:
        !(transactionManager instanceof ChainedTransactionManager)
    }
}
