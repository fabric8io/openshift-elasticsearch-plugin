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

package io.fabric8.elasticsearch.plugin.auth;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.ArrayUtils;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;

import com.floragunn.searchguard.auth.AuthenticationBackend;
import com.floragunn.searchguard.auth.HTTPAuthenticator;
import com.floragunn.searchguard.user.AuthCredentials;
import com.floragunn.searchguard.user.User;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftAPIService;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginServiceFactory;
import io.fabric8.elasticsearch.plugin.acl.BaseRolesSyncStrategy;
import io.fabric8.elasticsearch.plugin.acl.SearchGuardRolesMapping;

/**
 * OpenShiftTokenAuthentication is an AuthenticationBackend that will utilize an
 * authorization bearer token to create AuthCredentials and establish a User
 * with roles for: kibana, generated Openshift logging role name. It optionally
 * will populate backendroles based on satisfying a SubjectAccessReview for static role
 * definitions
 * 
 * The backend is configured as follows in the sg_config.yml:
 * 
 * searchguard: 
 *   authc: 
 *     my_domain: 
 *       enabled: true 
 *       order: 1 
 *       http_authenticator:
 *         challange: false 
 *           type: io.fabric8.elasticsearch.plugin.auth.OpenShiftTokenAuthentication
 *       authentication_backend: 
 *         type: io.fabric8.elasticsearch.plugin.auth.OpenShiftTokenAuthentication 
 *         config:
 *           subjectAccessReviews: 
 *             prometheus: 
 *               namespace: openshift-logging 
 *               verb: view
 *               resource: prometheus 
 *               resourceAPIGroup: metrics.openshift.io
 */
public class OpenShiftTokenAuthentication implements AuthenticationBackend, HTTPAuthenticator, BackendRoleRetriever {

    private static final Logger LOGGER = Loggers.getLogger(OpenShiftTokenAuthentication.class);
    private final Map<String, Settings> sars;

    public OpenShiftTokenAuthentication(final Settings settings) {
        sars = settings.getGroups("subjectAccessReviews");
        PluginServiceFactory.setBackendRoleRetriever(this);
    }

    @Override
    public AuthCredentials extractCredentials(RestRequest request, ThreadContext context)
            throws ElasticsearchSecurityException {
        if (PluginServiceFactory.isReady()) {
            OpenshiftRequestContextFactory contextFactory = PluginServiceFactory.getContextFactory();
            try {
                OpenshiftRequestContext requestContext = contextFactory.create(request);
                context.putTransient(ConfigurationSettings.OPENSHIFT_REQUEST_CONTEXT, requestContext);
                if (requestContext == OpenshiftRequestContext.EMPTY) {
                    return null;
                }
                return new AuthCredentials(requestContext.getUser(), requestContext.getBackendRoles()).markComplete();
            } catch (ElasticsearchSecurityException ese) {
                throw ese;
            } catch (Exception e) {
                LOGGER.error("Error handling request", e);
            }
        }
        return null;
    }

    @Override
    public User authenticate(AuthCredentials credentials) throws ElasticsearchSecurityException {
        if (PluginServiceFactory.isReady() && PluginServiceFactory.getThreadContext() != null) {
            OpenshiftRequestContext context = PluginServiceFactory.getThreadContext().getTransient(ConfigurationSettings.OPENSHIFT_REQUEST_CONTEXT);
            if(context == null || context == OpenshiftRequestContext.EMPTY) {
                return null;
            }
            User user = new User(context.getUser(), context.getBackendRoles());
            addGeneralRoles(user, credentials, context);
            return user;
        }
        return null;
    }

    public Collection<String> retrieveBackendRoles(String token) {
        List<String> roles = new ArrayList<>();
        if (PluginServiceFactory.isReady()) {
            final SecurityManager sm = System.getSecurityManager();
            if (sm != null) {
                sm.checkPermission(new SpecialPermission());
            }
            OpenshiftAPIService apiService = PluginServiceFactory.getApiService();
            for (Map.Entry<String, Settings> sar : sars.entrySet()) {
                boolean allowed = AccessController.doPrivileged(new PrivilegedAction<Boolean>() {

                    @Override
                    public Boolean run() {
                        try {
                            Settings params = sar.getValue();
                            return apiService.localSubjectAccessReview(token, 
                                    params.get("namespace"),
                                    params.get("verb"), 
                                    params.get("resource"), 
                                    params.get("resourceAPIGroup"),
                                    ArrayUtils.EMPTY_STRING_ARRAY);
                        } catch (Exception e) {
                            LOGGER.error("Exception executing LSAR", e);
                        }
                        return false;
                    }

                });
                if (allowed) {
                    roles.add(sar.getKey());
                }
            }
        }
        return roles;
    }

    private void addGeneralRoles(User user, AuthCredentials credentials, OpenshiftRequestContext context) {
        user.addRole(BaseRolesSyncStrategy.formatUserRoleName(credentials.getUsername()));
        user.addRole(BaseRolesSyncStrategy.formatUserKibanaRoleName(credentials.getUsername()));
        if (context.isOperationsUser()) {
            user.addRole(SearchGuardRolesMapping.ADMIN_ROLE);
        }
    }

    @Override
    public boolean exists(User user) {
        return user != null;
    }

    /**
     * ReRequest authentication is Unsupported
     */
    @Override
    public boolean reRequestAuthentication(RestChannel channel, AuthCredentials credentials) {
        return false;
    }

    @Override
    public String getType() {
        return OpenShiftTokenAuthentication.class.getName();
    }

}
