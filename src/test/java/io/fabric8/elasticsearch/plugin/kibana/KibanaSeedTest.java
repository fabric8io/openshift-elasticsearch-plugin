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

package io.fabric8.elasticsearch.plugin.kibana;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.common.settings.Settings;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Matchers;
import org.mockito.runners.MockitoJUnitRunner;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.KibanaIndexMode;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginClient;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.plugin.model.Project;

@RunWith(MockitoJUnitRunner.class)
public class KibanaSeedTest {

    private static final String USER = "auser";
    private static final String TOKEN = "token";
    
    private KibanaSeed seeder;
    private PluginClient pluginClient = mock(PluginClient.class);
    private PluginSettings settings = new PluginSettings(Settings.EMPTY);
    private IndexMappingLoader loader = mock(IndexMappingLoader.class);
    private OpenshiftRequestContext context;
    
    @Before
    public void setUp() {
        KibanaUtils utils = new KibanaUtils(settings, pluginClient);
        seeder = new KibanaSeed(settings, loader, pluginClient, utils);
        context = new OpenshiftRequestContextFactory.OpenshiftRequestContext(USER, TOKEN, true, 
                new HashSet<Project>(), ".kibana_123", KibanaIndexMode.SHARED_OPS);
        when(loader.getOperationsMappingsTemplate()).thenReturn("{\"foo\":\"bar\"");
        when(pluginClient.updateDocument(anyString(), anyString(), anyString(), anyString())).thenReturn(mock(UpdateResponse.class));
    }
    
    @After
    public void tearDown() {
        reset(pluginClient);
    }
    
    private void givenDefaultKibanaIndexExist(boolean exists) {
        when(pluginClient.indexExists(eq(ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX))).thenReturn(exists);
        KibanaUtilsTest.givenSearchResultForDocuments(pluginClient, ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX, new HashMap<>());
    }

    private void givenKibanaIndexExist(boolean exists) {
        when(pluginClient.indexExists(eq(context.getKibanaIndex()))).thenReturn(exists);
    }
    
    private void givenDocumentExistFor(String index, String type, String id, boolean exists) {
        when(pluginClient.documentExists(eq(index), eq(type), eq(id))).thenReturn(exists);
    }
    
    private void givenCopyKibanaIndexIsSuccessful() throws InterruptedException, ExecutionException, IOException {
        CreateIndexResponse response = mock(CreateIndexResponse.class);
        when(pluginClient.copyIndex(anyString(), anyString(), any(Settings.class), Matchers.<String>anyVararg())).thenReturn(response);
    }
    
    private void whenSettingTheDashboards() {
        seeder.setDashboards(context, ConfigurationSettings.DEFAULT_KIBANA_VERSION, ConfigurationSettings.OPENSHIFT_DEFAULT_PROJECT_INDEX_PREFIX);
    }
    
    /*
     * This is the case where Kibana is not deployed but maybe we are servicing
     * a request directly to Elasticsearch
     */
    @Test
    public void testSeedDoesNothingWhenTheDefaultKibanaDoesNotExist() throws Exception {

        givenDefaultKibanaIndexExist(false);
        //given index-patterns do not exist
        givenKibanaIndexExist(false);
       
        whenSettingTheDashboards();
        
        //thenOperationsIndexPatternsShouldBeCreated();
        for (String pattern : ConfigurationSettings.DEFAULT_KIBANA_OPS_INDEX_PATTERNS) {
            verify(pluginClient, never()).createDocument(eq(context.getKibanaIndex()), eq("index-pattern"), eq(pattern), anyString());
        }
        // thenKibanaIndexShouldBeRefreshed
        verify(pluginClient, never()).refreshIndices(eq(context.getKibanaIndex()));
    }
    
    @Test
    public void testSeedOperationsIndexPatternsWhenNeverSeeded() throws Exception {
        
        givenDefaultKibanaIndexExist(true);
        //given index-patterns do not exist
        givenKibanaIndexExist(true);
        for (String pattern : ConfigurationSettings.DEFAULT_KIBANA_OPS_INDEX_PATTERNS) {
            givenDocumentExistFor(context.getKibanaIndex(), "index-pattern", pattern, false);
        }
        
        givenCopyKibanaIndexIsSuccessful();
        
        whenSettingTheDashboards();
        
        //thenOperationsIndexPatternsShouldBeCreated();
        for (String pattern : ConfigurationSettings.DEFAULT_KIBANA_OPS_INDEX_PATTERNS) {
            verify(pluginClient, times(1)).createDocument(eq(context.getKibanaIndex()), eq("index-pattern"), eq(pattern), anyString());
        }
        // thenKibanaIndexShouldBeRefreshed
        verify(pluginClient, times(1)).refreshIndices(eq(context.getKibanaIndex()));
    }

    // Should be a no-op since everything exists
    @Test
    public void testSeedOperationsIndexPatternsWhenSeededPreviously() throws Exception {
        
        givenDefaultKibanaIndexExist(true);
        //given index-patterns do not exist
        givenKibanaIndexExist(true);
        for (String pattern : ConfigurationSettings.DEFAULT_KIBANA_OPS_INDEX_PATTERNS) {
            givenDocumentExistFor(context.getKibanaIndex(), "index-pattern", pattern, true);
        }
        
        whenSettingTheDashboards();
        
        //thenOperationsIndexPatternsShouldBeCreated();
        for (String pattern : ConfigurationSettings.DEFAULT_KIBANA_OPS_INDEX_PATTERNS) {
            verify(pluginClient, never()).createDocument(eq(context.getKibanaIndex()), eq("index-pattern"), eq(pattern), anyString());
        }
        // thenKibanaIndexShouldBeRefreshed
        verify(pluginClient, never()).refreshIndices(eq(context.getKibanaIndex()));
    }

}
