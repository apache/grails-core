package pages

import geb.Page

class LoginPage extends Page {

	static url = 'login/auth'

	static at = { title == 'Login' }

	static content = {
		loginForm { $('form') }
		username { $('input', type: 'text',     name: 'username') }
		password { $('input', type: 'password', name: 'password') }
		loginButton { $('input', type: 'submit', value: 'Login') }
	}
}
