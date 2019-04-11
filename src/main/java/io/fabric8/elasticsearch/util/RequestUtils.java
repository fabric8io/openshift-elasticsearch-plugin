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

import java.net.SocketAddress;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;

import org.elasticsearch.SpecialPermission;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftAPIService;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginSettings;

import io.netty.channel.Channel;

public class RequestUtils implements ConfigurationSettings  {
    
    private static final Logger LOGGER = Loggers.getLogger(RequestUtils.class);
    public static final String AUTHORIZATION_HEADER = "authorization";
    public static final String X_FORWARDED_ACCESS_TOKEN = "x-forwarded-access-token";

    private final String proxyUserHeader;
    private final String defaultKibanaIndex;
    private final OpenshiftAPIService apiService;

    public RequestUtils(final PluginSettings pluginSettings, final OpenshiftAPIService apiService) {
        this.defaultKibanaIndex = pluginSettings.getDefaultKibanaIndex();
        this.proxyUserHeader = pluginSettings.getSettings().get(SEARCHGUARD_AUTHENTICATION_PROXY_HEADER, DEFAULT_AUTH_PROXY_HEADER);
        this.apiService = apiService;
    }
    
    public void logRequest(final RestRequest request) {
        if (LOGGER.isDebugEnabled()) {
            try {
                LOGGER.debug("Handling Request... {}", request.uri());
                String user = getUser(request);
                String token = getBearerToken(request);
                LOGGER.debug("Evaluating request for user '{}' with a {} token", user,
                        (StringUtils.isNotEmpty(token) ? "non-empty" : "empty"));
                if (LOGGER.isTraceEnabled()) {
                    List<String> headers = new ArrayList<>();
                    for (Entry<String, List<String>> entry : request.getHeaders().entrySet()) {
                        if (RequestUtils.AUTHORIZATION_HEADER.equals(entry.getKey())) {
                            headers.add(entry.getKey() + "=Bearer <REDACTED>");
                        } else {
                            headers.add(entry.getKey() + "=" + entry.getValue());
                        }
                    }
                    LOGGER.trace("Request headers: {}", headers);
                }

            } catch (Exception e) {
                LOGGER.debug("unable to log request: " + e.getMessage());
            }
        }
    }
    
    public boolean hasUserHeader(RestRequest request) {
        return StringUtils.isNotEmpty(getUser(request));
    }
    
    public String getUser(RestRequest request) {
        return StringUtils.defaultIfEmpty(request.header(proxyUserHeader), "");
    }
    
    public String getBearerToken(RestRequest request) {
        String token = request.header(X_FORWARDED_ACCESS_TOKEN);
        if(token == null) {
            if (request.header(AUTHORIZATION_HEADER) != null) {
                final String[] auth = StringUtils.defaultIfEmpty(request.header(AUTHORIZATION_HEADER), "").split(" ");
                if (auth.length >= 2 && "Bearer".equals(auth[0])) {
                    token = auth[1];
                }
            }
        }
        return  StringUtils.defaultIfEmpty(token, "");
    }

    public boolean isClientCertAuth(final ThreadContext threadContext) {
        return threadContext != null && StringUtils.isNotEmpty(threadContext.getTransient("_sg_ssl_transport_principal"));
    }

    public boolean isOperationsUser(final RestRequest request) {
        return executePrivilegedAction(new PrivilegedAction<Boolean>(){

            @Override
            public Boolean run() {
                final String user = StringUtils.defaultIfEmpty(getUser(request), "<UNKNOWN>");
                final String token = getBearerToken(request);
                boolean allowed = false;
                try {
                    allowed = apiService.localSubjectAccessReview(token, "default", "view", "pods/log","", ArrayUtils.EMPTY_STRING_ARRAY);
                } catch (Exception e) {
                    LOGGER.error("Exception determining user's '{}' role: {}", user, e);
                } finally {
                    LOGGER.debug("User '{}' isOperationsUser: {}", user, allowed);
                }
                return allowed;
            }
            
        });
    }
    
    private <T> T executePrivilegedAction(PrivilegedAction<T> action){
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(new SpecialPermission());
        }
        return AccessController.doPrivileged(action);
    }
        
    public String assertUser(RestRequest request){
        return executePrivilegedAction(new PrivilegedAction<String>() {

            @Override
            public String run() {
                final String token = getBearerToken(request);
                return apiService.userName(token);
            }
            
        });
    }

    /**
     * Modify the request of needed
     * @param request the original request
     * @param context the Openshift context
     * @param channel the channel that is processing the request
     * 
     * @return The modified request
     */
    public RestRequest modifyRequest(final RestRequest request, OpenshiftRequestContext context, RestChannel channel) {
        
        final String uri = getUri(request, context);
        final BytesReference content = getContent(request, context);
        if(!getUser(request).equals(context.getUser()) || !uri.equals(request.uri()) || content != request.content()) {
            LOGGER.debug("Modifying header '{}' to be '{}'", proxyUserHeader, context.getUser());
            final Map<String, List<String>> modifiedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            modifiedHeaders.putAll(request.getHeaders());
            modifiedHeaders.put(proxyUserHeader, Arrays.asList(context.getUser()));
            if(request.header("Content-Type") != null && request.header("Content-Type").toLowerCase().endsWith("json")){
                modifiedHeaders.put("Content-Type", Arrays.asList("application/json"));
            }
            RestRequest modified = new RestRequest(request.getXContentRegistry(), uri, modifiedHeaders) {

                @Override
                public Method method() {
                    return request.method();
                }

                @Override
                public String uri() {
                    return uri;
                }

                @Override
                public boolean hasContent() {
                    return content.length() > 0;
                }

                @Override
                public BytesReference content() {
                    return content;
                }
                
                @Override
                public SocketAddress getRemoteAddress() {
                    return request.getRemoteAddress();
                }

                /**
                 * Returns the local address where this request channel is bound to.  The returned
                 * {@link SocketAddress} is supposed to be down-cast into more concrete
                 * type such as {@link java.net.InetSocketAddress} to retrieve the detailed
                 * information.
                 */
                @Override
                public SocketAddress getLocalAddress() {
                    return request.getRemoteAddress();
                }

                @SuppressWarnings("unused")
                public Channel getChannel() {
                    return (Channel) channel;
                }
                
            };
            modified.params().putAll(request.params());
            //HACK - only need to do if we modify the kibana index
            if (uri.contains(defaultKibanaIndex)) {
                modified.params().put("index", context.getKibanaIndex());
            }

            return modified;
        }
        return request;
    }
    
    private BytesReference getContent(final RestRequest request, final OpenshiftRequestContext context) {
        String content = request.content().utf8ToString();
        if(OpenshiftRequestContext.EMPTY != context && content.contains("_index\":\"" + defaultKibanaIndex)) {
            LOGGER.debug("Replacing the content that references the default kibana index");
            String replaced = content.replaceAll("_index\":\"" + defaultKibanaIndex + "\"", "_index\":\"" + context.getKibanaIndex() + "\"");
            return new BytesArray(replaced);
        }
        return request.content();
    }
    
    private String getUri(final RestRequest request, final OpenshiftRequestContext context) {
        if(OpenshiftRequestContext.EMPTY != context && request.uri().contains(defaultKibanaIndex) && !context.getKibanaIndex().equals(defaultKibanaIndex)) {
            String uri = request.uri().replaceAll(defaultKibanaIndex, context.getKibanaIndex());
            LOGGER.debug("Modifying uri to be '{}'", uri);
            return uri;
        }
        return request.uri();
    }
}
