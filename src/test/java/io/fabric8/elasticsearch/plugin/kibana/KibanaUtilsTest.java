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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchResponseSections;
import org.elasticsearch.action.search.ShardSearchFailure;
import org.elasticsearch.common.bytes.BytesArray;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import io.fabric8.elasticsearch.plugin.ConfigurationSettings;
import io.fabric8.elasticsearch.plugin.OpenshiftRequestContextFactory.OpenshiftRequestContext;
import io.fabric8.elasticsearch.plugin.PluginClient;
import io.fabric8.elasticsearch.plugin.PluginSettings;
import io.fabric8.elasticsearch.plugin.model.Project;

@RunWith(MockitoJUnitRunner.class)
public class KibanaUtilsTest {

    private PluginClient client = mock(PluginClient.class);
    private PluginSettings settings = new PluginSettings(Settings.EMPTY);

    private KibanaUtils utils = new KibanaUtils(settings, client);

    private void givenSearchResultToIncludePattern(String indexPattern) {
        SearchHit[] hits = new SearchHit[1];
        hits[0] = new SearchHit(1, indexPattern, null, null);
        int totHits = 1;
        if (indexPattern == null) {
            totHits = 0;
        }
        SearchHits searchHits = new SearchHits(hits, totHits, 1.0f);
        SearchResponseSections sections = new SearchResponseSections(searchHits, null, null, false, Boolean.FALSE, null,
                0);
        ShardSearchFailure[] failures = null;
        SearchResponse response = new SearchResponse(sections, "", 0, 0, 0, 0L, failures);

        when(client.search(anyString(), anyString())).thenReturn(response);
    }

    private void givenSearchResultForDocuments(String indexPattern, Map<String, BytesReference> docs) {
        givenSearchResultForDocuments(client, indexPattern, docs);
    }

    public static void givenSearchResultForDocuments(PluginClient client, String indexPattern, Map<String, BytesReference> docs) {
        List<SearchHit> hits = new ArrayList<>(docs.size());
        for (Map.Entry<String, BytesReference> entry : docs.entrySet()) {
            SearchHit hit = new SearchHit(1, entry.getKey(), null, null);
            hit.sourceRef(entry.getValue());
            hits.add(hit);
        }
        SearchHits searchHits = new SearchHits(hits.toArray(new SearchHit[hits.size()]), hits.size(), 1.0f);
        SearchResponseSections sections = new SearchResponseSections(searchHits, null, null, false, Boolean.FALSE, null,
                0);
        ShardSearchFailure[] failures = null;
        SearchResponse response = new SearchResponse(sections, "", 0, 0, 0, 0L, failures);

        when(client.search(anyString(), anyString())).thenReturn(response);
    }

    @SuppressWarnings("unchecked")
    private void givenSearchThrowsError() {
        when(client.search(anyString(), anyString())).thenThrow(IndexNotFoundException.class);
    }

    @Test
    public void testGetDefaultIndexPatternWhenConfigIsCurrentAndDefaultIsNotSet() {
        // given current config doesnt exist
        Map<String, BytesReference> docs = new HashMap<>();
        docs.put(ConfigurationSettings.DEFAULT_KIBANA_VERSION, new BytesArray("{\"somekey\":\"avalue\"}"));
        givenSearchResultForDocuments(".kibana.foo", docs);
        String defaultPattern = utils.getDefaultIndexPattern(".kibana.foo", "test-index-pattern");
        // assert we return empty to do nothing
        assertEquals("test-index-pattern", defaultPattern);
    }

    @Test
    public void testGetDefaultIndexPatternWhenConfigIsCurrentAndDefaultIsSet() {
        // given current config doesnt exist
        Map<String, BytesReference> docs = new HashMap<>();
        docs.put(ConfigurationSettings.DEFAULT_KIBANA_VERSION, new BytesArray("{\"defaultIndex\":\"current\"}"));
        givenSearchResultForDocuments(".kibana.foo", docs);
        String defaultPattern = utils.getDefaultIndexPattern(".kibana.foo", "test-index-pattern");
        // assert we return empty to do nothing
        assertEquals("current", defaultPattern);
    }
    
    @Test
    public void testGetDefaultIndexPatternWhenConfigIsPreviousVersionAndDefaultIsSet() {
        // given current config doesnt exist
        Map<String, BytesReference> docs = new HashMap<>();
        docs.put("4.7.5", new BytesArray("{\"defaultIndex\":\"reallyold\"}"));
        docs.put("5.6.10", new BytesArray("{\"defaultIndex\":\"previous\"}"));
        givenSearchResultForDocuments(".kibana.foo", docs);
        String defaultPattern = utils.getDefaultIndexPattern(".kibana.foo", "test-index-pattern");
        // assert its the default
        assertEquals("previous", defaultPattern);
    }

