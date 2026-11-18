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

package grails.plugins.quartz;

import java.util.HashMap;
import java.util.Map;

import groovy.lang.Closure;

import org.quartz.JobExecutionContext;

import grails.plugins.quartz.config.TriggersConfigBuilder;
import grails.util.GrailsClassUtils;
import org.grails.core.AbstractGrailsClass;

import static grails.plugins.quartz.GrailsJobClassConstants.CONCURRENT;
import static grails.plugins.quartz.GrailsJobClassConstants.DEFAULT_CONCURRENT;
import static grails.plugins.quartz.GrailsJobClassConstants.DEFAULT_DESCRIPTION;
import static grails.plugins.quartz.GrailsJobClassConstants.DEFAULT_DURABILITY;
import static grails.plugins.quartz.GrailsJobClassConstants.DEFAULT_ENABLED;
import static grails.plugins.quartz.GrailsJobClassConstants.DEFAULT_GROUP;
import static grails.plugins.quartz.GrailsJobClassConstants.DEFAULT_REQUESTS_RECOVERY;
import static grails.plugins.quartz.GrailsJobClassConstants.DEFAULT_SESSION_REQUIRED;
import static grails.plugins.quartz.GrailsJobClassConstants.DESCRIPTION;
import static grails.plugins.quartz.GrailsJobClassConstants.DURABILITY;
import static grails.plugins.quartz.GrailsJobClassConstants.ENABLED;
import static grails.plugins.quartz.GrailsJobClassConstants.EXECUTE;
import static grails.plugins.quartz.GrailsJobClassConstants.GROUP;
import static grails.plugins.quartz.GrailsJobClassConstants.REQUESTS_RECOVERY;
import static grails.plugins.quartz.GrailsJobClassConstants.SESSION_REQUIRED;

/**
 * Grails artifact class which represents a Quartz job.
 *
 * @author Micha?? K??ujszo
 * @author Marcel Overdijk
 * @author Sergey Nebolsin (nebolsin@gmail.com)
 * @since 0.1
 */
public class DefaultGrailsJobClass extends AbstractGrailsClass implements GrailsJobClass {

    public static final String JOB = "Job";
    private Map triggers = new HashMap();
    private boolean triggersEvaluated = false;

    public DefaultGrailsJobClass(Class clazz) {
        super(clazz, JOB);
    }

    private void evaluateTriggers() {
        // registering additional triggersClosure from 'triggersClosure' closure if present
        Closure triggersClosure = (Closure) GrailsClassUtils.getStaticPropertyValue(getClazz(), "triggers");

        TriggersConfigBuilder builder = new TriggersConfigBuilder(getFullName(), grailsApplication);

        if (triggersClosure != null) {
            builder.build(triggersClosure);
            triggers = (Map) builder.getTriggers();
        }
        triggersEvaluated = true;
    }

    public void execute() {
        getMetaClass().invokeMethod(getReferenceInstance(), EXECUTE, new Object[]{});
    }

    public void execute(JobExecutionContext context) {
        getMetaClass().invokeMethod(getReferenceInstance(), EXECUTE, new Object[]{context});
    }

    public String getGroup() {
        String group = getStaticPropertyValue(GROUP, String.class);
        if (group == null || "".equals(group)) return DEFAULT_GROUP;
        return group;
    }

    public boolean isConcurrent() {
        Boolean concurrent = getStaticPropertyValue(CONCURRENT, Boolean.class);
        return concurrent == null ? DEFAULT_CONCURRENT : concurrent;
    }

    public boolean isSessionRequired() {
        Boolean sessionRequired = getStaticPropertyValue(SESSION_REQUIRED, Boolean.class);
        return sessionRequired == null ? DEFAULT_SESSION_REQUIRED : sessionRequired;
    }

    public boolean isDurability() {
        Boolean durability = getStaticPropertyValue(DURABILITY, Boolean.class);
        return durability == null ? DEFAULT_DURABILITY : durability;
    }

    public boolean isRequestsRecovery() {
        Boolean requestsRecovery = getStaticPropertyValue(REQUESTS_RECOVERY, Boolean.class);
        return requestsRecovery == null ? DEFAULT_REQUESTS_RECOVERY : requestsRecovery;
    }

    public boolean isEnabled() {
        Boolean enabled = getStaticPropertyValue(ENABLED, Boolean.class);
        return enabled == null ? DEFAULT_ENABLED : enabled;
    }

    public String getDescription() {
        String description = (String) getPropertyOrStaticPropertyOrFieldValue(DESCRIPTION, String.class);
        if (description == null || "".equals(description)) return DEFAULT_DESCRIPTION;
        return description;
    }

    public Map getTriggers() {
        if (triggersEvaluated == false) {
            evaluateTriggers();
        }
        return triggers;
    }
}
