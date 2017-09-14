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

package io.fabric8.elasticsearch.util;

import org.apache.commons.lang.ObjectUtils;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.http.netty.NettyHttpRequest;
import org.elasticsearch.rest.RestRequest;
import org.jboss.netty.handler.codec.http.HttpRequest;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.api.model.SubjectAccessReviewResponse;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;

public class RequestUtils implements ConfigurationSettings  {
    
    private static final ESLogger LOGGER = Loggers.getLogger(RequestUtils.class);
    public static final String AUTHORIZATION_HEADER = "Authorization";

    private String proxyUserHeader;

    @Inject
    public RequestUtils(final Settings settings) {
        this.proxyUserHeader = settings.get(SEARCHGUARD_AUTHENTICATION_PROXY_HEADER, DEFAULT_AUTH_PROXY_HEADER);
    }
    
    public String getUser(RestRequest request) {
        return (String) ObjectUtils.defaultIfNull(request.header(proxyUserHeader), "");
    }
    
    public String getBearerToken(RestRequest request) {
        final String[] auth = ((String) ObjectUtils.defaultIfNull(request.header(AUTHORIZATION_HEADER), "")).split(" ");
        if (auth.length >= 2 && "Bearer".equals(auth[0])) {
            return auth[1];
        }
        return "";
    }
    
    public boolean isOperationsUser(RestRequest request) {
        final String user = getUser(request);
        final String token = getBearerToken(request);
        ConfigBuilder builder = new ConfigBuilder().withOauthToken(token);
        boolean allowed = false;
        try (NamespacedOpenShiftClient osClient = new DefaultOpenShiftClient(builder.build())) {
            LOGGER.debug("Submitting a SAR to see if '{}' is able to retrieve logs across the cluster", user);
            SubjectAccessReviewResponse response = osClient.inAnyNamespace().subjectAccessReviews().createNew()
                    .withVerb("get").withResource("pods/log").done();
            allowed = response.getAllowed();
        } catch (Exception e) {
            LOGGER.error("Exception determining user's '{}' role.", e, user);
        } finally {
            LOGGER.debug("User '{}' isOperationsUser: {}", user, allowed);
        }
        return allowed;
    }

    public void setUser(RestRequest request, String user) {
        LOGGER.debug("Modifying header '{}' to be '{}'", proxyUserHeader, user);
        request.putHeader(proxyUserHeader, user);
    }
    
    /**
     * Modify the request of needed
     * @param request the original request
     * @param context the Openshift context 
     * @return  a modified request
     */
    public RestRequest modifyRequest(RestRequest request, OpenshiftRequestContext context) {
        if(!getUser(request).equals(context.getUser()) && request instanceof NettyHttpRequest) {
            NettyHttpRequest netty = (NettyHttpRequest)request;
            HttpRequest httpRequest = netty.request();
            httpRequest.headers().set(proxyUserHeader, context.getUser());
            return new NettyHttpRequest(httpRequest, netty.getChannel());
        }
        return request;
    }
}
