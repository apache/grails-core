package grails.gorm.rx.api

/**
 * Combined interface for all operations
 *
 * @author Graeme Rocher
 * @since 6.0
 */
interface RxGormAllOperations<D> extends RxGormStaticOperations<D>, RxGormInstanceOperations<D> {
}
