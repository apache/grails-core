/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

// Registered on DOMContentLoaded so the host layout's end-of-body scripts
// (jQuery, Bootstrap) are guaranteed to have loaded before this runs,
// regardless of where the plugin's script tag lands in the document.
document.addEventListener('DOMContentLoaded', function() {

	$('#loginModal').on('shown.bs.modal', function() {
		$('#ajaxUsername').trigger('focus');
	});

	$('#ajaxLoginForm').on('submit', function(event) {
		event.preventDefault();
		var $form = $(this);
		$('#loginMessage').html(loggingYouIn);
		$.ajax({
			url: $form.attr('action'),
			method: 'post',
			data: $form.serialize(),
			dataType: 'json',
			success: function(json) {
				if (json.success) {
					window.location = json.url || window.location.href;
				}
				else if (json.error) {
					$('#loginMessage').html($('<span class="text-danger">').text(json.error));
				}
				else {
					$('#loginMessage').empty();
				}
			},
			error: function(jqXHR, textStatus, errorThrown) {
				console.log('Login error, textStatus: ' + textStatus + ', errorThrown: ' + errorThrown);
				$('#loginMessage').html($('<span class="text-danger">').text(textStatus));
			}
		});
	});
});
