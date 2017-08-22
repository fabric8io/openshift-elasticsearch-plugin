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

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

public enum Samples {

    ROLES_ACL("searchguard_roles_acl.yml"), 
    ROLESMAPPING_ACL("searchguard_rolesmapping_acl.yml"), 
    OPENSHIFT_ROLES_ACL("searchguard_roles_acl_with_openshift_projects.yml"), 
    OPENSHIFT_ROLESMAPPING_ACL("searchguard_rolesmapping_acl_with_openshift_projects.yml"),
    ROLES_OPS_SHARED_KIBANA_INDEX("roles_ops_shared_kibana_index.yml"),
    PASSWORDS("passwords.yml"),
    ROLES_SHARED_KIBANA_INDEX("roles_shared_kibana_index.yml"),
    ROLES_SHARED_OPS_KIBANA_INDEX("roles_shared_ops_kibana_index.yml"),
    ROLES_SHARED_NON_OPS_KIBANA_INDEX("roles_shared_non_ops_kibana_index.yml"),
    ROLES_OPS_SHARED_KIBANA_INDEX_WITH_UNIQUE("roles_ops_shared_kibana_index_with_unique.yml"),
    ROLESMAPPING_OPS_SHARED_KIBANA_INDEX_WITH_UNIQUE("rolesmapping_ops_shared_kibana_index.yml"),
    USER_ROLESMAPPING_STRATEGY("user_rolesmapping_shared_ops_kibana_index_with_unique.yml"),
    USER_ROLES_STRATEGY("user_role_with_shared_kibana_index_with_unique.yml");

    private String path;

    Samples(String path) {
        this.path = path;
    }

    public String getContent() {
        InputStream is = Samples.class.getResourceAsStream(path);
        try {
            return IOUtils.toString(is, "UTF-8");
        } catch (IOException e) {
            throw new RuntimeException(String.format("Unable to read file {}", path), e);
        }
    }
}
