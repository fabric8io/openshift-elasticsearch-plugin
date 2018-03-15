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

package io.fabric8.elasticsearch.plugin.acl;

import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;

public abstract class BaseRolesMappingSyncStrategy implements RolesMappingSyncStrategy {

    protected final SearchGuardRolesMapping mappings;
    private long expire;
    
    protected BaseRolesMappingSyncStrategy(final SearchGuardRolesMapping mappings, long expiresInMillis) {
        this.mappings = mappings;
        this.expire = expiresInMillis;
    }

    protected abstract void syncFromImpl(OpenshiftRequestContext context, RolesMappingBuilder builder);
    
    protected long getExpires() {
        return expire;
    }

    @Override
    public void syncFrom(OpenshiftRequestContext context) {
        RolesMappingBuilder builder = new RolesMappingBuilder();
        syncFromImpl(context, builder);
        mappings.addAll(builder.build());
    }

}
