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
 ******************************************************************************/
package com.ikanow.aleph2.data_model.utils;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.ikanow.aleph2.data_model.interfaces.data_import.IHarvestContext;

public class ContextUtils {
	
	/** Returns the configured context object, for use in modules not part of the Aleph2 dependency injection
	 * @param signature can either be the fully qualified class name, or "<FQ class name>:arbitrary_config_string", which is then passed to the context via IHarvestContext.initializeNewContext 
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws ClassNotFoundException
	 */
	public static @NonNull IHarvestContext getHarvestContext(@NonNull String signature) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		String[] clazz_and_config = signature.split(":", 2);
		@SuppressWarnings("unchecked")
		Class<IHarvestContext> harvest_clazz = (Class<IHarvestContext>) Class.forName(clazz_and_config[0]);
		IHarvestContext context = harvest_clazz.newInstance();
		if (clazz_and_config.length > 1) {
			context.initializeNewContext(clazz_and_config[1]);
		}
		return context;
	}
	//TODO getAnalyticsContext
	//TODO getAccessContext?
}