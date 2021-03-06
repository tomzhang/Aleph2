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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import scala.Tuple2;

import com.ikanow.aleph2.security.interfaces.IRoleProvider;

public class MapRoleProvider implements IRoleProvider{

	protected static final Logger logger = LogManager.getLogger(MapRoleProvider.class);

	protected Map<String, Set<String>> rolesMap;
	protected Map<String, Set<String>> permissionsMap;
	// internal variable used for testing only
   private ThreadLocal<Long> tlCallCount =
        new ThreadLocal<Long>() {
            @Override protected Long initialValue() {
                return 0L;
        }
    };
    
	public MapRoleProvider(Map<String,Set<String>> rolesMap, Map<String,Set<String>> permissionsMap){
		this.rolesMap = rolesMap;
		this.permissionsMap = permissionsMap;
	}
	@Override
	public Tuple2<Set<String>, Set<String>> getRolesAndPermissions(String principalName) {
		Set<String> roles = rolesMap.get(principalName);
		Set<String> permissions = permissionsMap.get(principalName);
		if(roles==null){
			roles =  new HashSet<String>();
		}
		if(permissions==null){
			permissions =  new HashSet<String>();
		}
		Long count  = tlCallCount.get();
		count++;
		tlCallCount.set(count);
		Tuple2<Set<String>, Set<String>> t2 = new Tuple2<Set<String>, Set<String>>(roles,permissions);
		logger.debug("MapRoleProvider.getRolesAndPermissions:"+t2);
		return t2;
	}
	public long getCallCount() {
		return tlCallCount.get();
	}
	public void setCallCount(long callCount) {
		this.tlCallCount.set(callCount);
	}

}
