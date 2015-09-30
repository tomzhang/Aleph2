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

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.ikanow.aleph2.data_model.objects.data_analytics.AnalyticThreadJobBean;
import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.data_model.objects.data_import.EnrichmentControlMetadataBean;
import com.ikanow.aleph2.data_model.objects.data_import.HarvestControlMetadataBean;
import com.ikanow.aleph2.data_model.objects.shared.SharedLibraryBean;

public class TestBucketUtils {

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void test_ConvertDataBucketBeanToTest() {
		String original_full_name = "/my_bean/sample_path";
		String original_id = "id12345";
		String user_id = "user12345";
		DataBucketBean original_bean = BeanTemplateUtils.build(DataBucketBean.class)
				.with(DataBucketBean::_id, "id12345")
				.with(DataBucketBean::full_name, original_full_name)
				.done().get();
		
		DataBucketBean test_bean = BucketUtils.convertDataBucketBeanToTest(original_bean, user_id);
		
		assertTrue(test_bean._id().equals(original_id));
		assertTrue("Name is wrong: " + test_bean.full_name(), test_bean.full_name().equals("/aleph2_testing/" + user_id + original_full_name));
		
		assertTrue(BucketUtils.isTestBucket(test_bean));
		assertFalse(BucketUtils.isTestBucket(original_bean));		
	}

	@Test
	public void test_getUniqueBucketSignature() {
		
		final String path1 = "/test+extra/";
		final String path2 = "/test+extra/4354____42";
		final String path3 = "test+extra/4354____42/some/more/COMPONENTS_VERY_VERY_LONG";
		
		assertEquals("test_extra__c1651d4c69ed", BucketUtils.getUniqueSignature(path1, Optional.empty()));
		assertEquals("test_extra_test_12345__c1651d4c69ed", BucketUtils.getUniqueSignature(path1, Optional.of("test+;12345")));
		assertEquals("test_extra_4354_42__bb8a6a382d7b", BucketUtils.getUniqueSignature(path2, Optional.empty()));
		assertEquals("test_extra_4354_42_t__bb8a6a382d7b", BucketUtils.getUniqueSignature(path2, Optional.of("t")));
		assertEquals("test_extra_more_components_very__7768508661fc", BucketUtils.getUniqueSignature(path3, Optional.empty()));
		assertEquals("test_extra_more_components_very_xx__7768508661fc", BucketUtils.getUniqueSignature(path3, Optional.of("XX__________")));
	}

	@Test
	public void test_getEntryPoints() {
		
		final SharedLibraryBean lib1 = BeanTemplateUtils.build(SharedLibraryBean.class)
											.with(SharedLibraryBean::_id, "id1")											
											.with(SharedLibraryBean::path_name, "path1")
											.with(SharedLibraryBean::streaming_enrichment_entry_point, "stream_test1")
											.with(SharedLibraryBean::misc_entry_point, "misc_test1")
										.done().get();
		
		final SharedLibraryBean lib2 = BeanTemplateUtils.build(SharedLibraryBean.class)
				.with(SharedLibraryBean::_id, "id2")
				.with(SharedLibraryBean::path_name, "path2")
				.with(SharedLibraryBean::batch_enrichment_entry_point, "batch_test2")
				.with(SharedLibraryBean::misc_entry_point, "misc_test2")
			.done().get();

		final SharedLibraryBean lib3 = BeanTemplateUtils.build(SharedLibraryBean.class)
				.with(SharedLibraryBean::_id, "id3")
				.with(SharedLibraryBean::path_name, "path3")
			.done().get();		
		
		// Different cases:
		
		final Map<String, SharedLibraryBean> test_map = ImmutableMap.<String, SharedLibraryBean>builder()
																.put("id1", lib1)
																.put("path1", lib1)
																.put("id2", lib2)
																.put("path2", lib2)
																.put("id3", lib3)
																.put("path3", lib3)
															.build();
		
		final EnrichmentControlMetadataBean entry_point_override = BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
																		.with(EnrichmentControlMetadataBean::entry_point, "override_test")
																	.done().get();
		
		final EnrichmentControlMetadataBean batch_case = BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
																.with(EnrichmentControlMetadataBean::module_name_or_id, "id2")
															.done().get();
		
		final EnrichmentControlMetadataBean streaming_case = BeanTemplateUtils.build(EnrichmentControlMetadataBean.class)
				.with(EnrichmentControlMetadataBean::module_name_or_id, "id1")
			.done().get();
		
		final AnalyticThreadJobBean misc_case = BeanTemplateUtils.build(AnalyticThreadJobBean.class)
				.with(AnalyticThreadJobBean::module_name_or_id, "id1")
			.done().get();
		
		final HarvestControlMetadataBean libs_case = BeanTemplateUtils.build(HarvestControlMetadataBean.class)
															.with(HarvestControlMetadataBean::library_names_or_ids, Arrays.asList("not_there", "id1", "path2"))
														.done().get();

		final HarvestControlMetadataBean libs_case2 = BeanTemplateUtils.build(HarvestControlMetadataBean.class)
				.with(HarvestControlMetadataBean::library_names_or_ids, Arrays.asList("id3"))
			.done().get();		

		final AnalyticThreadJobBean libs_case3 = BeanTemplateUtils.build(AnalyticThreadJobBean.class)
				.with(AnalyticThreadJobBean::library_names_or_ids, Arrays.asList("not_there", "id1", "path2"))
			.done().get();		
		
		assertEquals("override_test", BucketUtils.getBatchEntryPoint(test_map, entry_point_override).get());
		assertEquals("batch_test2", BucketUtils.getBatchEntryPoint(test_map, batch_case).get());
		assertEquals("misc_test1", BucketUtils.getBatchEntryPoint(test_map, streaming_case).get());
		assertEquals("misc_test2", BucketUtils.getStreamingEntryPoint(test_map, batch_case).get());
		assertEquals("stream_test1", BucketUtils.getStreamingEntryPoint(test_map, streaming_case).get());
		assertEquals("misc_test1", BucketUtils.getBatchEntryPoint(test_map, misc_case).get());
		assertEquals("stream_test1", BucketUtils.getStreamingEntryPoint(test_map, misc_case).get());
		assertEquals("misc_test1", BucketUtils.getEntryPoint(test_map, libs_case).get());
		assertFalse("Entry point not found", BucketUtils.getEntryPoint(test_map, libs_case2).isPresent());
		assertEquals("batch_test2", BucketUtils.getBatchEntryPoint(test_map, libs_case3).get());
	}
	
}
