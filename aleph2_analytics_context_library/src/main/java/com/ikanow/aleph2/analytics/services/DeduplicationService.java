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
package com.ikanow.aleph2.analytics.services;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.Level;

import scala.Tuple2;
import scala.Tuple3;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.ikanow.aleph2.analytics.data_model.DedupConfigBean;
import com.ikanow.aleph2.analytics.utils.ErrorUtils;
import com.ikanow.aleph2.core.shared.utils.BatchRecordUtils;
import com.ikanow.aleph2.core.shared.utils.DataServiceUtils;
import com.ikanow.aleph2.data_model.interfaces.data_analytics.IBatchRecord;
import com.ikanow.aleph2.data_model.interfaces.data_import.IEnrichmentBatchModule;
import com.ikanow.aleph2.data_model.interfaces.data_import.IEnrichmentModuleContext;
import com.ikanow.aleph2.data_model.interfaces.data_services.IDocumentService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IBucketLogger;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ICrudService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IDataServiceProvider;
import com.ikanow.aleph2.data_model.interfaces.shared_services.ILoggingService;
import com.ikanow.aleph2.data_model.interfaces.shared_services.IUnderlyingService;
import com.ikanow.aleph2.data_model.objects.data_import.AnnotationBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataSchemaBean.DocumentSchemaBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataSchemaBean.DocumentSchemaBean.CustomPolicy;
import com.ikanow.aleph2.data_model.objects.data_import.DataSchemaBean.DocumentSchemaBean.DeduplicationPolicy;
import com.ikanow.aleph2.data_model.objects.data_import.DataSchemaBean.DocumentSchemaBean.DeduplicationTiming;
import com.ikanow.aleph2.data_model.objects.data_import.EnrichmentControlMetadataBean;
import com.ikanow.aleph2.data_model.objects.shared.AuthorizationBean;
import com.ikanow.aleph2.data_model.objects.shared.BasicMessageBean;
import com.ikanow.aleph2.data_model.objects.shared.SharedLibraryBean;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils;
import com.ikanow.aleph2.data_model.utils.BeanTemplateUtils.MethodNamingHelper;
import com.ikanow.aleph2.data_model.utils.BucketUtils;
import com.ikanow.aleph2.data_model.utils.CrudUtils;
import com.ikanow.aleph2.data_model.utils.Lambdas;
import com.ikanow.aleph2.data_model.utils.Optionals;
import com.ikanow.aleph2.data_model.utils.Patterns;
import com.ikanow.aleph2.data_model.utils.TimeUtils;
import com.ikanow.aleph2.data_model.utils.Tuples;
import com.ikanow.aleph2.data_model.utils.CrudUtils.QueryComponent;
import com.ikanow.aleph2.data_model.utils.JsonUtils;
import com.ikanow.aleph2.data_model.utils.SetOnce;

import fj.data.Either;
import fj.data.Validation;

/** An enrichment module that will perform deduplication using the provided document_schema
 * @author Alex
 */
public class DeduplicationService implements IEnrichmentBatchModule {
	protected final static ObjectMapper _mapper = BeanTemplateUtils.configureMapper(Optional.empty());
	protected final static MethodNamingHelper<AnnotationBean> _annot = BeanTemplateUtils.from(AnnotationBean.class);
	
	protected final SetOnce<ICrudService<JsonNode>> _dedup_context = new SetOnce<>();
	protected final SetOnce<IEnrichmentModuleContext> _context = new SetOnce<>();
	protected final SetOnce<DocumentSchemaBean> _doc_schema = new SetOnce<>();
	protected final SetOnce<String> _timestamp_field = new SetOnce<>();
	protected final SetOnce<Boolean> _deduplication_is_disabled = new SetOnce<>();
	protected final SetOnce<Boolean> _is_system_dedup_stage = new SetOnce<>(); // if executes the system level deduplication or if is part of the app logic (affects logging)
	
	protected final SetOnce<IEnrichmentBatchModule> _custom_handler = new SetOnce<>();
	protected final SetOnce<DeduplicationEnrichmentContext> _custom_context = new SetOnce<>();
	protected final SetOnce<EnrichmentControlMetadataBean> _control = new SetOnce<>();
	
	protected final SetOnce<List<String>> _dedup_fields = new SetOnce<>();
	protected final SetOnce<DeduplicationPolicy> _policy = new SetOnce<>();	
		
	protected final SetOnce<IBucketLogger> _logger = new SetOnce<>();
	
	public static class MutableStats {
		int nonduplicate_keys = 0;		
		int duplicates_incoming = 0;
		int duplicates_existing = 0;
		int duplicate_keys = 0;
		int deleted = 0;
	}
	protected final MutableStats _mutable_stats = new MutableStats();
	
	protected final LinkedList<CompletableFuture<Long>> mutable_uncompleted_deletes = new LinkedList<>();
	
