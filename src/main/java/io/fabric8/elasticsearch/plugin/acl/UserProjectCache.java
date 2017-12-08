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

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Map;
import java.util.Set;

/**
 * Cache of SearchGuard ACLs
 *
 */
public interface UserProjectCache {

    /**
     * Add users for a project
     *
     * @param user
     *            the user
     * @param token
     *            the token that the user used to authenticate
     * @param projects
     *            the projects to add the user to
     * @param operationsUser
     *            boolean whether or not the user is an operationsUser
     */
    void update(final String user, final String token, Set<String> projects, boolean operationsUser);

    /**
     * Retrieve an unmodifiable mapping of users to their projects
     *
     * @return Immutable map of user/token to projects
     */
    Map<SimpleImmutableEntry<String, String>, Set<String>> getUserProjects();

    /**
     *
     * @param  user  The user to check in the cache
     * @param  token The user's token to check in the cache
     * @return true if the cache has an entry for a user
     */
    boolean hasUser(String user, String token);

    boolean isOperationsUser(String user, String token);

    void expire();

    /**
     * Retrieve names of all projects that users belong to
     *
     * @return  The set of all the projects in the cache
     */
    Set<String> getAllProjects();
}
