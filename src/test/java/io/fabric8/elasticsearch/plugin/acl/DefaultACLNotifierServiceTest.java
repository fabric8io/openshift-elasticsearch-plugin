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

import static org.mockito.Mockito.*;

import java.util.concurrent.ExecutorService;

import org.elasticsearch.common.logging.ESLogger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class DefaultACLNotifierServiceTest {

	private static final String ACTION = "cluster:read/write";
	
	@Mock private ExecutorService runner;
	@Mock private ESLogger logger;
	@Mock private SearchGuardACLActionRequestListener listener;
	
	private DefaultACLNotifierService service;
	
	@Before
	public void setUp() throws Exception {
		service = new DefaultACLNotifierService(logger);
		service.setExecutorService(runner);
	}

	@Test
	public void nullNotifierShouldReturnSilently() {
		service = new DefaultACLNotifierService(logger);
		service.notify(ACTION);
		verify(runner, times(0)).execute(any(Runnable.class));
	}
	
	@Test
	public void noListenersShouldSkipNotifier(){
		service.notify(ACTION);
		verify(runner, times(0)).execute(any(Runnable.class));
	}
	
	@Test
	public void listenersShouldBeNotifiedOfActions(){
		doAnswer(new Answer<Object>() {
			@Override
			public Object answer(InvocationOnMock invocation) throws Throwable {
				Object[] args = invocation.getArguments();
				((Runnable)args[0]).run();
				return null;
			}
		}).when(runner).execute(any(Runnable.class));
		service.addActionRequestListener(listener);
		service.notify(ACTION);
		verify(listener,times(1)).onSearchGuardACLActionRequest(ACTION);
	}
	
	
}
