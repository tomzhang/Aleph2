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

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import scala.Tuple2;

import com.fasterxml.jackson.databind.JsonNode;
import com.ikanow.aleph2.data_model.objects.shared.AuthorizationBean;
import com.ikanow.aleph2.data_model.objects.shared.ProjectBean;
import com.ikanow.aleph2.data_model.utils.CrudServiceUtils;
import com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent;
import com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent;
import com.ikanow.aleph2.data_model.utils.FutureUtils.ManagementFuture;

/** Override of the CRUD service that provides a side channel with additional management information in its future
 * @author acp
 *
 * @param <T>
 */
public interface IManagementCrudService<O> extends ICrudService<O> {
	public static interface IReadOnlyManagementCrudService<O> extends IManagementCrudService<O>, IReadOnlyCrudService<O> {}
	
	//////////////////////////////////////////////////////

	// Authorization and project filtering:
	
	/** Returns a copy of the CRUD service that is filtered based on the client (user) and project rights
	 * @param authorization_fieldname the fieldname in the bean that determines where the per-bean authorization is held
	 * @param client_auth Optional specification of the user's access rights
	 * @param project_auth Optional specification of the projects's access rights
	 * @return The filtered CRUD repo
	 */
	@Override
	IManagementCrudService<O> getFilteredRepo(final String authorization_fieldname, final Optional<AuthorizationBean> client_auth, final Optional<ProjectBean> project_auth);
	
	/** Returns a possibly read only version of the CRUD service. The interface is the same, but writable calls will exception.
	 * @param is_read_only - whether to return the read only version or not
	 * @return the possibly read only version of the CRUD service
	 */
	default IManagementCrudService<O> readOnlyVersion(boolean is_read_only) {
		return is_read_only ? new CrudServiceUtils.ReadOnlyManagementCrudService<>(this) : this;
	}
	/** Returns a definitely read only version of the CRUD service. The interface is the same, but writable calls will exception.
	 * @return the definitely read only version of the CRUD service
	 */
	default IReadOnlyManagementCrudService<O> readOnlyVersion() {
		if (IReadOnlyManagementCrudService.class.isAssignableFrom(this.getClass())) {
			return (IReadOnlyManagementCrudService<O>) this;
		}
		else {
			return (IReadOnlyManagementCrudService<O>) readOnlyVersion(true);
		}
	}	
	
	//////////////////////////////////////////////////////
	
	// *C*REATE
	
	/** Stores the specified object in the database, optionally failing if it is already present
	 *  If the "_id" field of the object is not set then it is assigned
	 * @param new_object
	 * @param replace_if_present if true then any object with the specified _id is overwritten
	 * @return A future containing the _id (filled in if not present in the object) - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Supplier<Object>> storeObject(final O new_object, final boolean replace_if_present);

	/** Stores the specified object in the database, failing if it is already present
	 *  If the "_id" field of the object is not set then it is assigned
	 * @param new_object
	 * @return A future containing the _id (filled in if not present in the object) - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Supplier<Object>> storeObject(final O new_object);
	
	/**
	 * @param objects - a list of objects to insert
	 * @param continue_on_error if true then duplicate objects are ignored (not inserted) but the store continues
	 * @return A future containing the list of _ids (filled in if not present in the object), and the number of docs retrieved - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Tuple2<Supplier<List<Object>>, Supplier<Long>>> storeObjects(final List<O> new_objects, final boolean continue_on_error);
	
	/**
	 * @param objects - a list of objects to insert, failing out as soon as a duplicate is inserted 
	 * @return A future containing the list of _ids (filled in if not present in the object), and the number of docs retrieved - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Tuple2<Supplier<List<Object>>, Supplier<Long>>> storeObjects(final List<O> new_objects);
	
	//////////////////////////////////////////////////////
	
	// *R*ETRIEVE
	
	/** Registers that you wish to optimize specific queries
	 * @param ordered_field_list a list of the fields in the query
	 * @return a future describing if the optimization was successfully completed - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Boolean> optimizeQuery(final List<String> ordered_field_list);	

	/** Returns the object (in optional form to handle its not existing) given a simple object template that contains a unique search field (but other params are allowed)
	 * @param unique_spec A specification (must describe at most one object) generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @return A future containing an optional containing the object, or Optional.empty() - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Optional<O>> getObjectBySpec(final QueryComponent<O> unique_spec);

	/** Returns the object (in optional form to handle its not existing) given a simple object template that contains a unique search field (but other params are allowed)
	 * @param unique_spec A specification (must describe at most one object) generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @param field_list list of fields to return, supports "." nesting
	 * @param include - if true, the field list is to be included; if false, to be excluded
	 * @return A future containing an optional containing the object, or Optional.empty()  - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Optional<O>> getObjectBySpec(final QueryComponent<O> unique_spec, final List<String> field_list, final boolean include);

	/** Returns the object given the id
	 * @param id the id of the object
	 * @return A future containing an optional containing the object, or Optional.empty() - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Optional<O>> getObjectById(final Object id);	

	/** Returns the object given the id
	 * @param id the id of the object
	 * @param field_list List of fields to return, supports "." nesting
	 * @param include - if true, the field list is to be included; if false, to be excluded
	 * @return A future containing an optional containing the object, or Optional.empty() - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Optional<O>> getObjectById(final Object id, final List<String> field_list, final boolean include);	
	
	/** Returns the list of objects specified by the spec (all fields returned)
	 * @param spec A specification generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @return A future containing a (possibly empty) list of Os - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Cursor<O>> getObjectsBySpec(final QueryComponent<O> spec);
	
	/** Returns the list of objects/order/limit specified by the spec. Note that the resulting object should be run within a try-with-resources or read fully.
	 * @param spec A specification generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @param field_list List of fields to return, supports "." nesting
	 * @param include - if true, the field list is to be included; if false, to be excluded
	 * @return A future containing a (possibly empty) list of Os - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Cursor<O>> getObjectsBySpec(final QueryComponent<O> spec, final List<String> field_list, final boolean include);

	/** Counts the number of objects specified by the spec (all fields returned)
	 * @param spec A specification generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @return A future containing the number of matching objects - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Long> countObjectsBySpec(final QueryComponent<O> spec);
	
	/** Counts the number of objects in the data store
	 * @param spec A specification generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @return A future containing the number of matching objects - accessing the future will also report on errors via ExecutionException 
	 */
	@Override
	ManagementFuture<Long> countObjects();
	
