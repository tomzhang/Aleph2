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
package com.ikanow.aleph2.data_model.objects.shared;

import java.io.Serializable;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.Set;


public class SharedLibraryBean implements Serializable {
	private static final long serialVersionUID = 7679107180417790229L;
	protected SharedLibraryBean() {}
	
	/** User constructor
	 */
	public SharedLibraryBean(final String _id, final String display_name, final String path_name,
			final LibraryType type, final String subtype, final String owner_id,
			final Set<String> tags, 
			final String batch_streaming_entry_point, final String batch_enrichment_entry_point, final String misc_entry_point, 
			final Map<String, Object> library_config) {
		super();
		this._id = _id;
		this.display_name = display_name;
		this.path_name = path_name;
		this.type = type;
		this.subtype = subtype;
		this.owner_id = owner_id;
		this.tags = tags;
		this.streaming_enrichment_entry_point = batch_streaming_entry_point;
		this.batch_enrichment_entry_point = batch_enrichment_entry_point;
		this.misc_entry_point = misc_entry_point;
		this.library_config = library_config;
	}
	/** The management DB id of the shared library (unchangeable, unlike the name)
	 * @return the library _id
	 */
	public String _id() {
		return _id;
	}
	/** When this bucket was first created
	 * @return created date
	 */
	public Date created() {
		return created;
	}
	/** When this bucket was last modified
	 * @return modified date
	 */
	public Date modified() {
		return modified;
	}
	/** The display name - used for display and search only. If not set will correspond to the filename in the main path
	 * @return the display name
	 */
	public String display_name() {
		return display_name;
	}
	/** The full path - must be unique within the cluster
	 * @return the full path of the file (in the storage service eg HDFS)
	 */
	public String path_name() {
		return path_name;
	}
	/** A description purely for informational/discovery purposes
	 * @return the description text
	 */
	public String description() {
		return description;
	}
	/** The type of the library (harvest/enrichment/analytics/access/misc and technology/module/etc-for-misc)
	 * @return the type
	 */
	public LibraryType type() {
		return type;
	}
	/** An optional sub-type provided for searching, can be any string - for display/search only
	 * @return the subtype
	 */
	public String subtype() {
		return subtype;
	}
	/** The owner id, a meaningful string to the security service
	 * @return the owner id
	 */
	public String owner_id() {
		return owner_id;
	}
	/** An optional set of tags for display/search purposes
	 * @return
	 */
	public Set<String> tags() {
		return tags == null ? null : Collections.unmodifiableSet(tags);
	}
	
	/** For JARs, the default entry point (type specific - eg will point to the implementation of IAccessTechnology, or IEnrichmentBatchModule, etc)
	 * @return the fully qualified classpath of the primary/default entry point
	 */
	public String batch_enrichment_entry_point() {
		return batch_enrichment_entry_point;
	}

	/** For JARs, the default entry point (type specific - eg will point to the implementation of IAccessTechnology, or IEnrichmentBatchModule, etc)
	 * @return the fully qualified classpath of the primary/default entry point
	 */
	public String streaming_enrichment_entry_point() {
		return streaming_enrichment_entry_point;
	}

	/** For JARs, the default entry point (type specific - eg will point to the implementation of IAccessTechnology, or IEnrichmentBatchModule, etc)
	 * @return the fully qualified classpath of the primary/default entry point
	 */
	public String misc_entry_point() {
		return misc_entry_point;
	}

	/** Generic JSON object associated with this library bean
	 * @return
	 */
	public Map<String, Object> library_config() {
		return null == library_config ? null : Collections.unmodifiableMap(library_config);
	}
	
	private String _id;
	private Date created;
	private Date modified;
	private String display_name;
	private String path_name;
	private String description;
	private LibraryType type;
	private String subtype;
	public enum LibraryType { 
			analytics_technology, analytics_module, 
				enrichment_module, enrichment_utility, 
					harvest_technology, harvest_module,
						access_module, access_utility,
							misc_archive, misc_directory, misc_file, misc_json };
						
	private String owner_id;
	private Set<String> tags;
	private String batch_enrichment_entry_point; // (for batch module only)
	private String streaming_enrichment_entry_point; // (for streaming module only)
	private String misc_entry_point; // (harvest, others)
	private Map<String, Object> library_config;
	
	@SuppressWarnings("unused")
	private Object access_rights; // (not currently used but still in the DB so leave in for now)	
}
