/*******************************************************************************
* Copyright 2015, The IKANOW Open Source Project.
* 
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Affero General Public License, version 3,
* as published by the Free Software Foundation.
* 
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Affero General Public License for more details.
* 
* You should have received a copy of the GNU Affero General Public License
* along with this program. If not, see <http://www.gnu.org/licenses/>.
******************************************************************************/
package com.ikanow.aleph2.management_db.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import org.checkerframework.checker.nullness.qual.NonNull;

import scala.Tuple2;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.ikanow.aleph2.data_model.interfaces.data_services.IManagementDbService;
import com.ikanow.aleph2.data_model.interfaces.data_services.IStorageService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IBasicSearchService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IServiceContext;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketStatusBean;
import com.ikanow.aleph2.data_model.objects.shared.AuthorizationBean;
import com.ikanow.aleph2.data_model.objects.shared.BasicMessageBean;
import com.ikanow.aleph2.data_model.objects.shared.ProjectBean;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils;
import com.ikanow.aleph2.data_model.utils.CrudUtils;
import com.ikanow.aleph2.data_model.utils.ErrorUtils;
import com.ikanow.aleph2.data_model.utils.FutureUtils;
import com.ikanow.aleph2.data_model.utils.Lambdas;
import com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent;
import com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent;
import com.ikanow.aleph2.data_model.utils.FutureUtils.ManagementFuture;
import com.ikanow.aleph2.management_db.controllers.actors.BucketActionSupervisor;
import com.ikanow.aleph2.management_db.data_model.BucketActionMessage;
import com.ikanow.aleph2.management_db.data_model.BucketActionReplyMessage.BucketActionCollectedRepliesMessage;import com.ikanow.aleph2.management_db.data_model.BucketActionRetryMessage;
import com.ikanow.aleph2.management_db.utils.ManagementDbErrorUtils;
import com.ikanow.aleph2.management_db.utils.MgmtCrudUtils;


//TODO (ALEPH-19): Need an additional bucket service that is responsible for actually deleting the data

/** CRUD service for Data Bucket with management proxy
 * @author acp
 */
public class DataBucketCrudService implements IManagementCrudService<DataBucketBean> {

	protected final IStorageService _storage_service;
	
	protected final IManagementDbService _underlying_management_db;
	
	protected final ICrudService<DataBucketBean> _underlying_data_bucket_db;
	protected final ICrudService<DataBucketStatusBean> _underlying_data_bucket_status_db;
	protected final ICrudService<BucketActionRetryMessage> _bucket_action_retry_store;
	
	protected final ManagementDbActorContext _actor_context;
	
	/** Guice invoked constructor
	 * @param underlying_management_db
	 */
	@Inject
	public DataBucketCrudService(final IServiceContext service_context, ManagementDbActorContext actor_context)
	{
		_underlying_management_db = service_context.getService(IManagementDbService.class, Optional.empty());
		_underlying_data_bucket_db = _underlying_management_db.getDataBucketStore();
		_underlying_data_bucket_status_db = _underlying_management_db.getDataBucketStatusStore();
		_bucket_action_retry_store = _underlying_management_db.getRetryStore(BucketActionRetryMessage.class);
		
		_storage_service = service_context.getStorageService();
		
		_actor_context = actor_context;
		
		// Handle some simple optimization of the data bucket CRUD repo:
		Executors.newSingleThreadExecutor().submit(() -> {
			_underlying_data_bucket_db.optimizeQuery(Arrays.asList(
					BeanTemplateUtils.from(DataBucketBean.class).field(DataBucketBean::full_name)));
		});		
	}

