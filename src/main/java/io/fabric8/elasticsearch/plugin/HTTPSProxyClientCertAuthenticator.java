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

import org.apache.commons.lang.ArrayUtils;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;

import com.floragunn.searchguard.authentication.AuthException;
import com.floragunn.searchguard.authentication.User;
import com.floragunn.searchguard.authentication.backend.AuthenticationBackend;
import com.floragunn.searchguard.authentication.http.HTTPAuthenticator;
import com.floragunn.searchguard.authentication.http.clientcert.HTTPSClientCertAuthenticator;
import com.floragunn.searchguard.authentication.http.proxy.HTTPProxyAuthenticator;
import com.floragunn.searchguard.authorization.Authorizator;

public class HTTPSProxyClientCertAuthenticator implements HTTPAuthenticator, ConfigurationSettings{
	
	private final HTTPProxyAuthenticator proxyAuthenticator;
	private final HTTPSClientCertAuthenticator certAuthenticator;
	private final String[] userWhitelists;
	private final ESLogger log;
	
	@Inject
	public HTTPSProxyClientCertAuthenticator(final Settings settings){
		this(settings, new HTTPProxyAuthenticator(settings), new HTTPSClientCertAuthenticator(settings), Loggers.getLogger(HTTPSProxyClientCertAuthenticator.class));
	}
	
	/*
	 * Testing constructor
	 */
	public HTTPSProxyClientCertAuthenticator(final Settings settings, final HTTPProxyAuthenticator proxyAuthenticator, final HTTPSClientCertAuthenticator certAuthenticator, final ESLogger logger){
		this.certAuthenticator = certAuthenticator;
		this.proxyAuthenticator = proxyAuthenticator;
		this.userWhitelists = settings.getAsArray(OPENSHIFT_WHITELISTED_USERS, DEFAULT_WHITELISTED_USERS);
		this.log = logger;
	}
	
	@Override
	public User authenticate(RestRequest request, RestChannel channel, AuthenticationBackend backend,
			Authorizator authorizator) throws AuthException {
		
		User user = null;
		try{
			user = proxyAuthenticator.authenticate(request, channel, backend, authorizator);
		}catch(AuthException e){
			log.debug("Unable to Authenticate using the proxy header.  Trying certificate authorization...");
		}
		if(user != null){
			if(ArrayUtils.contains(userWhitelists,user.getName())){
				log.info("Denying a request because it has a proxy user header that is the same as one that is whitelisted");
				throw new AuthException("Denying a request because it has a proxy userheader that is the same as one that is whitelisted.");
			}
			return user;
			
		}
		try{
			return certAuthenticator.authenticate(request, channel, backend, authorizator);
		}catch(AuthException e){
			throw new UnauthorizedException(e);
		}
	}

}
