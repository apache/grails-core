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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.DefaultTransactionAttribute;
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;

/**
 * Extended version of {@link RuleBasedTransactionAttribute} that ensures all exception types are rolled back and allows inheritance of setRollbackOnly
 *
 * @author Graeme Rocher
 * @since 3.0
 */
public class GrailsTransactionAttribute extends RuleBasedTransactionAttribute {

    private static final Logger log = LoggerFactory.getLogger(GrailsTransactionAttribute.class);

    private static final long serialVersionUID = 1L;
    private boolean inheritRollbackOnly = true;

    public GrailsTransactionAttribute() {
        super();
    }

    public GrailsTransactionAttribute(int propagationBehavior, List<RollbackRuleAttribute> rollbackRules) {
        super(propagationBehavior, rollbackRules);
    }

    public GrailsTransactionAttribute(TransactionAttribute other) {
        this((TransactionDefinition) other);
    }

    public GrailsTransactionAttribute(TransactionDefinition other) {
        super();
        setPropagationBehavior(other.getPropagationBehavior());
        setIsolationLevel(other.getIsolationLevel());
        setTimeout(other.getTimeout());
        setReadOnly(other.isReadOnly());
        setName(other.getName());
        if (other instanceof TransactionAttribute attribute) {
            copyAttributeState(attribute);
        }
        if (other instanceof RuleBasedTransactionAttribute ruleBased) {
            // Spring's copy constructor snapshots the source's rule list from the field, unlike
            // getRollbackRules() which would lazily assign a new list into the source object
            setRollbackRules(new RuleBasedTransactionAttribute(ruleBased).getRollbackRules());
        }
        copyGrailsState(other);
    }

    public GrailsTransactionAttribute(GrailsTransactionAttribute other) {
        this((RuleBasedTransactionAttribute) other);
    }

    public GrailsTransactionAttribute(RuleBasedTransactionAttribute other) {
        super(other);
        copyAttributeState(other);
        copyGrailsState(other);
    }

    /**
     * Copies the attribute-level state that Spring's copy constructors do not carry over.
     * As of Spring Framework 7.0, {@code DefaultTransactionAttribute(TransactionAttribute)}
     * only copies the {@link TransactionDefinition} fields.
     */
    private void copyAttributeState(TransactionAttribute other) {
        if (other instanceof DefaultTransactionAttribute defaultAttribute) {
            setDescriptor(defaultAttribute.getDescriptor());
            setTimeoutString(defaultAttribute.getTimeoutString());
        }
        setQualifier(other.getQualifier());
        Collection<String> labels = other.getLabels();
        if (labels != null) {
            // defensive copy: setLabels stores the given reference
            setLabels(new ArrayList<>(labels));
        }
    }

    private void copyGrailsState(TransactionDefinition other) {
        if (other instanceof GrailsTransactionAttribute grailsAttribute) {
            this.inheritRollbackOnly = grailsAttribute.inheritRollbackOnly;
        }
    }

    @Override
    public boolean rollbackOn(Throwable ex) {
        if (log.isTraceEnabled()) {
            log.trace("Applying rules to determine whether transaction should rollback on " + ex);
        }

        RollbackRuleAttribute winner = null;
        int deepest = Integer.MAX_VALUE;

        List<RollbackRuleAttribute> rollbackRules = getRollbackRules();
        if (rollbackRules != null) {
            for (RollbackRuleAttribute rule : rollbackRules) {
                int depth = rule.getDepth(ex);
                if (depth >= 0 && depth < deepest) {
                    deepest = depth;
                    winner = rule;
                }
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Winning rollback rule is: " + winner);
        }

        // User superclass behavior (rollback on unchecked) if no rule matches.
        if (winner == null) {
            if (log.isTraceEnabled()) {
                log.trace("No relevant rollback rule found: applying default rules");
            }

            // always rollback regardless if it is a checked or unchecked exception since Groovy doesn't differentiate those
            return true;
        }

        return !(winner instanceof NoRollbackRuleAttribute);
    }

    public boolean isInheritRollbackOnly() {
        return inheritRollbackOnly;
    }

    public void setInheritRollbackOnly(boolean inheritRollbackOnly) {
        this.inheritRollbackOnly = inheritRollbackOnly;
    }
}
