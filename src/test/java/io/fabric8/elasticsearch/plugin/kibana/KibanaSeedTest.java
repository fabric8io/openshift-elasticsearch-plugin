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

import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.Client;
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

@RunWith(MockitoJUnitRunner.class)
public class KibanaSeedTest {

    private static final String USER = "auser";
    private static final String TOKEN = "token";
    
    private KibanaSeed seeder;
    private PluginClient pluginClient = mock(PluginClient.class);
    private PluginSettings settings = new PluginSettings(Settings.EMPTY);
    private IndexMappingLoader loader = mock(IndexMappingLoader.class);
    private Client client = mock(Client.class);
    private OpenshiftRequestContext context;
    
    @Before
    public void setUp() {
        seeder = new KibanaSeed(settings, loader, pluginClient);
        context = new OpenshiftRequestContextFactory.OpenshiftRequestContext(USER, TOKEN, true, 
                new HashSet<String>(), ConfigurationSettings.DEFAULT_USER_PROFILE_PREFIX, KibanaIndexMode.SHARED_OPS);
        when(loader.getOperationsMappingsTemplate()).thenReturn("{\"foo\":\"bar\"");
        when(pluginClient.update(anyString(), anyString(), anyString(), anyString())).thenReturn(mock(UpdateResponse.class));
    }
    
    @After
    public void tearDown() {
        reset(pluginClient);
    }
    
    private void givenKibanaIndexExist(boolean exists) {
        when(pluginClient.indexExists(anyString())).thenReturn(exists);
    }
    
    private void givenDocumentExistFor(String index, String type, String id, boolean exists) {
        when(pluginClient.documentExists(eq(index), eq(type), eq(id))).thenReturn(exists);
    }
    
    private void givenCopyKibanaIndexIsSuccessful() throws InterruptedException, ExecutionException, IOException {
        CreateIndexResponse response = mock(CreateIndexResponse.class);
        when(pluginClient.copyIndex(anyString(), anyString(), Matchers.<String>anyVararg())).thenReturn(response );
    }
    
    private void whenSettingTheDashboards() {
        seeder.setDashboards(context, client, ConfigurationSettings.DEFAULT_KIBANA_VERSION, ConfigurationSettings.OPENSHIFT_DEFAULT_PROJECT_INDEX_PREFIX);
    }
    
    @Test
    public void testSeedOperationsIndexPatternsWhenNeverSeeded() throws Exception {

        //given index-patterns do not exist
        givenKibanaIndexExist(false);
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
