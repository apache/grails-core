package grails.gorm.transactions

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.springframework.transaction.TransactionDefinition

/**
 * A GORM service that simplifies the execution of transactions
 *
 * @author Graeme Rocher
 * @since 6.1
 */
interface TransactionService {

    /**
     * Executes the given callable within the context of a transaction with the default attributes
     *
     * @param callable The callable
     * @return The result
     */
    public <T> T withTransaction(@ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)

    /**
     * Executes the given callable within the context of a transaction that is automatically rolled back with the default attributes
     *
     * @param callable The callable
     * @return The result
     */
    public <T> T withRollback(@ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)
    /**
     * Executes the given callable within the context of a new transaction with the default attributes
     *
     * @param callable The callable
     * @return The result
     */
    public <T> T withNewTransaction(@ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)

    /**
     * Executes the given callable within the context of a transaction with the given definition
     *
     * @param definition The transaction definition
     * @param callable The callable The callable
     * @return The result of the callable
     */
    public <T> T withTransaction(TransactionDefinition definition, @ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)

    /**
     * Executes the given callable within the context of a transaction with the given definition
     *
     * @param definition The transaction definition as a map
     * @param callable The callable The callable
     * @return The result of the callable
     */
    public <T> T withTransaction(Map definition, @ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)

    /**
     * Executes the given callable within the context of a transaction that is automatically rolled back with the default attributes
     *
     * @param definition The transaction definition
     * @param callable The callable
     * @return The result
     */
    public <T> T withRollback(TransactionDefinition definition, @ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)
    /**
     * Executes the given callable within the context of a new transaction with the default attributes
     *
     * @param definition The transaction definition
     * @param callable The callable
     * @return The result
     */
    public <T> T withNewTransaction(TransactionDefinition definition, @ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)
}