/*
 * Copyright (c) 2011 the original author or authors.
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

package grails.plugins.quartz.config

import grails.plugins.quartz.CustomTriggerFactoryBean
import grails.plugins.quartz.GrailsJobClassConstants as Constants
import grails.util.GrailsUtil
import grails.core.GrailsApplication
import org.quartz.CronExpression
import org.quartz.SimpleTrigger
import org.quartz.Trigger
import org.quartz.impl.triggers.CronTriggerImpl
import org.quartz.impl.triggers.SimpleTriggerImpl

/**
 * Groovy Builder for parsing triggers configuration info.
 *
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 *
 * @since 0.3
 */
class TriggersConfigBuilder extends BuilderSupport {

    private triggerNumber = 0
    private jobName
    GrailsApplication grailsApplication
    def triggers = [:]

    TriggersConfigBuilder(String jobName, GrailsApplication grailsApplication) {
        this.jobName = jobName
        this.grailsApplication = grailsApplication
    }

    /**
     * Evaluate triggers closure
     */
    def build(closure) {
        closure.delegate = this
        closure.call()
        return triggers
    }

    /**
     * Create a trigger.
     *
     * @param name the name of the method to create trigger. It's trigger type: simple, cron, custom.
     * @param attributes trigger attributes
     * @return trigger definitions
     */
    Expando createTrigger(name, Map attributes) {
        def triggerClass

        def triggerAttributes = attributes ? new HashMap(attributes) : [:]

        prepareCommonTriggerAttributes(triggerAttributes)

        String triggerType = normalizeTriggerType(name)

        switch (triggerType) {
            case 'simple':
                triggerClass = SimpleTriggerImpl
                prepareSimpleTriggerAttributes(triggerAttributes)
                break
            case 'cron':
                triggerClass = CronTriggerImpl
                prepareCronTriggerAttributes(triggerAttributes)
                break
            case 'custom':
                if (!triggerAttributes?.triggerClass) {
                    throw new Exception("Custom trigger must have 'triggerClass' attribute")
                }
                triggerClass = (Class) triggerAttributes.remove('triggerClass')
                if (!Trigger.isAssignableFrom(triggerClass)) {
                    throw new Exception('Custom trigger class must implement org.quartz.Trigger class.')
                }
                break
            default:
                throw new Exception('Invalid format')
        }

        new Expando(clazz: CustomTriggerFactoryBean, triggerClass: triggerClass, triggerAttributes: triggerAttributes)
    }

    /**
     * Convert old trigger types' names
     *
     * @param old or new trigger type
     * @return new trigger type
     */
    private String normalizeTriggerType(name) {
        def triggerType = name

        if (triggerType == 'simpleTrigger') {
            GrailsUtil.deprecated("You're using deprecated 'simpleTrigger' construction in the ${jobName}, use 'simple' instead.")
            triggerType = 'simple'
        } else if (triggerType == 'cronTrigger') {
            GrailsUtil.deprecated("You're using deprecated 'cronTrigger' construction in the ${jobName}, use 'cron' instead.")
            triggerType = 'cron'
        } else if (triggerType == 'customTrigger') {
            GrailsUtil.deprecated("You're using deprecated 'customTrigger' construction in the ${jobName}, use 'custom' instead.")
            triggerType = 'custom'
        }
        triggerType
    }

    private prepareCommonTriggerAttributes(Map triggerAttributes) {
        def prepare = prepareTriggerAttribute.curry(triggerAttributes)

        if (triggerAttributes[Constants.NAME] == null) {
            triggerAttributes[Constants.NAME] = "${jobName}${triggerNumber++}".toString()
        }

        prepare(Constants.GROUP, Constants.DEFAULT_TRIGGERS_GROUP.toString())
        prepare(
                Constants.START_DELAY,
                Constants.DEFAULT_START_DELAY,
                {
                    if (!(it instanceof Integer || it instanceof Long)) {
                        throw new IllegalArgumentException(
                                "startDelay trigger property in the job class ${jobName} must be Integer or Long"
                        )
                    }
                    if (((Number) it).longValue() < 0) {
                        throw new IllegalArgumentException(
                                "startDelay trigger property in the job class ${jobName} is negative (possibly integer overflow error)"
                        )
                    }
                }
        )
    }