    @Test
    public void testGetDefaultIndexPatternWhenConfigIsPreviousVersionAndDefaultIsNotSet() {
        // given current config doesnt exist
        Map<String, BytesReference> docs = new HashMap<>();
        docs.put("5.6.10", new BytesArray("{\"somekey\":\"somevalue\"}"));
        givenSearchResultForDocuments(".kibana.foo", docs);
        String defaultPattern = utils.getDefaultIndexPattern(".kibana.foo", "test-index-pattern");
        // assert its the default
        assertEquals("test-index-pattern", defaultPattern);
    }

    @Test
    public void testGetDefaultIndexPatternWhenConfigDoesNotExist() {
        // given current config doesnt exist
        givenSearchResultForDocuments(".kibana.foo",  new HashMap<>());
        // when getting defaults
        String defaultPattern = utils.getDefaultIndexPattern(".kibana.foo", "test-index-pattern");
        // assert its the default
        assertEquals("test-index-pattern", defaultPattern);
    }

    @Test
    public void testGetDefaultIndexPatternWhenIndexDoesNotExist() {
        // given current index doesnt exist
        givenSearchThrowsError();
        // when getting defaults
        String defaultPattern = utils.getDefaultIndexPattern(".kibana.foo", "test-index-pattern");
        // assert its the default
        assertEquals("test-index-pattern", defaultPattern);
    }

    @Test
    public void testFormatIndexPatternForAllAlias() {
        assertEquals(".all", utils.formatIndexPattern(new Project(".all", null)));
    }

    @Test
    public void testFormatIndexPatternForOperations() {
        assertEquals(".operations.*", utils.formatIndexPattern(new Project(".operations", null)));
        assertEquals(".operations.*", utils.formatIndexPattern(new Project(".operations.*", null)));
    }

    @Test
    public void testFormatIndexPatternFromOrphanedIndexPattern() {
        assertEquals(".orphaned.*", utils.formatIndexPattern(new Project(".orphaned", null)));
        assertEquals(".orphaned.*", utils.formatIndexPattern(new Project(".orphaned.*", null)));
    }

    @Test
    public void testFormatIndexPatternForEmptyProject() {
        assertEquals("project.empty-project.*", utils.formatIndexPattern(new Project(".empty-project", null)));
    }

    @Test
    public void testFormatIndexPatternWhenCdmPrefixIsEmpty() {
        PluginSettings settings = mock(PluginSettings.class);
        when(settings.getCdmProjectPrefix()).thenReturn("");
        KibanaUtils utils = new KibanaUtils(settings, client);
        assertEquals("foo.uuid.*", utils.formatIndexPattern(new Project("foo", "uuid")));
    }

    @Test
    public void testFormatIndexPatternWhenCdmPrefixIsSet() {
        assertEquals("project.foo.uuid.*", utils.formatIndexPattern(new Project("foo", "uuid")));
    }

    @Test
    public void testGetProjectsFromIndexPatternsWhenResultsIncludesGeneratedIndexPattern() {
        givenSearchResultToIncludePattern("project.foo.uid.*");
        Set<Project> act = utils.getProjectsFromIndexPatterns(OpenshiftRequestContext.EMPTY);
        Set<Project> exp = new HashSet<>();
        exp.add(new Project("foo", "uid"));
        assertEquals(exp, act);
    }

    @Test
    public void testGetProjectsFromIndexPatternsWhenResultsIncludeAll() {
        givenSearchResultToIncludePattern(".all");
        Set<Project> act = utils.getProjectsFromIndexPatterns(OpenshiftRequestContext.EMPTY);
        Set<Project> exp = new HashSet<>();
        exp.add(new Project(".all", null));
        assertEquals(exp, act);
    }

    @Test
    public void testGetProjectsFromIndexPatternsWhenNoResults() {
        givenSearchResultToIncludePattern(null);
        Set<Project> act = utils.getProjectsFromIndexPatterns(OpenshiftRequestContext.EMPTY);
        Set<Project> exp = new HashSet<>();
        assertEquals(exp, act);
    }

    @Test
    public void testGetProjectsFromIndexPatternsForUserCreatedPatterns() {
        givenSearchResultToIncludePattern("foo.*");
        Set<Project> act = utils.getProjectsFromIndexPatterns(OpenshiftRequestContext.EMPTY);
        Set<Project> exp = new HashSet<>();
        assertEquals(exp, act);
    }

    @Test
    public void testGetProjectFromIndexPatternWhenEmpty() {
        assertEquals(Project.EMPTY, utils.getProjectFromIndexPattern(""));
    }

    @Test
    public void testGetProjectFromIndexPatternWhenOperations() {
        assertEquals(new Project(".operations", null), utils.getProjectFromIndexPattern(".operations"));
    }

    @Test
    public void testGetProjectFromIndexPatternWhenAllAlias() {
        assertEquals(new Project(".all", null), utils.getProjectFromIndexPattern(".all"));
    }

    @Test
    public void testGetProjectFromIndexPatternWhenUserCreated() {
        assertEquals(new Project("foo", null), utils.getProjectFromIndexPattern("foo"));
    }

    @Test
    public void testGetProjectFromIndexPatternWhenSeededPattern() {
        assertEquals(new Project("foo", "uuid"), utils.getProjectFromIndexPattern("project.foo.uuid.*"));
    }

}
