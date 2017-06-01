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


import org.elasticsearch.action.ActionModule;
import org.elasticsearch.common.settings.Settings;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import com.floragunn.searchguard.filter.SearchGuardFilter;

import io.fabric8.elasticsearch.plugin.filter.FieldStatsResponseFilter;

public class OpenShiftElasticSearchPluginTest {

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testSearchGuardFilterIsRegisteredLast() {
        ActionModule module = Mockito.spy(new ActionModule(true));
        Settings settings = Settings.EMPTY;
        OpenShiftElasticSearchPlugin plugin = new OpenShiftElasticSearchPlugin(settings );
        
        plugin.onModule(module);
        
        InOrder inOrder = Mockito.inOrder(module);
        inOrder.verify(module).registerFilter(FieldStatsResponseFilter.class);
        inOrder.verify(module).registerFilter(KibanaUserReindexAction.class);
        inOrder.verify(module).registerFilter(SearchGuardFilter.class);
    }

}
