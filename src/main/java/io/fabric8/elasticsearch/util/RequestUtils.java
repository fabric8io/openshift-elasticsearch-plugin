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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.Logger;

import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentHelper;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftClientFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.openshift.api.model.SubjectAccessReviewResponse;
import io.fabric8.openshift.client.DefaultOpenShiftClient;
import io.fabric8.openshift.client.NamespacedOpenShiftClient;
import io.netty.channel.Channel;

public class RequestUtils implements ConfigurationSettings  {
    
    private static final Logger LOGGER = Loggers.getLogger(RequestUtils.class);
    public static final String AUTHORIZATION_HEADER = "Authorization";

    private final String proxyUserHeader;
    private final OpenshiftClientFactory k8ClientFactory;
    private final String defaultKibanaIndex;

    @Inject
    public RequestUtils(final PluginSettings pluginSettings, OpenshiftClientFactory clientFactory) {
        this.defaultKibanaIndex = pluginSettings.getDefaultKibanaIndex();
        this.proxyUserHeader = pluginSettings.getSettings().get(SEARCHGUARD_AUTHENTICATION_PROXY_HEADER, DEFAULT_AUTH_PROXY_HEADER);
        this.k8ClientFactory = clientFactory;
    }
    
    public boolean hasUserHeader(RestRequest request) {
        return StringUtils.isNotEmpty(getUser(request));
    }
    
    public String getUser(RestRequest request) {
        return StringUtils.defaultIfEmpty(request.header(proxyUserHeader), "");
    }
    
    public String getBearerToken(RestRequest request) {
        final String[] auth = StringUtils.defaultIfEmpty(request.header(AUTHORIZATION_HEADER), "").split(" ");
        if (auth.length >= 2 && "Bearer".equals(auth[0])) {
            return auth[1];
        }
        return "";
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
                Config config = new ConfigBuilder().withOauthToken(token).build();
                try (NamespacedOpenShiftClient osClient = (NamespacedOpenShiftClient) k8ClientFactory.create(config)) {
                    LOGGER.debug("Submitting a SAR to see if '{}' is able to retrieve logs across the cluster", user);
                    SubjectAccessReviewResponse response = osClient.inAnyNamespace().subjectAccessReviews().createNew()
                            .withVerb("get").withResource("pods/log").done();
                    allowed = response.getAllowed();
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
        

    @SuppressWarnings("rawtypes")
    public String assertUser(RestRequest request){
        return executePrivilegedAction(new PrivilegedAction<String>() {

            @Override
            public String run() {
                String username = null;
                final String user = getUser(request);
                final String token = getBearerToken(request);
                ConfigBuilder builder = new ConfigBuilder().withOauthToken(token);
                try (DefaultOpenShiftClient osClient = new DefaultOpenShiftClient(builder.build())) {
                    LOGGER.debug("Verifying user {} matches the given token.", user);
                    Request okRequest = new Request.Builder()
                            .addHeader(AUTHORIZATION_HEADER, "Bearer " + token)
                            .url(osClient.getMasterUrl() + "oapi/v1/users/~")
                            .build();
                    Response response = null;
                    try {
                        response = osClient.getHttpClient().newCall(okRequest).execute();
                        final String body = response.body().string();
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Response: code '{}' {}", response.code(), body);
                        }
                        if(response.code() != RestStatus.OK.getStatus()) {
                            throw new ElasticsearchSecurityException("", RestStatus.UNAUTHORIZED);
                        }
                        Map<String, Object> userResponse = XContentHelper.convertToMap(XContentFactory.xContent(body), body, false);
                        if(userResponse.containsKey("metadata") && ((Map)userResponse.get("metadata")).containsKey("name")) {
                            username = (String) ((Map)userResponse.get("metadata")).get("name");
                        }
                    }catch (Exception e) {
                        LOGGER.error("Exception trying to assertUser '{}'", e, user);
                    }
                    if(StringUtils.isNotBlank(username) && StringUtils.isNotBlank(user) && !user.equals(username)) {
                        String message = String.format("The given username '%s' does not match the username '%s' associated with the token provided with the request.",
                                user, username);
                        LOGGER.debug(message);
                    }
                }
                if (null == username) {
                    throw new ElasticsearchSecurityException("", RestStatus.UNAUTHORIZED);
                }
                return username;
            }
            
        });
    }

    /**
     * Modify the request of needed
     * @param request the original request
     * @param context the Openshift context 
     */
    public RestRequest modifyRequest(final RestRequest request, OpenshiftRequestContext context, RestChannel channel) {
        
        final String uri = getUri(request, context);
        final BytesReference content = getContent(request, context);
        if(!getUser(request).equals(context.getUser()) || !uri.equals(request.uri()) || content != request.content()) {
            LOGGER.debug("Modifying header '{}' to be '{}'", proxyUserHeader, context.getUser());
            final Map<String, List<String>> modifiedHeaders = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            modifiedHeaders.putAll(request.getHeaders());
            modifiedHeaders.put(proxyUserHeader, Arrays.asList(context.getUser()));
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
        if(content.contains("_index\":\"" + defaultKibanaIndex)) {
            LOGGER.debug("Replacing the content that references the default kibana index");
            String replaced = content.replaceAll("_index\":\"" + defaultKibanaIndex + "\"", "_index\":\"" + context.getKibanaIndex() + "\"");
            return new BytesArray(replaced);
        }
        return request.content();
    }
    
    private String getUri(final RestRequest request, final OpenshiftRequestContext context) {
        if(request.uri().contains(defaultKibanaIndex) && !context.getKibanaIndex().equals(defaultKibanaIndex)) {
            String uri = request.uri().replaceAll(defaultKibanaIndex, context.getKibanaIndex());
            LOGGER.debug("Modifying uri to be '{}'", uri);
            return uri;
        }
        return request.uri();
    }
}
