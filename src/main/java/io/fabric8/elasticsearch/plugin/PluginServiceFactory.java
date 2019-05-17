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

import org.elasticsearch.common.util.concurrent.ThreadContext;

import io.fabric8.elasticsearch.plugin.auth.BackendRoleRetriever;

/**
 * Static factory class to provide late binding between
 * SG Authentication implementations and this plugin's
 * utility classes 
 */
public class PluginServiceFactory {
    
    private static OpenshiftRequestContextFactory contextFactory;
    private static OpenshiftAPIService apiService;
    private static boolean isReady;
    private static ThreadContext threadContext;
    private static BackendRoleRetriever backendRoleRetriever;

    private PluginServiceFactory() {
    }

    public static OpenshiftRequestContextFactory getContextFactory() {
        return contextFactory;
    }

    public static void setContextFactory(OpenshiftRequestContextFactory contextFactory) {
        PluginServiceFactory.contextFactory = contextFactory;
    }

    public static OpenshiftAPIService getApiService() {
        return apiService;
    }

    public static void setApiService(OpenshiftAPIService apiService) {
        PluginServiceFactory.apiService = apiService;
    }
    
    public static boolean isReady() {
        return isReady;
    }
    
    public static void markReady() {
        isReady = true;
    }

    public static void markNotReady() {
        isReady = false;
    }

    public static ThreadContext getThreadContext() {
        return threadContext;
    }

    public static void setThreadContext(ThreadContext threadContext) {
        PluginServiceFactory.threadContext = threadContext;
    }

    public static BackendRoleRetriever getBackendRoleRetriever() {
        return backendRoleRetriever;
    }

    public static void setBackendRoleRetriever(BackendRoleRetriever backendRoleRetriever) {
        PluginServiceFactory.backendRoleRetriever = backendRoleRetriever;
    }
    
}
