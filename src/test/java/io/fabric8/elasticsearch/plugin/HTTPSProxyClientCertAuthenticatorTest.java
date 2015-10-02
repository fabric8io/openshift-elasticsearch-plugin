/**
 * Copyright (C) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.fabric8.elasticsearch.plugin;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.floragunn.searchguard.authentication.AuthException;
import com.floragunn.searchguard.authentication.User;
import com.floragunn.searchguard.authentication.backend.AuthenticationBackend;
import com.floragunn.searchguard.authentication.http.clientcert.HTTPSClientCertAuthenticator;
import com.floragunn.searchguard.authentication.http.proxy.HTTPProxyAuthenticator;
import com.floragunn.searchguard.authorization.Authorizator;

@RunWith(MockitoJUnitRunner.class)
public class HTTPSProxyClientCertAuthenticatorTest {
	
	@Mock private Settings settings;
	private HTTPSProxyClientCertAuthenticator authenticator;
	private HTTPSClientCertAuthenticator certAuthenticator;
	private HTTPProxyAuthenticator proxyAuthenticator;
	@Mock private RestRequest request;
	@Mock private RestChannel channel;
	@Mock private AuthenticationBackend backend;
	@Mock private Authorizator authorizator;
	private User user = new User("someusername");
	
	@Before
	public void setUp() throws Exception {
		when(settings.get(anyString())).thenReturn(null);
		when(settings.getAsArray(anyString(), any(String[].class)))
			.thenReturn(ConfigurationSettings.DEFAULT_WHITELISTED_USERS);
		
		certAuthenticator = spy(new HTTPSClientCertAuthenticator(settings));
		proxyAuthenticator = spy(new HTTPProxyAuthenticator(settings));
		
		authenticator = new HTTPSProxyClientCertAuthenticator(settings, proxyAuthenticator, certAuthenticator, mock(ESLogger.class));
	}

	@Test
	public void nonWhiteListedProxyUsersShouldBeAuthenticated() throws Exception {
		whenProxyUserHeaderIs(user);
		assertEquals(user, authenticator.authenticate(request, channel, backend, authorizator));
	}

	@Test(expected=AuthException.class)
	public void whiteListedProxyUsersShouldNotBeAuthenticated() throws Exception{
		whenProxyUserHeaderIs(new User(ConfigurationSettings.DEFAULT_WHITELISTED_USERS[0]));
		authenticator.authenticate(request, channel, backend, authorizator);
	}
	
	@Test(expected=UnauthorizedException.class)
	public void usersWithInvalidCertsShouldNotBeAuthenticated() throws Exception{
		whenUnableToAuthUsingProxyHeader();
		doThrow(AuthException.class)
			.when(certAuthenticator)
			.authenticate(any(RestRequest.class), 
					any(RestChannel.class), 
					any(AuthenticationBackend.class), 
					any(Authorizator.class));
		authenticator.authenticate(request, channel, backend, authorizator);
	}

	@Test
	public void certificatUsersShouldBeAuthenticated() throws Exception {
		whenUnableToAuthUsingProxyHeader();
		whenRequestUtilizesCertificateFor(user);
		assertEquals(user, authenticator.authenticate(request, channel, backend, authorizator));
	}
	private void whenUnableToAuthUsingProxyHeader() throws Exception{
		doThrow(AuthException.class)
		.when(proxyAuthenticator)
		.authenticate(any(RestRequest.class), 
				any(RestChannel.class), 
				any(AuthenticationBackend.class), 
				any(Authorizator.class));
	}
	
	private void whenProxyUserHeaderIs(User user) throws Exception{
		doReturn(user)
		.when(proxyAuthenticator)
		.authenticate(any(RestRequest.class), 
				any(RestChannel.class), 
				any(AuthenticationBackend.class), 
				any(Authorizator.class));
		
	}
	private void whenRequestUtilizesCertificateFor(User user) throws Exception{
		doReturn(user)
		.when(certAuthenticator)
		.authenticate(any(RestRequest.class), 
				any(RestChannel.class), 
				any(AuthenticationBackend.class), 
				any(Authorizator.class));
		
	}
}
