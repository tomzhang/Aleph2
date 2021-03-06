/*******************************************************************************
 * Copyright 2015, The IKANOW Open Source Project.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.ikanow.aleph2.security.service;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.ikanow.aleph2.data_model.objects.shared.AuthorizationBean;
import com.ikanow.aleph2.security.interfaces.IAuthProvider;

public class MapAuthProvider implements IAuthProvider{

	protected static final Logger logger = LogManager.getLogger(MapAuthProvider.class);
	protected Map<String, AuthorizationBean> authMap;
	// internal variable used for testing only
    private ThreadLocal<Long> tlCallCount =
            new ThreadLocal<Long>() {
                @Override protected Long initialValue() {
                    return 0L;
            }
        };
	
	public MapAuthProvider(Map<String,AuthorizationBean> authMap){
		this.authMap = authMap;
	}

	@Override
	public AuthorizationBean getAuthBean(String principalName) {
		Long count  = tlCallCount.get();
		count++;
		tlCallCount.set(count);
		AuthorizationBean a = authMap.get(principalName); 
		logger.debug("MapAuthProvider.getAuthBean:"+a);
		return a;
	}

	public long getCallCount() {
		return tlCallCount.get();
	}
	public void setCallCount(long callCount) {
		this.tlCallCount.set(callCount);
	}

}
