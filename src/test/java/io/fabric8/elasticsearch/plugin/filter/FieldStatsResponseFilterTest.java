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

package io.fabric8.elasticsearch.plugin.filter;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.action.fieldstats.FieldStatsRequest;
import org.elasticsearch.action.support.ActionFilterChain;
import org.elasticsearch.tasks.Task;
import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import io.fabric8.elasticsearch.plugin.PluginClient;

@SuppressWarnings("rawtypes")
public class FieldStatsResponseFilterTest {

    private FieldStatsResponseFilter filter;
    private ActionFilterChain chain = mock(ActionFilterChain.class);
    private String action = FieldStatsResponseFilter.INDICES_FIELD_STATS_READ_ACTION;
    private ActionResponse response = mock(ActionResponse.class);
    private ActionRequest request = mock(ActionRequest.class);
    private ActionListener listener = mock(ActionListener.class);
    private Task task = mock(Task.class);
    private RuntimeException exception = mock(RuntimeException.class);
    private PluginClient client = mock(PluginClient.class);
    
    @Before
    public void setUp() throws Exception {
        filter = new FieldStatsResponseFilter(client);
    }

    @Test
    public void testFilterIsOrderedToBeLast() {
        assertEquals(Integer.MAX_VALUE, filter.order());
    }
    
    @SuppressWarnings("unchecked")
    private void givenAnExeptionOccurs() {
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionListener listener = (ActionListener) args[3];
                listener.onFailure(exception);
                return null;
            }
        }).when(chain).proceed(any(Task.class), anyString(), any(ActionRequest.class), any(ActionListener.class));
    }
    
    private FieldStatsRequest givenAFieldStatsRequest() {
        FieldStatsRequest request = mock(FieldStatsRequest.class);
        when(request.indices()).thenReturn(new String [] {"project.foo.uuid.*"});
        return request;
    }

    @Test
    public void testApplyActionRequestMakesPassThroughCallToOnFailureWithModifiedReturnValueWhenIndexIsMissing() {
        //given the chain will be called and wrappers the listener
        givenAnExeptionOccurs();
        FieldStatsRequest request = givenAFieldStatsRequest();
        when(client.indexExists(anyString())).thenReturn(false);
        
        //when
        filter.apply(task, action, request, listener, chain );
        
        //then the original listener should be notified with the modified exception
        verify(listener).onFailure(any(ElasticsearchException.class));
    }

    @Test
    public void testApplyActionRequestMakesPassThroughCallToOnFailureWithModifiedReturnValueWhenIndexExists() {
        //given the chain will be called and wrappers the listener
        givenAnExeptionOccurs();
        FieldStatsRequest request = givenAFieldStatsRequest();
        when(client.indexExists(anyString())).thenReturn(true);
        
        //when
        filter.apply(task, action, request, listener, chain );
        
        //then the original listener should be notified with the modified exception
        verify(listener).onFailure(any(ElasticsearchException.class));
    }
    
    @Test
    public void testApplyActionRequestMakesPassThroughCallToOnFailureWhenRequestIsNotReadFieldsRequest() {
        //given the chain will be called and wrappers the listener
        givenAnExeptionOccurs();
        
        //when
        filter.apply(task, action, request, listener, chain );
        
        //then the original listener should be notified
        verify(listener).onFailure(exception);
    }
    
    @Test
    public void testApplyActionRequestMakesPassThroughCallToOnFailureWhenActionIsNotOfInterest() {
        //given the chain will be called and wrappers the listener
        givenAnExeptionOccurs();
        request = mock(FieldStatsRequest.class);
        //when
        filter.apply(task, "someaction", request, listener, chain );
        
        //then the original listener should be notified
        verify(listener).onFailure(exception);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testApplyActionRequestMakesPassThroughCallToOnResponse() {
        //given the chain will be called and wrappers the listener
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object[] args = invocation.getArguments();
                ActionListener listener = (ActionListener) args[3];
                listener.onResponse(response);
                return null;
            }
        }).when(chain).proceed(any(Task.class), anyString(), any(ActionRequest.class), any(ActionListener.class));
        
        //when
        filter.apply(task, action, request, listener, chain );
        
        //then the original listener should be notified
        verify(listener).onResponse(response);
    }

}
