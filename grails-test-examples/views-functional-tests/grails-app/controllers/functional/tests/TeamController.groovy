package functional.tests


import grails.rest.*
import grails.converters.*

class TeamController extends RestfulController {
    static responseFormats = ['json', 'xml']
    TeamController() {
        super(Team)
    }

    @Override
    Object show() {
        respond Team.findById(params.id, [fetch:[players:'join']])
    }

    def composite() {
        respond Composite.findByTeamAndPlayer(Team.load(1), Player.load(2))
    }

    def deep(Long id) {
        respond Team.get(id)
    }

    def hal(Long id) {
        respond Team.findById(params.id, [fetch:[players:'join']])
    }
}