	/** User constructor, for wrapping
	 * @param underlying_management_db
	 * @param underlying_data_bucket_db
	 */
	public DataBucketCrudService(final @NonNull IManagementDbService underlying_management_db, 
			final @NonNull IStorageService storage_service,
			final @NonNull ICrudService<DataBucketBean> underlying_data_bucket_db,
			final @NonNull ICrudService<DataBucketStatusBean> underlying_data_bucket_status_db			
			)
	{
		_underlying_management_db = underlying_management_db;
		_underlying_data_bucket_db = underlying_data_bucket_db;
		_underlying_data_bucket_status_db = underlying_data_bucket_status_db;
		_bucket_action_retry_store = _underlying_management_db.getRetryStore(BucketActionRetryMessage.class);
		_actor_context = ManagementDbActorContext.get();		
		_storage_service = storage_service;
	}
		
	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getFilteredRepo(java.lang.String, java.util.Optional, java.util.Optional)
	 */
	@NonNull
	public IManagementCrudService<DataBucketBean> getFilteredRepo(
			final @NonNull String authorization_fieldname,
			final @NonNull Optional<AuthorizationBean> client_auth,
			final @NonNull Optional<ProjectBean> project_auth) 
	{
		return new DataBucketCrudService(_underlying_management_db.getFilteredDb(client_auth, project_auth), 
				_storage_service,
				_underlying_data_bucket_db.getFilteredRepo(authorization_fieldname, client_auth, project_auth),
				_underlying_data_bucket_status_db.getFilteredRepo(authorization_fieldname, client_auth, project_auth)
				);
	}