    private prepareSimpleTriggerAttributes(Map triggerAttributes) {
        def prepare = prepareTriggerAttribute.curry(triggerAttributes)

        // Process the old deprecated property "timeout"
        if (triggerAttributes[Constants.TIMEOUT] != null) {
            GrailsUtil.deprecated("You're using deprecated 'timeout' property in the ${jobName}, use 'repeatInterval' instead")

            if (!(triggerAttributes[Constants.TIMEOUT] instanceof Integer || triggerAttributes[Constants.TIMEOUT] instanceof Long)) {
                throw new IllegalArgumentException(
                        "timeout trigger property in the job class ${jobName} must be Integer or Long"
                )
            }
            if (((Number) triggerAttributes[Constants.TIMEOUT]).longValue() < 0) {
                throw new IllegalArgumentException(
                        "timeout trigger property for job class ${jobName} is negative (possibly integer overflow error)"
                )
            }
            triggerAttributes[Constants.REPEAT_INTERVAL] = triggerAttributes.remove(Constants.TIMEOUT)
        }

        // Validate repeat interval
        prepare(
                Constants.REPEAT_INTERVAL,
                Constants.DEFAULT_REPEAT_INTERVAL,
                {
                    if (!(it instanceof Integer || it instanceof Long)) {
                        throw new IllegalArgumentException("repeatInterval trigger property in the job class ${jobName} must be Integer or Long")
                    }
                    if (((Number) it).longValue() < 0) {
                        throw new IllegalArgumentException("repeatInterval trigger property for job class ${jobName} is negative (possibly integer overflow error)")
                    }
                }
        )

        // Validate repeat count
        prepare(
                Constants.REPEAT_COUNT,
                Constants.DEFAULT_REPEAT_COUNT,
                {
                    if (!(it instanceof Integer || it instanceof Long)) {
                        throw new IllegalArgumentException(
                                "repeatCount trigger property in the job class ${jobName} must be Integer or Long"
                        )
                    }
                    if (((Number) it).longValue() < 0 && ((Number) it).longValue() != SimpleTrigger.REPEAT_INDEFINITELY) {
                        throw new IllegalArgumentException(
                                "repeatCount trigger property for job class ${jobName} is negative (possibly integer overflow error)"
                        )
                    }
                }
        )
    }

    private prepareCronTriggerAttributes(Map triggerAttributes) {
        prepareTriggerAttribute(
                triggerAttributes,
                Constants.CRON_EXPRESSION,
                Constants.DEFAULT_CRON_EXPRESSION,
                {
                    if (!CronExpression.isValidExpression(it.toString())) {
                        throw new IllegalArgumentException(
                                "Cron expression '${it}' in the job class ${jobName} is not a valid cron expression"
                        )
                    }
                }
        )
    }

    private prepareTriggerAttribute = { Map attributes, String name, defaultValue, validate = {} ->
        if (attributes[name] == null) {
            attributes[name] = defaultValue
        }
        validate(attributes[name])
    }

    /**
     * Does nothing. Implements the BuilderSupport method.
     */
    protected void setParent(parent, child) {
        // Nothing!
    }

    /**
     * Implements the BuilderSupport method.
     */
    protected createNode(name) {
        createNode(name, null, null)
    }

    /**
     * Implements the BuilderSupport method.
     */
    protected createNode(name, value) {
        createNode(name, null, value)
    }

    /**
     * Implements the BuilderSupport method.
     */
    protected createNode(name, Map attributes) {
        createNode(name, attributes, null)
    }

    /**
     * Create a trigger. Implements the BuilderSupport method.
     */
    protected Object createNode(name, Map attributes, Object value) {
        def trigger = createTrigger(name, attributes)
        triggers[trigger.triggerAttributes.name.toString()] = trigger
        trigger
    }
}
