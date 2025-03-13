package org.grails.plugins.databasemigration

import grails.gorm.transactions.GrailsTransactionTemplate
import org.springframework.context.ApplicationContext
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.DefaultTransactionDefinition
import org.springframework.util.Assert

/**
 * Created by Jim on 7/15/2016.
 */
class DatabaseMigrationTransactionManager {

    final String dataSource
    final ApplicationContext applicationContext

    DatabaseMigrationTransactionManager(ApplicationContext applicationContext, String dataSource) {
        this.dataSource = dataSource
        this.applicationContext = applicationContext
    }

    /**
     *
     * @return The transactionManager bean for the current dataSource
     */
    PlatformTransactionManager getTransactionManager() {
        String dataSource = this.dataSource ?: "dataSource"
        String beanName = "transactionManager"
        if (dataSource != "dataSource") {
            beanName += "_${dataSource}"
        }
        applicationContext.getBean(beanName, PlatformTransactionManager)
    }

    /**
     * Executes the closure within the context of a transaction, creating one if none is present or joining
     * an existing transaction if one is already present.
     *
     * @param callable The closure to call
     * @return The result of the closure execution
     * @see #withTransaction(Map, Closure)
     * @see #withNewTransaction(Closure)
     * @see #withNewTransaction(Map, Closure)
     */
    void withTransaction(Closure callable) {
        withTransaction(new DefaultTransactionDefinition(), callable)
    }

    /**
     * Executes the closure within the context of a new transaction
     *
     * @param callable The closure to call
     * @return The result of the closure execution
     * @see #withTransaction(Closure)
     * @see #withTransaction(Map, Closure)
     * @see #withNewTransaction(Map, Closure)
     */
    void withNewTransaction(Closure callable) {
        withTransaction([propagationBehavior: TransactionDefinition.PROPAGATION_REQUIRES_NEW], callable)
    }

    /**
     * Executes the closure within the context of a new transaction which is
     * configured with the properties contained in transactionProperties.
     * transactionProperties may contain any properties supported by
     * {@link DefaultTransactionDefinition}.  Note that if transactionProperties
     * includes entries for propagationBehavior or propagationName, those values
     * will be ignored.  This method always sets the propagation level to
     * TransactionDefinition.REQUIRES_NEW.
     *
     * <blockquote>
     * <pre>
     * SomeEntity.withNewTransaction([isolationLevel: TransactionDefinition.ISOLATION_REPEATABLE_READ]) {
     *     // ...
     * }
     * </pre>
     * </blockquote>
     *
     * @param transactionProperties properties to configure the transaction properties
     * @param callable The closure to call
     * @return The result of the closure execution
     * @see DefaultTransactionDefinition
     * @see #withNewTransaction(Closure)
     * @see #withTransaction(Closure)
     * @see #withTransaction(Map, Closure)
     */
    void withNewTransaction(Map transactionProperties, Closure callable) {
        def props = new HashMap(transactionProperties)
        props.remove 'propagationName'
        props.propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        withTransaction(props, callable)
    }

    void  withTransaction(Map transactionProperties, Closure callable) {
        def transactionDefinition = new DefaultTransactionDefinition()
        transactionProperties.each { k, v ->
            if(v instanceof CharSequence && !(v instanceof String)) {
                v = v.toString()
            }
            try {
                transactionDefinition[k as String] = v
            } catch (MissingPropertyException mpe) {
                throw new IllegalArgumentException("[${k}] is not a valid transaction property.", mpe)
            }
        }
        withTransaction(transactionDefinition, callable)
    }

    /**
     * Executes the closure within the context of a transaction for the given {@link TransactionDefinition}
     *
     * @param callable The closure to call
     * @return The result of the closure execution
     */
    void withTransaction(TransactionDefinition definition, Closure callable) {
        Assert.notNull transactionManager, "No transactionManager bean configured"

        if (!callable) {
            return
        }

        new GrailsTransactionTemplate(transactionManager, definition).execute(callable)
    }
}