	/** Validates whether the new or updated bucket is valid: both in terms of authorization and in terms of format
	 * @param bucket
	 * @return
	 */
	@NonNull
	protected Collection<BasicMessageBean> validateBucket(DataBucketBean bucket, boolean is_new) {
		
		// (will live with this being mutable)
		final LinkedList<BasicMessageBean> errors = new LinkedList<BasicMessageBean>();

		final JsonNode bucket_json = BeanTemplateUtils.toJson(bucket);
		
		// Check for missing fields
		
		ManagementDbErrorUtils.NEW_BUCKET_ERROR_MAP.keySet().stream()
			.filter(s -> !bucket_json.has(s))
			.forEach(s -> 
				errors.add(new BasicMessageBean(
						new Date(), // date
						false, // success
						"CoreManagementDbService",
						BucketActionMessage.NewBucketActionMessage.class.getSimpleName(),
						null, // message code
						ErrorUtils.get(ManagementDbErrorUtils.NEW_BUCKET_ERROR_MAP.get(s), Optional.ofNullable(bucket._id()).orElse("(new)")),
						null // details						
					)));
		
		// More complex missing field checks
		
		//TODO more complex rules
		
		//TODO multi buckets
		
		//TODO various authorization
		
		return errors;
	}
	
	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#storeObject(java.lang.Object, boolean)
	 */
	@NonNull
	public ManagementFuture<Supplier<Object>> storeObject(final @NonNull DataBucketBean new_object, final boolean replace_if_present)
	{
		try {
			
			// New bucket vs update
			
			final Optional<DataBucketBean> old_bucket = Lambdas.exec(() -> {
				try {
					if (replace_if_present && (null != new_object._id())) {
						return _underlying_data_bucket_db.getObjectById(new_object._id()).get();
					}
					else {
						return Optional.<DataBucketBean>empty();
					}
				}
				catch (Exception e) {
					throw new RuntimeException(e);
				}
			});

			// Validation
			
			final Collection<BasicMessageBean> errors = validateBucket(new_object, old_bucket.isPresent());
		
			if (!errors.isEmpty()) {
				return FutureUtils.createManagementFuture(
						FutureUtils.returnError(new RuntimeException("Bucket not valid, see management channels")),
						CompletableFuture.completedFuture(errors)
						);
			}
			
			// OK if the bucket is validated we can store it (and create a status object)
					
			CompletableFuture<Supplier<Object>> ret_val = _underlying_data_bucket_db.storeObject(new_object, false);

			// Check if the low level store has failed:
			final Object id = Lambdas.exec(() -> {
				try {
					return ret_val.get().get().toString();
				}
				catch (Exception e) { // just pass the raw result back up
					return FutureUtils.createManagementFuture(ret_val);
				}				
			});

			// Create the directories
			
			//TODO (ALEPH-19): create the directories
			
			// We've created a new bucket but is it enabled or not?
			
			final CompletableFuture<Optional<DataBucketStatusBean>> bucket_status = _underlying_data_bucket_status_db.getObjectById(id);
			final Optional<DataBucketStatusBean> status = Lambdas.exec(() -> {
				try {
					return bucket_status.get();
				}
				catch (Exception e) { // just pass the raw result back up
					// Hmm not sure what's going on - just treat this like the status didn't exist:
					return Optional.<DataBucketStatusBean>empty();
				}				
			});
			final boolean is_suspended = Optional.ofNullable(status.map(stat -> stat.suspended()).orElse(false)).orElse(false);

			// OK if we're here then it's time to notify any interested harvesters

			final BucketActionMessage.NewBucketActionMessage new_message = 
					new BucketActionMessage.NewBucketActionMessage(new_object, is_suspended);
			
			final CompletableFuture<BucketActionCollectedRepliesMessage> f =
					Optional.ofNullable(new_object.multi_node_enabled()).orElse(false)
					? 					
					BucketActionSupervisor.askDistributionActor(
							_actor_context.getBucketActionSupervisor(), 
							(BucketActionMessage)new_message, 
							Optional.empty())
					:
					BucketActionSupervisor.askChooseActor(
							_actor_context.getBucketActionSupervisor(), 
							(BucketActionMessage)new_message, 
							Optional.empty());
			
			final CompletableFuture<Collection<BasicMessageBean>> management_results =
					f.<Collection<BasicMessageBean>>thenApply(replies -> {
						return replies.replies(); 
					});

			// Convert BucketActionCollectedRepliesMessage into a management side-channel:
			return FutureUtils.createManagementFuture(ret_val, management_results);					
		}
		catch (Exception e) {
			// This is a serious enough exception that we'll just leave here
			return FutureUtils.createManagementFuture(
					FutureUtils.returnError(e));			
		}
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#storeObject(java.lang.Object)
	 */
	@NonNull
	public ManagementFuture<Supplier<Object>> storeObject(final @NonNull DataBucketBean new_object) {
		return this.storeObject(new_object, false);
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#storeObjects(java.util.List, boolean)
	 */
	@NonNull
	public ManagementFuture<Tuple2<Supplier<List<Object>>, Supplier<Long>>> storeObjects(final @NonNull List<DataBucketBean> new_objects, final boolean continue_on_error) {
		throw new RuntimeException("This method is not supported, call storeObject on each object separately");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#storeObjects(java.util.List)
	 */
	@NonNull
	public ManagementFuture<Tuple2<Supplier<List<Object>>, Supplier<Long>>> storeObjects(final @NonNull List<DataBucketBean> new_objects) {
		return this.storeObjects(new_objects, false);
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#optimizeQuery(java.util.List)
	 */
	@NonNull
	public ManagementFuture<Boolean> optimizeQuery(final @NonNull List<String> ordered_field_list) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_db.optimizeQuery(ordered_field_list));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#deregisterOptimizedQuery(java.util.List)
	 */
	public boolean deregisterOptimizedQuery(final @NonNull List<String> ordered_field_list) {
		return _underlying_data_bucket_db.deregisterOptimizedQuery(ordered_field_list);
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	@NonNull
	public ManagementFuture<Optional<DataBucketBean>> getObjectBySpec(final @NonNull QueryComponent<DataBucketBean> unique_spec) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_db.getObjectBySpec(unique_spec));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.List, boolean)
	 */
	@NonNull
	public ManagementFuture<Optional<DataBucketBean>> getObjectBySpec(
			final @NonNull QueryComponent<DataBucketBean> unique_spec,
			final @NonNull List<String> field_list, final boolean include) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_db.getObjectBySpec(unique_spec, field_list, include));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getObjectById(java.lang.Object)
	 */
	@NonNull
	public ManagementFuture<Optional<DataBucketBean>> getObjectById(final @NonNull Object id) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_db.getObjectById(id));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getObjectById(java.lang.Object, java.util.List, boolean)
	 */
	@NonNull
	public ManagementFuture<Optional<DataBucketBean>> getObjectById(final @NonNull Object id,
			final @NonNull List<String> field_list, final boolean include) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_db.getObjectById(id, field_list, include));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	@NonNull
	public ManagementFuture<ICrudService.Cursor<DataBucketBean>> getObjectsBySpec(
			final @NonNull QueryComponent<DataBucketBean> spec)
	{
		return FutureUtils.createManagementFuture(_underlying_data_bucket_db.getObjectsBySpec(spec));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.List, boolean)
	 */
	@NonNull
	public ManagementFuture<ICrudService.Cursor<DataBucketBean>> getObjectsBySpec(
			final @NonNull QueryComponent<DataBucketBean> spec, final @NonNull List<String> field_list,
			final boolean include)
	{
		return FutureUtils.createManagementFuture(_underlying_data_bucket_db.getObjectsBySpec(spec, field_list, include));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#countObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	@NonNull
	public ManagementFuture<Long> countObjectsBySpec(final @NonNull QueryComponent<DataBucketBean> spec) {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_db.countObjectsBySpec(spec));
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#countObjects()
	 */
	@NonNull
	public ManagementFuture<Long> countObjects() {
		return FutureUtils.createManagementFuture(_underlying_data_bucket_db.countObjects());
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#deleteObjectById(java.lang.Object)
	 */
	@NonNull
	public ManagementFuture<Boolean> deleteObjectById(final @NonNull Object id) {		
		final CompletableFuture<Optional<DataBucketBean>> result = _underlying_data_bucket_db.getObjectById(id);		
		try {
			if (result.get().isPresent()) {
				return this.deleteBucket(result.get().get());
			}
			else {
				return FutureUtils.createManagementFuture(CompletableFuture.completedFuture(false));
			}
		}
		catch (Exception e) {
			// This is a serious enough exception that we'll just leave here
			return FutureUtils.createManagementFuture(
					FutureUtils.returnError(e));			
		}
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#deleteObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	@NonNull
	public ManagementFuture<Boolean> deleteObjectBySpec(final @NonNull QueryComponent<DataBucketBean> unique_spec) {
		final CompletableFuture<Optional<DataBucketBean>> result = _underlying_data_bucket_db.getObjectBySpec(unique_spec);
		try {
			if (result.get().isPresent()) {
				return this.deleteBucket(result.get().get());
			}
			else {
				return FutureUtils.createManagementFuture(CompletableFuture.completedFuture(false));
			}
		}
		catch (Exception e) {
			// This is a serious enough exception that we'll just leave here
			return FutureUtils.createManagementFuture(
					FutureUtils.returnError(e));			
		}
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#deleteObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent)
	 */
	public ManagementFuture<Long> deleteObjectsBySpec(final @NonNull QueryComponent<DataBucketBean> spec) {
		
		// Need to do these one by one:

		final CompletableFuture<Cursor<DataBucketBean>> to_delete = _underlying_data_bucket_db.getObjectsBySpec(spec);
		
		try {
			return MgmtCrudUtils.applyCrudPredicate(to_delete, this::deleteBucket);
		}
		catch (Exception e) {
			// This is a serious enough exception that we'll just leave here
			return FutureUtils.createManagementFuture(
					FutureUtils.returnError(e));
		}
	}

	/** Internal function to delete the bucket, while notifying active users of the bucket
	 * @param to_delete
	 * @return a management future containing the result 
	 */
	@NonNull
	private ManagementFuture<Boolean> deleteBucket(final @NonNull DataBucketBean to_delete) {
		try {
			final CompletableFuture<Boolean> delete_reply = _underlying_data_bucket_db.deleteObjectById(to_delete._id());
			
			if (delete_reply.get()) {
			
				// Get the status and delete it:
				
				final CompletableFuture<Optional<DataBucketStatusBean>> future_status_bean =
					_underlying_data_bucket_status_db.updateAndReturnObjectBySpec(
							CrudUtils.allOf(DataBucketStatusBean.class).when(DataBucketStatusBean::_id, to_delete._id()),
							Optional.empty(), CrudUtils.update(DataBucketStatusBean.class).deleteObject(), Optional.of(true), Collections.emptyList(), false);

				final Optional<DataBucketStatusBean> status_bean = Lambdas.exec(() -> {
					try {
						return future_status_bean.get(); 
					}
					catch (Exception e) { // just treat this as not finding anything
						return Optional.<DataBucketStatusBean>empty();
					}										
				});
				
				final BucketActionMessage.DeleteBucketActionMessage delete_message = new
						BucketActionMessage.DeleteBucketActionMessage(to_delete, 
								new HashSet<String>(
										Optional.ofNullable(
												status_bean.isPresent() ? status_bean.get().node_affinity() : null)
										.orElse(Collections.emptyList())
										));
				
				final CompletableFuture<Collection<BasicMessageBean>> management_results =
					MgmtCrudUtils.applyRetriableManagementOperation(_actor_context, _bucket_action_retry_store, 
							delete_message, source -> {
								return new BucketActionMessage.DeleteBucketActionMessage(
										delete_message.bucket(),
										new HashSet<String>(Arrays.asList(source)));	
							});
				
				// Convert BucketActionCollectedRepliesMessage into a management side-channel:
				return FutureUtils.createManagementFuture(delete_reply, management_results);					
			}	
			else {
				return FutureUtils.createManagementFuture(delete_reply);
			}
		}
		catch (Exception e) {
			// This is a serious enough exception that we'll just leave here
			return FutureUtils.createManagementFuture(
					FutureUtils.returnError(e));			
		}
	}
	
	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#deleteDatastore()
	 */
	@NonNull
	public ManagementFuture<Boolean> deleteDatastore() {
		throw new RuntimeException("This method is not supported");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getRawCrudService()
	 */
	@NonNull
	public IManagementCrudService<JsonNode> getRawCrudService() {
		throw new RuntimeException("DataBucketCrudService.getRawCrudService not supported");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getSearchService()
	 */
	@NonNull
	public Optional<IBasicSearchService<DataBucketBean>> getSearchService() {
		return _underlying_data_bucket_db.getSearchService();
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService#getUnderlyingPlatformDriver(java.lang.Class, java.util.Optional)
	 */
	@SuppressWarnings("unchecked")
	@NonNull
	public <T> T getUnderlyingPlatformDriver(final @NonNull Class<T> driver_class,
			final @NonNull Optional<String> driver_options)
	{
		if (driver_class == ICrudService.class) {
			return (@NonNull T) _underlying_data_bucket_db;
		}
		else {
			throw new RuntimeException("DataBucketCrudService.getUnderlyingPlatformDriver not supported");
		}
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#updateObjectById(java.lang.Object, com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent)
	 */
	@Override
	public @NonNull 
	ManagementFuture<Boolean> updateObjectById(
			final @NonNull Object id, final @NonNull UpdateComponent<DataBucketBean> update) {
		throw new RuntimeException("DataBucketCrudService.update* not supported");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#updateObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.Optional, com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Boolean> updateObjectBySpec(
			final @NonNull QueryComponent<DataBucketBean> unique_spec,
			final @NonNull Optional<Boolean> upsert,
			final @NonNull UpdateComponent<DataBucketBean> update) {
		throw new RuntimeException("DataBucketCrudService.update* not supported");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#updateObjectsBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.Optional, com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent)
	 */
	@Override
	public @NonNull ManagementFuture<Long> updateObjectsBySpec(
			final @NonNull QueryComponent<DataBucketBean> spec,
			final @NonNull Optional<Boolean> upsert,
			final @NonNull UpdateComponent<DataBucketBean> update) {
		throw new RuntimeException("DataBucketCrudService.update* not supported");
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.shared_services.IManagementCrudService#updateAndReturnObjectBySpec(com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent, java.util.Optional, com.ikanow.aleph2.data_model.utils.CrudUtils.UpdateComponent, java.util.Optional, java.util.List, boolean)
	 */
	@Override
	public @NonNull ManagementFuture<Optional<DataBucketBean>> updateAndReturnObjectBySpec(
			final @NonNull QueryComponent<DataBucketBean> unique_spec,
			final @NonNull Optional<Boolean> upsert,
			final @NonNull UpdateComponent<DataBucketBean> update,
			final @NonNull Optional<Boolean> before_updated, @NonNull List<String> field_list,
			final boolean include) {
		throw new RuntimeException("DataBucketCrudService.update* not supported");
	}
}
