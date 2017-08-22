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

import java.io.File;
import java.nio.charset.StandardCharsets;

import javax.xml.bind.DatatypeConverter;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.elasticsearch.ElasticsearchSecurityException;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.loader.YamlSettingsLoader;
import org.elasticsearch.common.util.concurrent.ThreadContext;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestRequest;

import com.floragunn.searchguard.action.configupdate.TransportConfigUpdateAction;
import com.floragunn.searchguard.auth.AuthenticationBackend;
import com.floragunn.searchguard.auth.HTTPAuthenticator;
import com.floragunn.searchguard.user.AuthCredentials;
import com.floragunn.searchguard.user.User;

import io.fabric8.elasticsearch.plugin.OpenShiftElasticSearchConfigurationException;

/**
 * Provides 'basic' authorization using a file of usernames and passwords.
 * The source is YAML based file of usernames and base64 encoded passwords:
 * 
 * ---
 * # username of 'foo' with password 'bar'
 * foo:
 *   passwd: YmFyCg==
 *   
 * This authorization assumes the header is also base64 encoded and the password
 * contains no colons (e.g. :).  This allows the username to be in an Openshift
 * service account format (e.g. system:serviceaccount:logging:prometheus) and still
 * be sent by the client with a parsable password.
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
 *         type: io.fabric8.elasticsearch.plugin.auth.FileAuthenticationBackend
 *         config:
 *           file_path: /path/to/yamlfile
 *       authentication_backend:
 *         type: io.fabric8.elasticsearch.plugin.auth.FileAuthenticationBackend
 *         config:
 *           file_path: /path/to/yamlfile         
 */
public class FileAuthenticationBackend implements AuthenticationBackend, HTTPAuthenticator {

    private static final String PASSWD = ".passwd";
    protected static final String FILE = "file_path";
    private final File auth; 
    private Settings mappings;
    private long lastModified;
    
    /*
     * remove me if we need TransportConfigUpdateAction
     */
    public FileAuthenticationBackend(final Settings settings) {
        this(settings, null);
    }
    
    public FileAuthenticationBackend(final Settings settings, final TransportConfigUpdateAction tcua) {
        String file = settings.get(FILE);
        if(StringUtils.isBlank(file)) {
            throw new OpenShiftElasticSearchConfigurationException("Unable to Configure FileAuthenticationBackend because file_path is empty");
        }
        auth = FileUtils.getFile(file);
        if(!auth.exists()) {
            throw new OpenShiftElasticSearchConfigurationException("Unable to Configure YmlFileAuthenticationBackend because file_path does not exist: " + file);
        }
        loadAuthFile();
    }
    
    @Override
    public AuthCredentials extractCredentials(RestRequest request, ThreadContext context) throws ElasticsearchSecurityException {
        final String authorizationHeader = request.header("Authorization");
        if (authorizationHeader != null) {
            if (authorizationHeader.trim().toLowerCase().startsWith("basic ")) {
                final String decoded = new String(DatatypeConverter.parseBase64Binary(authorizationHeader.split(" ")[1]),
                        StandardCharsets.UTF_8);

                //username:password
                //Assume password is all chars from the last : to the end
                //this is the only way to send service accounts
               
                final int delimiter = decoded.lastIndexOf(':');

                String username = null;
                String password = null;

                if (delimiter > 0) {
                    username = decoded.substring(0, delimiter);
                    
                    if(decoded.length() - 1 != delimiter) {
                        password = decoded.substring(delimiter + 1).trim();
                    }
                }
                if (username != null && StringUtils.isNotEmpty(password)) {
                    return new AuthCredentials(username, password.getBytes(StandardCharsets.UTF_8)).markComplete();
                }
            }
        }
        return null;
    }

    @Override
    public boolean reRequestAuthentication(RestChannel channel, AuthCredentials credentials) {
        // unsupported
        return false;
    }

    @Override
    public String getType() {
        return FileAuthenticationBackend.class.getName();
    }

    @Override
    public User authenticate(AuthCredentials credentials) throws ElasticsearchSecurityException {
        if (credentials == null) {
            throw new ElasticsearchSecurityException("Creditials are null while trying to authenticate");
        }
        Settings settings = loadAuthFile();
        if(exists(settings, credentials.getUsername())){
            final String hash = settings.get(credentials.getUsername() + PASSWD);
            if(StringUtils.isNotBlank(hash)) {
                final String saved = new String(DatatypeConverter.parseBase64Binary(hash), StandardCharsets.UTF_8).trim();
                final String presented = new String(credentials.getPassword());
                if(saved.equals(presented)) {
                    return new User(credentials.getUsername());
                }
            }
        }
        throw new ElasticsearchSecurityException("Unable to authenticate {}", credentials.getUsername());
    }

    @Override
    public boolean exists(User user) {
        if(user == null || user.getName() == null) {
            return false;
        }
        Settings settings = loadAuthFile();
        return exists(settings, user.getName());
    }
    
    private boolean exists(Settings settings, String username) {
        return settings.names().contains(username);
    }

    private Settings loadAuthFile() {
        long now = auth.lastModified();
        if (now > lastModified) {
            try {
                final String ref = FileUtils.readFileToString(auth);
                mappings = Settings.builder().put(new YamlSettingsLoader(true).load(ref)).build();
                lastModified = now;
            } catch (final Exception e) {
                throw new OpenShiftElasticSearchConfigurationException("Unable to parse " + auth.getAbsolutePath(), e);
            }
        }
        return mappings;
    }

}
