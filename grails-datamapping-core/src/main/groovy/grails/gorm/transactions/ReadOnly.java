/*
 * Copyright 2017 the original author or authors.
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
package grails.gorm.transactions;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;
import org.grails.datastore.gorm.transform.GormASTTransformationClass;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;

import java.lang.annotation.*;

/**
 * Define a read-only transaction
 *
 * @since 6.1
 * @author Graeme Rocher
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Documented
@GroovyASTTransformationClass("org.grails.datastore.gorm.transform.OrderedGormTransformation")
@GormASTTransformationClass("org.grails.datastore.gorm.transactions.transform.TransactionalTransform")
public @interface ReadOnly {
    /**
     * A qualifier value for the specified transaction.
     * <p>May be used to determine the target transaction manager,
     * matching the qualifier value (or the bean name) of a specific
     * {@link org.springframework.transaction.PlatformTransactionManager}
     * bean definition.
     */
    String value() default "";

    /**
     * The transaction propagation type.
     * Defaults to {@link org.springframework.transaction.annotation.Propagation#REQUIRED}.
     * @see org.springframework.transaction.interceptor.TransactionAttribute#getPropagationBehavior()
     */
    Propagation propagation() default Propagation.REQUIRED;

    /**
     * The transaction isolation level.
     * Defaults to {@link org.springframework.transaction.annotation.Isolation#DEFAULT}.
     * @see org.springframework.transaction.interceptor.TransactionAttribute#getIsolationLevel()
     */
    Isolation isolation() default Isolation.DEFAULT;

    /**
     * The timeout for this transaction.
     * Defaults to the default timeout of the underlying transaction system.
     * @see org.springframework.transaction.interceptor.TransactionAttribute#getTimeout()
     */
    int timeout() default TransactionDefinition.TIMEOUT_DEFAULT;

    /**
     * Defines zero (0) or more exception {@link Class classes}, which must be a
     * subclass of {@link Throwable}, indicating which exception types must cause
     * a transaction rollback.
     * <p>This is the preferred way to construct a rollback rule, matching the
     * exception class and subclasses.
     * <p>Similar to {@link org.springframework.transaction.interceptor.RollbackRuleAttribute#RollbackRuleAttribute(Class clazz)}
     */
    Class<? extends Throwable>[] rollbackFor() default {};

    /**
     * Defines zero (0) or more exception names (for exceptions which must be a
     * subclass of {@link Throwable}), indicating which exception types must cause
     * a transaction rollback.
     * <p>This can be a substring, with no wildcard support at present.
     * A value of "ServletException" would match
     * jakarta.servlet.ServletException and subclasses, for example.
     * <p><b>NB: </b>Consider carefully how specific the pattern is, and whether
     * to include package information (which isn't mandatory). For example,
     * "Exception" will match nearly anything, and will probably hide other rules.
     * "java.lang.Exception" would be correct if "Exception" was meant to define
     * a rule for all checked exceptions. With more unusual {@link Exception}
     * names such as "BaseBusinessException" there is no need to use a FQN.
     * <p>Similar to {@link org.springframework.transaction.interceptor.RollbackRuleAttribute#RollbackRuleAttribute(String exceptionName)}
     */
    String[] rollbackForClassName() default {};

    /**
     * Defines zero (0) or more exception {@link Class Classes}, which must be a
     * subclass of {@link Throwable}, indicating which exception types must <b>not</b>
     * cause a transaction rollback.
     * <p>This is the preferred way to construct a rollback rule, matching the
     * exception class and subclasses.
     * <p>Similar to {@link org.springframework.transaction.interceptor.NoRollbackRuleAttribute#NoRollbackRuleAttribute(Class clazz)}
     */
    Class<? extends Throwable>[] noRollbackFor() default {};

    /**
     * Defines zero (0) or more exception names (for exceptions which must be a
     * subclass of {@link Throwable}) indicating which exception types must <b>not</b>
     * cause a transaction rollback.
     * <p>See the description of {@link #rollbackForClassName()} for more info on how
     * the specified names are treated.
     * <p>Similar to {@link org.springframework.transaction.interceptor.NoRollbackRuleAttribute#NoRollbackRuleAttribute(String exceptionName)}
     */
    String[] noRollbackForClassName() default {};


    /**
     * In Spring, when there are nested transaction calls, the execution of the outermost callback will throw UnexpectedRollbackException if TransactionStatus.setRollbackOnly() was called in a nested transaction callback.
     *
     * This feature will make the setRollbackOnly state get inherited to parent level transaction template calls and therefore prevent UnexpectedRollbackException.
     * The default value is true.
     *
     */
    boolean inheritRollbackOnly() default true;

    /**
     * If you are using multiple GORM implementations and wish to create a transaction for a specific implementation then use this. For example {@code @Transactional(forDatastore=HibernateDatastore) }
     *
     * @return The type of the datastore
     */
    Class<? extends ConnectionSourcesProvider>[] datastore() default {};

    /**
     * The connection to use by default
     */
    String connection() default ConnectionSource.DEFAULT;
}
