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
package com.ikanow.aleph2.data_model.interfaces.data_access;

import com.google.inject.Provider;
import com.ikanow.aleph2.data_model.interfaces.data_layers.IColumnarDbService;
import com.ikanow.aleph2.data_model.interfaces.data_layers.IDataService;
import com.ikanow.aleph2.data_model.interfaces.data_layers.IDocumentDbService;
import com.ikanow.aleph2.data_model.interfaces.data_layers.IGeospatialService;
import com.ikanow.aleph2.data_model.interfaces.data_layers.IGraphDbService;
import com.ikanow.aleph2.data_model.interfaces.data_layers.IManagementDbService;
import com.ikanow.aleph2.data_model.interfaces.data_layers.ISearchIndexService;
import com.ikanow.aleph2.data_model.interfaces.data_layers.IStorageService;
import com.ikanow.aleph2.data_model.interfaces.data_layers.ITemporalService;
import com.ikanow.aleph2.data_model.interfaces.shared.ISecurityService;

/**
 * Gives an app access to all the currently configured data services.
 * 
 * @author Burch
 *
 */
public interface IAccessContext {	
	enum DataServiceType {
		ColumnarDbService,
		DocumentDbService,
		GeospatialService,
		GraphDbService,
		ManagementDbService,
		SearchIndexService,
		StorageService,
		TemporalService,
		SecurityService
	}
	
	//generic get service interface
	//public void setDataService(String serviceName, Provider<?> service);	
	public IDataService getDataService(String serviceName);
	
	//utility getters for common services
	public IColumnarDbService getColumnarDbService();
	public IDocumentDbService getDocumentDbService();
	public IGeospatialService getGeospatialService();
	public IGraphDbService getGraphDbService();	
	public IManagementDbService getManagementDbService();
	public ISearchIndexService getSearchIndexService();
	public IStorageService getStorageIndexService();
	public ITemporalService getTemporalService();
	
	//security service is related to data services
	public ISecurityService getSecurityService();
}
