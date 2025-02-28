package org.grails.async.factory.future

import grails.async.PromiseFactory

import java.util.concurrent.ExecutorService

/**
 * Interface for classes that are both a PromiseFactory and an ExecutorService
 *
 * @author Graeme Rocher
 * @since 3.3
 */
interface ExecutorPromiseFactory extends PromiseFactory, ExecutorService {}