package pages

import geb.Page

class LogoutPage extends Page {

	static url = 'logout'

	static at = { title == 'Logout' }

	static content = {
		logoutForm { $('form') }
		logoutButton { $('input', value: 'Logout') }
	}
}
