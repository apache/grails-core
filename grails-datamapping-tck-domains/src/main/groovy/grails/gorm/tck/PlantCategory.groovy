package grails.gorm.tck

import grails.persistence.Entity

@Entity
class PlantCategory implements Serializable {
    Long id
    Long version
    Set plants
    String name

    static hasMany = [plants:Plant]

    static namedQueries = {
//        withPlantsInPatch {
//            plants {
//                eq 'goesInPatch', true
//            }
//        }
//        withPlantsThatStartWithG {
//            plants {
//                like 'name', 'G%'
//            }
//        }
//        withPlantsInPatchThatStartWithG {
//            withPlantsInPatch()
//            withPlantsThatStartWithG()
//        }
    }
}