	//////////////////////////////////////////////////////
	
	// *U*PDATE
	
	/** Updates the specified object
	 * @param id the id of the object to update
	 * @param update A specification to update the object (UpdateOperator.unset with field "" deletes the object) 
	 * @return a future describing if the update was successful - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Boolean> updateObjectById(final Object id, final UpdateComponent<O> update);

	/** Updates the specified object
	 * @param unique_spec A specification (must describe at most one object) generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @param upsert if specified and true then inserts the object if it doesn't exist
	 * @param update A specification to update the object (UpdateOperator.unset with field "" deletes the object) 
	 * @return a future describing if the update was successful - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Boolean> updateObjectBySpec(final QueryComponent<O> unique_spec, final Optional<Boolean> upsert, final UpdateComponent<O> update);

	/** Updates the specified object
	 * @param spec A specification generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @param upsert if specified and true then inserts the object if it doesn't exist
	 * @param update A specification to update the object (UpdateOperator.unset with field "" deletes the object) 
	 * @return a future describing the number of objects updated - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Long> updateObjectsBySpec(final QueryComponent<O> spec, final Optional<Boolean> upsert, final UpdateComponent<O> update);

	/** Updates the specified object, returning the updated version
	 * @param unique_spec A specification (must describe at most one object) generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @param upsert if specified and true then inserts the object if it doesn't exist
	 * @param update A specification to update the object (UpdateOperator.unset with field "" deletes the object) 
	 * @param before_updated if specified and "true" then returns the object _before_ it is modified
	 * @param field_list List of fields to return, supports "." nesting
	 * @param include - if true, the field list is to be included; if false, to be excluded
	 * @return a future containing the object, if found (or upserted) - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Optional<O>> updateAndReturnObjectBySpec(final QueryComponent<O> unique_spec, final Optional<Boolean> upsert, final UpdateComponent<O> update, final Optional<Boolean> before_updated, final List<String> field_list, final boolean include);
	
	//////////////////////////////////////////////////////
	
	// *D*ELETE
	
	/** Deletes the specific object
	 * @param id the id of the object to update
	 * @return a future describing if the delete was successful - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Boolean> deleteObjectById(final Object id);

	/** Deletes the specific object
	 * @param unique_spec A specification (must describe at most one object) generated by CrudUtils.allOf(...) (all fields must be match) or CrudUtils.anyOf(...) (any fields must match) together with extra fields generated by .withAny(..), .withAll(..), present(...) or notPresent(...)   
	 * @return a future describing if the delete was successful - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Boolean> deleteObjectBySpec(final QueryComponent<O> unique_spec);

	/** Deletes the specific object
	 * @param A specification that must be initialized via CrudUtils.anyOf(...) and then the desired fields added via .exists(<field or getter>)
	 * @return a future describing the number of objects updated - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Long> deleteObjectsBySpec(final QueryComponent<O> spec);

	/** Deletes the entire datastore and all documents, including mappings/indexes/metdata etc
	 * @return a future describing if the delete was successful - accessing the future will also report on errors via ExecutionException
	 */
	@Override
	ManagementFuture<Boolean> deleteDatastore();
	
	//////////////////////////////////////////////////////
	
	// OTHER:

	/** Returns an identical version of this CRUD service but using JsonNode instead of beans (which may save serialization)
	 * @return the JsonNode-genericized version of this same CRUD service
	 */
	@Override
	IManagementCrudService<JsonNode> getRawService();

	/** Returns a secured version of the CRUD service. 
	 * @param service_context - the system service context
	 * @param auth_bean - the security context of the calling user
	 * @return the secured version of the CRUD service
	 */
	default IManagementCrudService<O> secured(IServiceContext service_context, AuthorizationBean auth_bean) {
		if (auth_bean==null) {
			return this;
		}
		else {
			return service_context.getSecurityService().secured(this, auth_bean);
		}
	}	
	
}
