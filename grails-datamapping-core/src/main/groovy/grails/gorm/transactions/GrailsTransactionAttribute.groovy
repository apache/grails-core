/*
 * Copyright 2015 original authors
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
package grails.gorm.transactions

import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.interceptor.NoRollbackRuleAttribute
import org.springframework.transaction.interceptor.RollbackRuleAttribute
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute



/**
 * Used to configure a {@link GrailsTransactionTemplate}
 *
 * @author Graeme Rocher
 * @since 5.0
 */
@CompileStatic
@InheritConstructors
@Slf4j
class GrailsTransactionAttribute extends RuleBasedTransactionAttribute {
    private static final long serialVersionUID = 1L;
    private boolean inheritRollbackOnly = true;

    public GrailsTransactionAttribute(org.springframework.transaction.interceptor.TransactionAttribute other) {
        super();
        propagationBehavior = other.propagationBehavior
        isolationLevel = other.isolationLevel
        timeout = other.timeout
        readOnly = other.readOnly
        name = other.name
    }

    public GrailsTransactionAttribute(TransactionDefinition other) {
        super();
        propagationBehavior = other.propagationBehavior
        isolationLevel = other.isolationLevel
        timeout = other.timeout
        readOnly = other.readOnly
        name = other.name
    }

    public GrailsTransactionAttribute(GrailsTransactionAttribute other) {
        this((RuleBasedTransactionAttribute)other);
    }

    public GrailsTransactionAttribute(RuleBasedTransactionAttribute other) {
        super(other)
        if(other instanceof GrailsTransactionAttribute) {
            this.inheritRollbackOnly = ((GrailsTransactionAttribute)other).inheritRollbackOnly
        }
    }

    public boolean isInheritRollbackOnly() {
        return inheritRollbackOnly;
    }

    @Override
    boolean rollbackOn(Throwable ex) {
        if (log.isTraceEnabled()) {
            log.trace("Applying rules to determine whether transaction should rollback on $ex");
        }

        RollbackRuleAttribute winner = null
        int deepest = Integer.MAX_VALUE

        List<RollbackRuleAttribute> rollbackRules = getRollbackRules()
        if (rollbackRules != null) {
            for (RollbackRuleAttribute rule in rollbackRules) {
                int depth = rule.getDepth(ex)
                if (depth >= 0 && depth < deepest) {
                    deepest = depth
                    winner = rule
                }
            }
        }

        if (log.isTraceEnabled()) {
            log.trace("Winning rollback rule is: $winner" );
        }

        // User superclass behavior (rollback on unchecked) if no rule matches.
        if (winner == null) {
            log.trace("No relevant rollback rule found: applying default rules")

            // always rollback regardless if it is a checked or unchecked exception since Groovy doesn't differentiate those
            return true
        }

        return !(winner instanceof NoRollbackRuleAttribute)
    }

    public void setInheritRollbackOnly(boolean inheritRollbackOnly) {
        this.inheritRollbackOnly = inheritRollbackOnly;
    }
}
