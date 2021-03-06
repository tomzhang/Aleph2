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
package com.ikanow.aleph2.data_model.interfaces.shared_services;

import java.util.Collection;
import java.util.Optional;

import scala.Tuple3;

import com.google.inject.Provider;
import com.ikanow.aleph2.data_model.interfaces.data_services.IColumnarService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IDocumentService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IGeospatialService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IGraphService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService;
import com.ikanow.aleph2.data_model.interfaces.data_services.ISearchIndexService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IStorageService;
import com.ikanow.aleph2.data_model.interfaces.data_services.ITemporalService;
import com.ikanow.aleph2.data_model.objects.shared.GlobalPropertiesBean;

/**
 * Helper class to give access to all the configured services available
 * in the application.  This should be used as a passthrough to
 * {@link com.ikanow.aleph2.data_model.utils.ModuleUtils#getService(Class, Optional)}
 * 
 * @author Burch
 *
 */
public interface IServiceContext {

	/////////////////////////////////////////////////////////////////////
		
	//generic get service interface
	/** (safe for use when called from a context)
	* Enables access to the data services available in the system.  The currently
	* configured instance of the class passed in will be returned if it exists, null otherwise.
	* 
	* @param serviceClazz The class of the resource you want to access e.g. ISecurityService.class
	* @return the data service requested or null if it does not exist
	*/
	public <I extends IUnderlyingService> Optional<I> getService(Class<I> serviceClazz, Optional<String> serviceName);

	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Enables access to the data services available in the system.  The currently
	* configured instance of the class passed in will be returned if it exists, null otherwise.
	* 
	* @param serviceClazz The class of the resource you want to access e.g. ISecurityService.class
	* @return the data service requested or null if it does not exist
	*/
	public <I extends IUnderlyingService> Optional<Provider<I>> getServiceProvider(Class<I> serviceClazz, Optional<String> serviceName);	
	
	/** Generates a list of all service providers in the system - note to get a unique set of services, deduplicate on the first element in the tuple
	 *  first element is the service provider, second is the name of the interface, third is the service name
	 * @return
	 */
	public Collection<Tuple3<Provider<? extends IUnderlyingService>, Class<? extends IUnderlyingService>, Optional<String>>> listServiceProviders(); 
	
	/////////////////////////////////////////////////////////////////////
	
	//utility getters for common services
	
	/** (safe for use when called from a context)
	* Returns an instance of the currently configured columnar service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<IColumnarService> getColumnarService();
	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Returns an instance of the currently configured columnar service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<Provider<IColumnarService>> getColumnarServiceProvider();
	
	/** (safe for use when called from a context)
	* Returns an instance of the currently configured document service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<IDocumentService> getDocumentService();
	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Returns an instance of the currently configured document service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<Provider<IDocumentService>> getDocumentServiceProvider();
	
	/** (safe for use when called from a context)
	* Returns an instance of the currently configured geospatial service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<IGeospatialService> getGeospatialService();
	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Returns an instance of the currently configured geospatial service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<Provider<IGeospatialService>> getGeospatialServiceProvider();
	
	/** (safe for use when called from a context)
	* Returns an instance of the currently configured graph service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<IGraphService> getGraphService();
	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Returns an instance of the currently configured graph service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<Provider<IGraphService>> getGraphServiceProvider();
	
	/** (safe for use when called from a context)
	* Returns an instance of the currently configured _core_ mangement db service (which sits above the actual technology configured for the management db service via service.ManagementDbService.*).
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public IManagementDbService getCoreManagementDbService();
	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Returns an instance of the currently configured _core_ mangement db service (which sits above the actual technology configured for the management db service via service.ManagementDbService.*).
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Provider<IManagementDbService> getCoreManagementDbServiceProvider();
	
	/** (safe for use when called from a context)
	* Returns an instance of the currently configured search index service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<ISearchIndexService> getSearchIndexService();
	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Returns an instance of the currently configured search index service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<Provider<ISearchIndexService>> getSearchIndexServiceProvider();
	
	/** (safe for use when called from a context)
	* Returns an instance of the currently configured storage index service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public IStorageService getStorageService();
	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Returns an instance of the currently configured storage index service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Provider<IStorageService> getStorageServiceProvider();
	
	/** (safe for use when called from a context)
	* Returns an instance of the currently configured temporal service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<ITemporalService> getTemporalService();
	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Returns an instance of the currently configured temporal service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Optional<Provider<ITemporalService>> getTemporalServiceProvider();
	
	/////////////////////////////////////////////////////////////////////
	
	//security service is related to data services
	/** (safe for use when called from a context)
	* Returns an instance of the currently configured security service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public ISecurityService getSecurityService();
	/** THIS VERSION IS PREFERRED IN GUICE-MANAGED CODE SINCE IT HANDLES CIRCULAR DEPENDENCIES
	 * (in contexts the non-provider version is fine though)
	* Returns an instance of the currently configured security service.
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public Provider<ISecurityService> getSecurityServiceProvider();

	/////////////////////////////////////////////////////////////////////
	
	//security service is related to data services
	/**
	* Returns an instance of the global configuration parameters
	* 
	* This is a helper function that just calls {@link getDataService(Class<I>)}
	* 
	* @return
	*/
	public GlobalPropertiesBean getGlobalProperties();
	

}
