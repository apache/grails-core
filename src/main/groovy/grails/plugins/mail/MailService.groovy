/*
 * Copyright 2008-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.plugins.mail

import grails.config.Config
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.PropertiesPropertySource
import org.springframework.mail.MailMessage

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Provides the entry point to the mail sending API.
 */
@Slf4j
@CompileStatic
class MailService implements InitializingBean, DisposableBean {

	MailConfigurationProperties mailConfigurationProperties
    MailMessageBuilderFactory mailMessageBuilderFactory

	private ThreadPoolExecutor mailExecutorService

	private static final Integer DEFAULT_POOL_SIZE = 5
	private static final Bindable<MailConfigurationProperties> CONFIG_BINDABLE = Bindable.of(MailConfigurationProperties)

    MailMessage sendMail(MailConfigurationProperties properties, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MailMessageBuilder) Closure callable) {
        if (disabled) {
            log.warn('Sending emails disabled by configuration option')
            return null
        }
        def messageBuilder = mailMessageBuilderFactory.createBuilder(properties)
        callable.delegate = messageBuilder
        callable.resolveStrategy = Closure.DELEGATE_FIRST
        callable.call(messageBuilder)
        return messageBuilder.sendMessage(mailExecutorService)
    }

	MailMessage sendMail(Config config, @DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MailMessageBuilder) Closure callable) {
		return sendMail(toMailProperties(config), callable)
	}

    MailMessage sendMail(@DelegatesTo(strategy = Closure.DELEGATE_FIRST, value = MailMessageBuilder) Closure callable) {
        return sendMail(mailConfigurationProperties, callable)
    }

	private static MailConfigurationProperties toMailProperties(Config config) {
		def propertySource = new PropertiesPropertySource('mailProperties', config.toProperties())
		def configurationPropertySources = ConfigurationPropertySources.from(propertySource)
		def binder = new Binder(configurationPropertySources)
		return binder.bind(MailConfigurationProperties.PREFIX, CONFIG_BINDABLE).get()
	}

    boolean isDisabled() {
        mailConfigurationProperties.disabled
    }

	void setPoolSize(Integer poolSize){
		mailExecutorService.setMaximumPoolSize(poolSize ?: DEFAULT_POOL_SIZE)
		mailExecutorService.setCorePoolSize(poolSize ?: DEFAULT_POOL_SIZE)
	}

	@Override
	void destroy() throws Exception {
		mailExecutorService.shutdown()
		mailExecutorService.awaitTermination(10, TimeUnit.SECONDS)
	}

	@Override
	void afterPropertiesSet() throws Exception {
		mailExecutorService = new ThreadPoolExecutor(1, 1, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>())
		mailExecutorService.allowCoreThreadTimeOut(true)
		setPoolSize(mailConfigurationProperties.poolSize)
	}
}