	//TODO (ALEPH-20): move this into the ES service
	public static class ElasticsearchTechnologyOverride {
		protected ElasticsearchTechnologyOverride() {}
		public ElasticsearchTechnologyOverride(final String default_modifier, final Map<String, String> field_override) {
			this.default_modifier = default_modifier;
			this.field_override = field_override;
		}
		private String default_modifier;
		private Map<String, String> field_override;
		public final String default_modifier() { return Optional.ofNullable(default_modifier).orElse(""); }
		public final Map<String, String> field_override() { return Optional.ofNullable(field_override).orElse(Collections.emptyMap()); }
	}
	protected final SetOnce<Function<String, String>> _db_mapper = new SetOnce<>();
	
	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.data_import.IEnrichmentBatchModule#validateModule(com.ikanow.aleph2.data_model.interfaces.data_import.IEnrichmentModuleContext, com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean, com.ikanow.aleph2.data_model.objects.data_import.EnrichmentControlMetadataBean)
	 */
	@Override
	public Collection<BasicMessageBean> validateModule(
			IEnrichmentModuleContext context, DataBucketBean bucket,
			EnrichmentControlMetadataBean control) {
		
		final LinkedList<BasicMessageBean> mutable_errs = new LinkedList<>();
		
		// Validation
		
		// 1) Check that has doc schema enabled unless override set 

		final DedupConfigBean dedup_config = BeanTemplateUtils.from(Optional.ofNullable(control.config()).orElse(Collections.emptyMap()), DedupConfigBean.class).get();
		
		final DocumentSchemaBean doc_schema = Optional.ofNullable(dedup_config.doc_schema_override()).orElse(bucket.data_schema().document_schema()); //(exists by construction)
		if (null == doc_schema) { // Has to either have a doc schema or an override 
			mutable_errs.add(ErrorUtils.buildErrorMessage(this.getClass().getSimpleName(), "validateModule", ErrorUtils.get(ErrorUtils.MISSING_DOCUMENT_SERVICE)));
			return mutable_errs; //(no point going any further here)
		}

		if (!Optional.ofNullable(doc_schema.lookup_service_override()).filter(s -> !s.isEmpty()).isPresent()) {
			final boolean doc_schema_enabled = 
					Optionals.of(() -> bucket.data_schema().document_schema()).map(ds -> Optional.ofNullable(ds.enabled()).orElse(true)).orElse(false);
			if (!doc_schema_enabled) {
				mutable_errs.add(ErrorUtils.buildErrorMessage(this.getClass().getSimpleName(), "validateModule", ErrorUtils.get(ErrorUtils.MISSING_DOCUMENT_SERVICE)));
			}
		}
		//(else up to the user to ensure that the required service is included)
		
		// 1.5) Validate that the service override is valid
		
		final Validation<String, Tuple2<Optional<Class<? extends IUnderlyingService>>, Optional<String>>> service_to_use = getDataService(doc_schema);					
		
		if (service_to_use.isFail()) {
			mutable_errs.add(ErrorUtils.buildErrorMessage(this.getClass().getSimpleName(), "validateModule", service_to_use.fail()));
		}
		
		// 2) Validate any child modules

		Optional<EnrichmentControlMetadataBean> custom_config =  
				Optionals.ofNullable(doc_schema.custom_deduplication_configs()).stream().filter(cfg -> Optional.ofNullable(cfg.enabled()).orElse(true)).findFirst();
		
		custom_config.ifPresent(cfg -> {
			mutable_errs.addAll(
					getEnrichmentModules(context, cfg).stream().flatMap(module -> module.validateModule(context, bucket, cfg).stream()).collect(Collectors.toList()));
		});
		return mutable_errs;
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.data_import.IEnrichmentBatchModule#onStageInitialize(com.ikanow.aleph2.data_model.interfaces.data_import.IEnrichmentModuleContext, com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean, com.ikanow.aleph2.data_model.objects.data_import.EnrichmentControlMetadataBean, scala.Tuple2, java.util.Optional)
	 */
	@Override
	public void onStageInitialize(final IEnrichmentModuleContext context,
			final DataBucketBean bucket, 
			final EnrichmentControlMetadataBean dedup_control,
			final Tuple2<ProcessingStage, ProcessingStage> previous_next,
			final Optional<List<String>> next_grouping_fields)
	{
		_context.set(context);
		_control.set(dedup_control);
		
		context.getServiceContext().getService(ILoggingService.class, Optional.empty()).map(s -> s.getSystemLogger(bucket)).ifPresent(logger -> _logger.set(logger));
		
		final DedupConfigBean dedup_config = BeanTemplateUtils.from(Optional.ofNullable(dedup_control.config()).orElse(Collections.emptyMap()), DedupConfigBean.class).get();
		
		final DocumentSchemaBean doc_schema = Optional.ofNullable(dedup_config.doc_schema_override()).orElse(bucket.data_schema().document_schema()); //(exists by construction)
		_is_system_dedup_stage.set(null == dedup_config.doc_schema_override());
		
		_deduplication_is_disabled.set(
				(null == doc_schema.deduplication_policy())
					&& Optionals.ofNullable(doc_schema.deduplication_fields()).isEmpty()
					&& Optionals.ofNullable(doc_schema.deduplication_contexts()).isEmpty()
				);
		
		// override defaults:
		_doc_schema.set( 
				BeanTemplateUtils.clone(doc_schema)
					.with(DocumentSchemaBean::enabled, Optional.ofNullable(doc_schema.enabled()).orElse(true))
					.with(DocumentSchemaBean::deduplication_timing, Optional.ofNullable(doc_schema.deduplication_timing()).orElse(DeduplicationTiming.custom))
					.with(DocumentSchemaBean::deduplication_policy, Optional.ofNullable(doc_schema.deduplication_policy()).orElse(DeduplicationPolicy.leave))
					.with(DocumentSchemaBean::custom_policy, Optional.ofNullable(doc_schema.custom_policy()).orElse(CustomPolicy.strict))
					.with(DocumentSchemaBean::custom_finalize_all_objects, Optional.ofNullable(doc_schema.custom_finalize_all_objects()).orElse(false))
					.with(DocumentSchemaBean::delete_unhandled_duplicates, 
							Optional.ofNullable(doc_schema.delete_unhandled_duplicates()).orElse(
									CustomPolicy.very_strict == Optional.ofNullable(doc_schema.custom_policy()).orElse(CustomPolicy.strict)))
					.with(DocumentSchemaBean::allow_manual_deletion, Optional.ofNullable(doc_schema.allow_manual_deletion()).orElse(false))
				.done()
				);
		
		final String timestamp_field = Optionals.of(() -> bucket.data_schema().temporal_schema().time_field())
											.orElseGet(() -> AnnotationBean.ROOT_PATH + ".tp");
		_timestamp_field.set(timestamp_field);

		final DataBucketBean context_holder =
				Optional.ofNullable(doc_schema.deduplication_contexts())
					.filter(l -> !l.isEmpty()) // (if empty or null then fall back to...)				
					.map(contexts -> 
						BeanTemplateUtils.build(DataBucketBean.class)
								.with(DataBucketBean::multi_bucket_children, ImmutableSet.<String>builder().addAll(contexts).build())
						.done().get()
					)
					.orElse(bucket);
		
		//(valid by construction - see validateSchema)
		final Validation<String, Tuple2<Optional<Class<? extends IUnderlyingService>>, Optional<String>>> service_to_use = getDataService(_doc_schema.get());					
		
		// Get secured data service -> CRUD for id checking and deletion
		final Optional<ICrudService<JsonNode>> maybe_read_crud =				
			context.getServiceContext().getService(service_to_use.success()._1().get(), service_to_use.success()._2())
					.map(ds -> ((IDataServiceProvider)ds).secured(context.getServiceContext(), new AuthorizationBean(bucket.owner_id())))
					.flatMap(ds -> ds.getDataService())
					.flatMap(ds -> 
						_doc_schema.get().delete_unhandled_duplicates() || _doc_schema.get().allow_manual_deletion()
							? ds.getUpdatableCrudService(JsonNode.class, Arrays.asList(context_holder), Optional.empty())
							: ds.getReadableCrudService(JsonNode.class, Arrays.asList(context_holder), Optional.empty()).map(crud -> (ICrudService<JsonNode>)crud)
					)
					//(just ensure it has a read/update interface even though the update might not be used)
			;
		
		maybe_read_crud.ifPresent(read_crud -> _dedup_context.set(read_crud));
		
		//TODO (ALEPH-20): move this into the DB (See related top level comment)
		final ElasticsearchTechnologyOverride tech_override = 
				BeanTemplateUtils.from(
						Optional.ofNullable(_doc_schema.get().technology_override_schema()).orElse(Collections.emptyMap()), ElasticsearchTechnologyOverride.class).get();	
		
		_db_mapper.set(f -> {
				return AnnotationBean._ID.equals(f)
					   ? f
					   :tech_override.field_override().getOrDefault(f.replace(".", ":"), f + tech_override.default_modifier())
					   ;
				});											

		_dedup_fields.set(Optional.ofNullable(_doc_schema.get().deduplication_fields()).orElse(Arrays.asList(AnnotationBean._ID)));
		_policy.set(Optional.ofNullable( _doc_schema.get().deduplication_policy()).orElse(DeduplicationPolicy.leave));
		
		if ((DeduplicationPolicy.custom == _policy.get()) || (DeduplicationPolicy.custom_update == _policy.get())) {
						
			Optional<EnrichmentControlMetadataBean> custom_config =  
					Optionals.ofNullable(_doc_schema.get().custom_deduplication_configs()).stream().filter(cfg -> Optional.ofNullable(cfg.enabled()).orElse(true)).findFirst();
			
			custom_config.ifPresent(cfg -> {
				
				getEnrichmentModules(context, cfg).stream().findFirst().ifPresent(module -> _custom_handler.set(module));			
				
				_custom_context.set(new DeduplicationEnrichmentContext(context, _doc_schema.get(), j -> getKeyFieldsAgain(j, getKeyFields(_dedup_fields.get()))));
				
				_custom_handler.optional().ifPresent(base_module -> base_module.onStageInitialize(_custom_context.get(), bucket, cfg, previous_next, next_grouping_fields));
			});
		}
	}

	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.data_import.IEnrichmentBatchModule#onObjectBatch(java.util.stream.Stream, java.util.Optional, java.util.Optional)
	 */
	@Override
	public void onObjectBatch(final Stream<Tuple2<Long, IBatchRecord>> batch,
			final Optional<Integer> batch_size, 
			final Optional<JsonNode> grouping_key)
	{
		if (_deduplication_is_disabled.get()) { 
			// no deduplication, generally shouldn't be here...
			//.. but if we are, make do the best we can
			batch.forEach(t2 -> _context.get().emitImmutableObject(t2._1(), t2._2().getJson(), Optional.empty(), Optional.empty(), Optional.empty()));
			return;
		}		
		
		// Create big query
		
		final Tuple3<QueryComponent<JsonNode>, List<Tuple2<JsonNode, Tuple2<Long, IBatchRecord>>>, Either<String, List<String>>> fieldinfo_dedupquery_keyfields = 
				getDedupQuery(batch, _dedup_fields.get(), _db_mapper.get());

		// Get duplicate results
		
		final Tuple2<List<String>, Boolean> fields_include = getIncludeFields(_policy.get(), _dedup_fields.get(), _timestamp_field.get());

		final CompletableFuture<Iterator<JsonNode>> dedup_res =
				fieldinfo_dedupquery_keyfields._2().isEmpty()
				? 
				CompletableFuture.completedFuture(Collections.<JsonNode>emptyList().iterator())
				: 
				_dedup_context.get().getObjectsBySpec(fieldinfo_dedupquery_keyfields._1(), fields_include._1(), fields_include._2()).thenApply(cursor -> cursor.iterator());

		// Wait for it to finsh
		
			//(create handy results structure if so)
			final LinkedHashMap<JsonNode, LinkedList<Tuple3<Long, IBatchRecord, ObjectNode>>> mutable_obj_map =				
					fieldinfo_dedupquery_keyfields._2().stream().collect(						
							Collector.of(
								() -> new LinkedHashMap<JsonNode, LinkedList<Tuple3<Long, IBatchRecord, ObjectNode>>>(), 
								(acc, t2) -> {
									// (ie only the first element is added, duplicate elements are removed)
									final Tuple3<Long, IBatchRecord, ObjectNode> t3 = Tuples._3T(t2._2()._1(), t2._2()._2(), _mapper.createObjectNode());
									acc.compute(t2._1(), (k, v) -> {
										final LinkedList<Tuple3<Long, IBatchRecord, ObjectNode>> new_list = 
												(null == v)
												? new LinkedList<>()
												: v;
										new_list.add(t3);
										return new_list;
									});
								},
								(map1, map2) -> {
									map1.putAll(map2);
									return map1;
								}));						
				
		//TODO (ALEPH-20): add timestamps to annotation
		//TODO (ALEPH-20): support different timestamp fields for the different buckets
		//TODO (ALEPH-20): really need to support >1 current enrichment job 
		//                 ^^(Really really longer term you should be able to decide what objects you want and what you don't  <- NOTE: don't remember what i meant here)
				
		final Iterator<JsonNode> cursor = dedup_res.join();
		
		// Handle the results
		
		final Stream<JsonNode> records_to_delete = Lambdas.get(() -> {
			if (isCustom(_doc_schema.get().deduplication_policy()) || _doc_schema.get().delete_unhandled_duplicates()) {
				return Optionals.streamOf(cursor, true)
							.collect(Collectors.groupingBy(ret_obj -> getKeyFieldsAgain(ret_obj, fieldinfo_dedupquery_keyfields._3())))
							.entrySet()
							.stream()
							.<JsonNode>flatMap(kv -> {
								
								final Optional<JsonNode> maybe_key = kv.getKey();
								final Optional<LinkedList<Tuple3<Long, IBatchRecord, ObjectNode>>> matching_records = maybe_key.map(key -> mutable_obj_map.get(key));
								
								// Stats:
								_mutable_stats.duplicate_keys++;
								_mutable_stats.duplicates_existing += kv.getValue().size();
								_mutable_stats.duplicates_incoming += matching_records.map(l -> l.size()).orElse(0);
								
								//DEBUG
								//System.out.println("?? " + kv.getValue().size() + " vs " + maybe_key + " vs " + matching_records.map(x -> Integer.toString(x.size())).orElse("(no match)"));
								
								return matching_records.<Stream<JsonNode>>map(records -> 
									handleDuplicateRecord(_doc_schema.get(), _custom_handler.optional().map(handler -> Tuples._2T(handler, this._custom_context.get())),
															_timestamp_field.get(), records, kv.getValue(), maybe_key.get(), mutable_obj_map))
															.orElse(Stream.empty());
							})
							;
			}
			else {
				Optionals.streamOf(cursor, true)
					.forEach(ret_obj -> {
						final Optional<JsonNode> maybe_key = getKeyFieldsAgain(ret_obj, fieldinfo_dedupquery_keyfields._3());
						final Optional<LinkedList<Tuple3<Long, IBatchRecord, ObjectNode>>> matching_records = maybe_key.map(key -> mutable_obj_map.get(key)); 
		
						//DEBUG
						//System.out.println("?? " + ret_obj + " vs " + maybe_key + " vs " + matching_record.map(x -> x._2().getJson().toString()).orElse("(no match)"));
						
						// Stats:
						_mutable_stats.duplicate_keys++;
						_mutable_stats.duplicates_existing++;
						_mutable_stats.duplicates_incoming += matching_records.map(l -> l.size()).orElse(0);
						
						matching_records.ifPresent(records -> 
							handleDuplicateRecord(_doc_schema.get(), _custom_handler.optional().map(handler -> Tuples._2T(handler, this._custom_context.get())),
													_timestamp_field.get(), records, Arrays.asList(ret_obj), maybe_key.get(), mutable_obj_map));
					});		
				return Stream.<JsonNode>empty();
			}
		});
		
		final List<Object> ids = records_to_delete
				.map(j -> jsonToObject(j))
				.filter(j -> null != j)
				.collect(Collectors.toList())
				;
		
		if (!ids.isEmpty()) { // fire a bulk deletion request
			mutable_uncompleted_deletes.add(_dedup_context.get().deleteObjectsBySpec(CrudUtils.allOf().withAny(AnnotationBean._ID, ids)));
			
			_mutable_stats.deleted += ids.size();
			
			//(quickly see if we can reduce the number of outstanding requests)
			final Iterator<CompletableFuture<Long>> it = mutable_uncompleted_deletes.iterator();
			while (it.hasNext()) {
				final CompletableFuture<Long> cf = it.next();
				if (cf.isDone()) {
					it.remove();
				}
				else break; // ie stop as soon as we hit one that isn't complete)
			}
		}
		
		_mutable_stats.nonduplicate_keys += mutable_obj_map.size();			
		
		if (Optional.ofNullable(_doc_schema.get().custom_finalize_all_objects()).orElse(false)) {
			mutable_obj_map.entrySet().stream()
				.forEach(kv -> handleCustomDeduplication(_custom_handler.optional().map(handler -> Tuples._2T(handler, this._custom_context.get())), 
															kv.getValue(), Collections.emptyList(), kv.getKey()))
				;			
		}
		else { // Just emit the last element of each grouped object set
			mutable_obj_map.values().stream()
				.map(t -> t.peekLast())
				.forEach(t -> _context.get().emitImmutableObject(t._1(), t._2().getJson(), Optional.of(t._3()), Optional.empty(), Optional.empty()))
				;
		}
	}

	/**Tidiness util (converts a long/int/double/float/stirng jsonnode value to its atomic type
	 * @param obj
	 * @return
	 */
	protected static Object jsonToObject(final JsonNode j) {
		return Patterns.match(j).<Object>andReturn()
				.when(jval -> jval.isTextual(), jval -> jval.asText())
				.when(jval -> jval.isLong(), jval -> jval.asLong())
				.when(jval -> jval.isInt(), jval -> jval.asInt())
				.when(jval -> jval.isFloat() || jval.isDouble(), jval -> jval.asDouble())
				.otherwise(__ -> null);		
	}
	
	/** Tidiness util
	 * @param policy
	 * @return
	 */
	private static boolean isCustom(final DeduplicationPolicy policy) {
		return (DeduplicationPolicy.custom == policy) || (DeduplicationPolicy.custom_update == policy);
	}
	
	/**Tidiness util
	 * @param in
	 * @return
	 */
	private static Stream<JsonNode> deleteOtherDuplicates(final Stream<JsonNode> in) {
		return in.skip(1).map(j -> j.get(AnnotationBean._ID)).filter(j -> null != j);
	}
	
	/** The heart of the dedup logic
	 *  (everything gets ordered in the correct order except if policy is custom, in which case the ordering is a
	 *   bit arbitrary anyway)
	 * @param policy
	 * @param context
	 * @param timestamp_field
	 * @param new_record
	 * @param old_record
	 * @param key
	 * @param mutable_obj_map
	 * @returns a stream of objects to delete efficiently
	 */
	protected static Stream<JsonNode> handleDuplicateRecord(final DocumentSchemaBean config,
			Optional<Tuple2<IEnrichmentBatchModule, DeduplicationEnrichmentContext>> custom_handler,
			final String timestamp_field,
			final LinkedList<Tuple3<Long, IBatchRecord, ObjectNode>> new_records, 
			final List<JsonNode> old_records,
			final JsonNode key,
			final Map<JsonNode, LinkedList<Tuple3<Long, IBatchRecord, ObjectNode>>> mutable_obj_map
			)
	{
		return Patterns.match(config.deduplication_policy()).<Stream<JsonNode>>andReturn()
				.when(p -> p == DeduplicationPolicy.leave, __ -> {
					mutable_obj_map.remove(key); //(drop new record)
					return Stream.empty();
				})
				.when(p -> p == DeduplicationPolicy.update, __ -> {
					final Tuple3<Long, IBatchRecord, ObjectNode> last_record = new_records.peekLast();
					final JsonNode old_record = old_records.stream().findFirst().get();
					if (newRecordUpdatesOld(timestamp_field, last_record._2().getJson(), old_record)) {
						last_record._3().set(AnnotationBean._ID, old_record.get(AnnotationBean._ID));
						return config.delete_unhandled_duplicates() ? deleteOtherDuplicates(old_records.stream()) : Stream.empty();
					}
					else {
						mutable_obj_map.remove(key); //(drop new record)				
						return Stream.empty();
					}
				})
				.when(p -> p == DeduplicationPolicy.overwrite, __ -> {
					final Tuple3<Long, IBatchRecord, ObjectNode> last_record = new_records.peekLast();
					// Just update the new record's "_id" field
					final JsonNode old_record = old_records.stream().findFirst().get();
					last_record._3().set(AnnotationBean._ID, old_record.get(AnnotationBean._ID));
					return config.delete_unhandled_duplicates() ? deleteOtherDuplicates(old_records.stream()) : Stream.empty();
				})
				.when(p -> p == DeduplicationPolicy.custom_update, __ -> {
					final Tuple3<Long, IBatchRecord, ObjectNode> last_record = new_records.peekLast();
					final JsonNode old_record = old_records.stream().findFirst().get();
					if (newRecordUpdatesOld(timestamp_field, last_record._2().getJson(), old_record)) {
						mutable_obj_map.remove(key); // (since the "final step" logic is responsible for calling the update code)
						return handleCustomDeduplication(custom_handler, new_records, old_records, key);
					}
					else {
						mutable_obj_map.remove(key); //(drop new record)
						return Stream.empty();
					}
				})
				.otherwise(__ -> {
					mutable_obj_map.remove(key); // (since the "final step" logic is responsible for calling the update code)		
					return handleCustomDeduplication(custom_handler, new_records, old_records, key);
				});
	}
	
	/** Returns the minimal set of includes to return from the dedup query
	 * @param policy
	 * @param dedup_fields
	 * @param timestamp_field
	 * @return
	 */
	protected static Tuple2<List<String>, Boolean> getIncludeFields(final DeduplicationPolicy policy, final List<String> dedup_fields, String timestamp_field) {
		final Tuple2<List<String>, Boolean> fields_include = Optional.of(Patterns.match(policy).<Tuple2<List<String>, Boolean>>andReturn()
				.when(p -> p == DeduplicationPolicy.leave, __ -> Tuples._2T(Arrays.asList(AnnotationBean._ID), true))
				.when(p -> p == DeduplicationPolicy.update, __ -> Tuples._2T(Arrays.asList(AnnotationBean._ID, timestamp_field), true))
				.when(p -> p == DeduplicationPolicy.overwrite, __ -> Tuples._2T(Arrays.asList(AnnotationBean._ID), true))
				.otherwise(__ -> Tuples._2T(Arrays.asList(), false))
			)
			.map(t2 -> t2._2() 
					? Tuples._2T(Stream.concat(t2._1().stream(), dedup_fields.stream()).collect(Collectors.toList()), t2._2())
					: t2
				)
			.get()
			;

		return fields_include;
	}
	
	/** Logic to perform the custom deduplication with the current and new versions
	 * @param maybe_custom_handler
	 * @param new_record
	 * @param old_record
	 * @returns list of Json objects to delete
	 */
	protected static Stream<JsonNode> handleCustomDeduplication(
			Optional<Tuple2<IEnrichmentBatchModule, DeduplicationEnrichmentContext>> maybe_custom_handler,
			final List<Tuple3<Long, IBatchRecord, ObjectNode>> new_records, final Collection<JsonNode> old_records, final JsonNode key)
	{
		return maybe_custom_handler.map(handler_context -> {
			handler_context._2().resetMutableState(old_records, key);
			
			final Consumer<IEnrichmentBatchModule> handler = new_module -> {
				final Stream<Tuple2<Long, IBatchRecord>> dedup_stream =
						Stream.concat(
								new_records.stream().map(t3 -> Tuples._2T(t3._1(), t3._2())),
								old_records.stream().map(old_record -> Tuples._2T(-1L, (IBatchRecord)(new BatchRecordUtils.InjectedJsonBatchRecord(old_record))))
								);
				
				final int batch_size = new_records.size();
				
				new_module.onObjectBatch(dedup_stream, 
						Optional.of(batch_size).filter(__ -> !old_records.isEmpty()), // (ie leave batch size blank if there's no dedup) 
						Optional.of(key));
				
				new_module.onStageComplete(false);
			};
			
			handler.accept(handler_context._1());
			
			return handler_context._2().getObjectIdsToDelete();
		})
		.orElse(Stream.empty())
		;
	}
	
	/** Compares the old and new records' timestamps (if either doesn't exist then assume we're leaving)
	 *  (so that if the time isn't present then doesn't hammer the DB)
	 * @param timestamp_field
	 * @param new_record
	 * @param old_record
	 * @return
	 */
	protected static boolean newRecordUpdatesOld(String timestamp_field, final JsonNode new_record, final JsonNode old_record) {
		final Optional<JsonNode> old_timestamp = JsonUtils.getProperty(timestamp_field, old_record);
		final Optional<JsonNode> new_timestamp = JsonUtils.getProperty(timestamp_field, new_record);
		final Optional<Tuple2<Long, Long>> maybe_old_new =
			old_timestamp.flatMap(old_ts -> getTimestampFromJsonNode(old_ts))
							.flatMap(old_ts -> 
										new_timestamp
											.flatMap(new_ts -> getTimestampFromJsonNode(new_ts))
											.map(new_ts -> Tuples._2T(old_ts, new_ts)));
		
		return maybe_old_new.filter(old_new -> old_new._2() > old_new._1()).isPresent();
	}
	
	/** Converts a JsonNode to a timestamp if possible
	 * @param in
	 * @return
	 */
	public static Optional<Long> getTimestampFromJsonNode(final JsonNode in) {
		if (null == in) {
			return Optional.empty();
		}
		else if (in.isNumber()) {
			return Optional.of(in.asLong());
		}
		else if (in.isTextual()) {
			return Optional.ofNullable(TimeUtils.parseIsoString(in.asText()).validation(fail -> null, success -> success.getTime()));
		}
		else {
			return Optional.empty();
		}
	}
	
	/** Utility that performs a cheap part of getDedupQuery
	 * @param dedup_fields
	 * @return
	 */
	protected static Either<String, List<String>> getKeyFields(final List<String> dedup_fields) {
		if (1 == dedup_fields.size()) { // this is a simpler case
			final String key_field = dedup_fields.stream().findFirst().get();
			return Either.left(key_field);
		}
		else {
			return Either.right(dedup_fields);
		}
	}
	
	/** Creates the query and some associated metadata (see also getKeyFields)
	 * @param batch
	 * @param dedup_fields
	 * @param db_field_mapper - allows the fields to be transformed (initial workaround for some ES issues, can just leave and pass f->f in once no longer needed)
	 * @return a 3-tuple containing: the query to apply, the list of records indexed by the key, the field-or-fields that form the key
	 */
	protected static Tuple3<QueryComponent<JsonNode>, List<Tuple2<JsonNode, Tuple2<Long, IBatchRecord>>>, Either<String, List<String>>> getDedupQuery(
			final Stream<Tuple2<Long, IBatchRecord>> batch, 
			final List<String> dedup_fields,
			final Function<String, String> db_field_mapper
	)
	{
		if (1 == dedup_fields.size()) { // this is a simpler case
			final String key_field = dedup_fields.stream().findFirst().get();
			final List<Tuple2<JsonNode, Tuple2<Long, IBatchRecord>>> field_info = extractKeyField(batch, key_field);
			return Tuples._3T(
					CrudUtils.allOf().withAny(db_field_mapper.apply(key_field), field_info.stream().map(t2 -> JsonUtils.jacksonToJava(t2._1())).collect(Collectors.toList())).limit(Integer.MAX_VALUE)
					,
					field_info
					,
					getKeyFields(dedup_fields)
					);
		}
		else {
			final List<Tuple2<JsonNode, Tuple2<Long, IBatchRecord>>> field_info = extractKeyFields(batch, dedup_fields);
			
			final Stream<QueryComponent<JsonNode>> elements =
				field_info.stream()
					.map(t2 -> {
						return Optionals.streamOf(t2._1().fields(), false)
						 	.reduce(CrudUtils.allOf(),
						 			(acc, kv) -> acc.when(db_field_mapper.apply(kv.getKey()), JsonUtils.jacksonToJava(kv.getValue())),
						 			(acc1, acc2) -> acc1 // (not possible because not parallel()
						 			)
						 	;
					})
					;
			
			final QueryComponent<JsonNode> query_dedup = 
					Optional.of(CrudUtils.anyOf(elements).limit(Integer.MAX_VALUE))
							//(consider putting a sort in here? I haven't so far because it seems like a lot of work when mostly there will just be a single object
							// for each batch so we're doing a lot of sorting completely unnecessarily
							// ... the downside is that if there are a large number of duplicates then the memory usage could get painful if they're not sorted...)
							.get()
							;
			
			return Tuples._3T(query_dedup, field_info, getKeyFields(dedup_fields));
		}		
	}
	
	/** Utility to find fragments of a json object from single/multiple fields
	 * @param in
	 * @param key_fields
	 * @return
	 */
	protected static Optional<JsonNode> getKeyFieldsAgain(final JsonNode in, Either<String, List<String>> key_field_or_fields) {
		return  key_field_or_fields.either(
				key_field -> extractKeyField(in, key_field)
				,
				key_fields -> extractKeyFields(in, key_fields)
				)
				;
	}
	
	/** Utility to find a single field for dedup purposes 
	 * @param in - stream of JSON objects
	 * @param key_field
	 * @return
	 */
	protected static List<Tuple2<JsonNode, Tuple2<Long, IBatchRecord>>> extractKeyField(final Stream<Tuple2<Long, IBatchRecord>> in, final String key_field) {
		return in
				.map(x -> extractKeyField(x._2().getJson(), key_field).map(y -> Tuples._2T(y, x)).orElse(null))
				.filter(x -> null != x)
				.collect(Collectors.toList())
				;
	}

	/** Utility to find a single field for dedup purposes 
	 * @param in - single JSON object
	 * @param key_field
	 * @return
	 */
	protected static Optional<JsonNode> extractKeyField(final JsonNode in, final String key_field) {
		return JsonUtils.getProperty(key_field, in).filter(j -> j.isValueNode());
	}

	
	/** Utility to find a multiple-field set of values for dedup purposes 
	 * @param in - stream of JSON objects
	 * @param key_fields
	 * @return
	 */
	protected static List<Tuple2<JsonNode, Tuple2<Long, IBatchRecord>>> extractKeyFields(final Stream<Tuple2<Long, IBatchRecord>> in, final List<String> key_fields) {
		return in
				.map(x -> extractKeyFields(x._2().getJson(), key_fields).map(y -> Tuples._2T(y, x)).orElse(null))
				.filter(x -> null != x)
				.collect(Collectors.toList())
				;
	}
	
	/** Utility to find a multiple-field set of values for dedup purposes 
	 * @param in - single JSON object
	 * @param key_fields
	 * @return
	 */
	protected static Optional<JsonNode> extractKeyFields(final JsonNode in, final List<String> key_fields) {
		final ObjectNode on = key_fields.stream()
				.reduce(_mapper.createObjectNode(),
						(acc, v) -> JsonUtils.getProperty(v, in).<ObjectNode>map(val -> (ObjectNode) acc.set(v, val)).orElse(acc),
						(acc1, acc2) -> acc1 //(not possible because not parallel())
						);

		return Optional.of((JsonNode) on).filter(o -> 0 != o.size());
	}
	
	/* (non-Javadoc)
	 * @see com.ikanow.aleph2.data_model.interfaces.data_import.IEnrichmentBatchModule#onStageComplete(boolean)
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void onStageComplete(final boolean is_original) {
		_custom_handler.optional().ifPresent(handler -> handler.onStageComplete(true));
		
		final Supplier<String> subsystem_builder = () -> (_is_system_dedup_stage.get() ? "" : ("." + _control.get().name() + Optional.ofNullable("no_name")));
		final Supplier<String> command_builder = () -> (_is_system_dedup_stage.get() ? "system" : _control.get().name() + Optional.ofNullable("no_name"));
		
		_logger.optional().ifPresent(l -> l.log(Level.DEBUG,
				ErrorUtils.lazyBuildMessage(true, 
						() -> "DeduplicationService" + subsystem_builder.get(),
						() -> command_builder.get() + ".onStageComplete", 
						() -> null, 
						() -> ErrorUtils.get("Job {0} completed deduplication: nondup_keys={1}, dup_keys={2}, dups_inc={3}, dups_db={4}, del={5}",
								command_builder.get(),
								Integer.toString(_mutable_stats.nonduplicate_keys), Integer.toString(_mutable_stats.duplicate_keys), Integer.toString(_mutable_stats.duplicates_incoming), Integer.toString(_mutable_stats.duplicates_existing),
								Integer.toString(_mutable_stats.deleted)), 
						() -> (Map<String, Object>)_mapper.convertValue(_mutable_stats, Map.class))
						));
		
		if (!mutable_uncompleted_deletes.isEmpty()) {
			try {
				CompletableFuture.allOf(mutable_uncompleted_deletes.stream().toArray(CompletableFuture[]::new)).get(60, TimeUnit.SECONDS);
			}
			catch (Exception e) {
				_logger.optional().ifPresent(l -> l.log(Level.ERROR, 
						ErrorUtils.lazyBuildMessage(false, 
								() -> "DeduplicationService" + subsystem_builder.get(),
								() -> command_builder.get() + ".onStageComplete", 
								() -> null, 
								() -> ErrorUtils.get("Job {0}: error completing deleted ids: {1}", command_builder.get(), e.getMessage()), 
								() -> null)
								));
			}
		}
		_logger.optional().ifPresent(Lambdas.wrap_consumer_u(l -> l.flush().get(60, TimeUnit.SECONDS)));
	}

	/** Utility function to allow the user to override data service to use in the lookup
	 * @param doc_schema
	 * @return
	 */
	protected static Validation<String, Tuple2<Optional<Class<? extends IUnderlyingService>>, Optional<String>>> getDataService(final DocumentSchemaBean doc_schema) {
		final Tuple2<Optional<Class<? extends IUnderlyingService>>, Optional<String>> service_to_use =
				Optional.ofNullable(doc_schema.lookup_service_override())
						.filter(s -> !s.isEmpty())
						.map(s -> s.split("[.:]", 2))
						.map(s2 -> Tuples._2T(DataServiceUtils.getUnderlyingServiceInterface(s2[0]), (s2.length > 1) ? Optional.ofNullable(s2[1]) : Optional.<String>empty()))
						.orElseGet(() -> Tuples._2T(Optional.of(IDocumentService.class), Optional.ofNullable(doc_schema.service_name())))
						;
		
		if (!service_to_use._1().isPresent()) {
			return Validation.fail(ErrorUtils.get(ErrorUtils.INVALID_LOOKUP_SERVICE, doc_schema.lookup_service_override()));
		}		
		else return Validation.success(service_to_use);
	}
	
	/** Utility to get the list (currently 0/1) of enrichment modules that define deduplication handling
	 * @param doc_schema
	 * @return
	 */
	final protected static Collection<IEnrichmentBatchModule> getEnrichmentModules(final IEnrichmentModuleContext context, final EnrichmentControlMetadataBean cfg) {
		
		final Optional<String> entry_point = Optional.ofNullable(cfg.entry_point())
				.map(Optional::of)
				.orElseGet(() -> {
					// Get the shared library bean:
					
					return BucketUtils.getBatchEntryPoint(							
						context.getServiceContext().getCoreManagementDbService().readOnlyVersion().getSharedLibraryStore()
							.getObjectBySpec(CrudUtils.anyOf(SharedLibraryBean.class)
										.when(SharedLibraryBean::_id, cfg.module_name_or_id())
										.when(SharedLibraryBean::path_name, cfg.module_name_or_id())
									)
							.join()
							.map(bean -> (Map<String, SharedLibraryBean>)ImmutableMap.of(cfg.module_name_or_id(), bean))
							.orElse(Collections.<String, SharedLibraryBean>emptyMap())
							,
							cfg);
				});
		
		return entry_point.map(Lambdas.wrap_u(ep -> 
				(IEnrichmentBatchModule) Class.forName(ep, true, Thread.currentThread().getContextClassLoader()).newInstance()))
			.map(i -> Arrays.asList(i))
			.orElse(Collections.emptyList())
			;
	}
	
}
