package multitenantcomposite

import grails.gorm.MultiTenant

class Book implements MultiTenant<Book>, Serializable {

    String id
    String tenantId
    String title

    static mapping  = {
        id composite: ['id', 'tenantId']
    }
}