package pages

import geb.Page

class SecureUserPage extends Page {
	static url = 'secure/users'

	static at = { title == 'users' }
}
