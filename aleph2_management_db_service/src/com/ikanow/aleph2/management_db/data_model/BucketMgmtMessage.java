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
package com.ikanow.aleph2.management_db.data_model;

import java.io.Serializable;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

import akka.actor.ActorRef;

import com.ikanow.aleph2.data_model.objects.data_import.DataBucketBean;
import com.ikanow.aleph2.distributed_services.data_model.IRoundRobinEventBusWrapper;

/** Set of ADTs for passing about management information related to buckets
 *  (ADT-ness may not matter in this case, not clear there's ever going to be a bus that handles many different types)
 * @author Alex
 */
public class BucketMgmtMessage implements Serializable {
	private static final long serialVersionUID = -6388837686470436299L;
	protected BucketMgmtMessage() {} // (for bean template utils)
	private BucketMgmtMessage(final DataBucketBean bucket) { this.bucket = bucket;  }
	public DataBucketBean bucket() { return bucket; };
	private DataBucketBean bucket;

	/** An internal class used to wrap event bus publications
	 * @author acp
	 */
	public static class BucketMgmtEventBusWrapper implements IRoundRobinEventBusWrapper<BucketMgmtMessage>,Serializable {
		private static final long serialVersionUID = -7333589171293704873L;
		protected BucketMgmtEventBusWrapper() { }
		/** User c'tor for wrapping a BucketActionMessage to be sent over the bus
		 * @param sender - the sender of the message
		 * @param message - the message to be wrapped
		 */
		public BucketMgmtEventBusWrapper(final ActorRef sender, final BucketMgmtMessage message) {
			this.sender = sender;
			this.message = message;
		}	
		@Override
		public ActorRef sender() { return sender; };
		@Override
		public BucketMgmtMessage message() { return message; };
		
		protected ActorRef sender;
		protected BucketMgmtMessage message;
	}		
	
	/** When a bucket is deleted by the user, this message is queued for a separate thread to delete the actual data and clean the bucket up (which can take some considerable time)
	 * @author Alex
	 */
	public static class BucketDeletionMessage extends BucketMgmtMessage implements Serializable {
		private static final long serialVersionUID = 8418826676589517525L;
		/** (Jackson c'tor)
		 */
		protected BucketDeletionMessage() { super(null); } 
		
		/** User constructor
		 * @param bucket - bucket to delete
		 * @param delete_on - when to delete it
		 */
		public BucketDeletionMessage(final DataBucketBean bucket, final Date delete_on, final boolean data_only) {
			super(bucket);
			this.delete_on = delete_on;
			//(_id generated by the underlying data store)
			deletion_attempts = 0;
			_id = bucket.full_name();
			this.data_only = data_only;
		}

		/** The _id of the object in the underlying data store (so it can be deleted)
		 *  Actually uses the bucket.full_path, so it's safe to use that in updateById and deleteById, and also (more importantly) safe to call storeObject to update
		 * @return
		 */
		public Object _id() { return _id; }
		/** The date to delete the object
		 * @return
		 */
		public Date delete_on() { return delete_on; }
		/** The number of (likely failed) attempts to delete the data
		 * @return
		 */
		public Integer deletion_attempts() { return deletion_attempts; }
		
		/** Whether the bucket deletion is actually just a timed purge
		 * @return
		 */
		public boolean data_only() { return Optional.ofNullable(data_only).orElse(false); } 
		
		private Object _id; // (read-only used for deletion)
		private Date delete_on;
		private Integer deletion_attempts;
		private Boolean data_only;
	}
	
	public static class BucketTimeoutMessage extends BucketMgmtMessage implements Serializable {
		private static final long serialVersionUID = -1141752282442676055L;
		private Object _id; // (read-only used for deletion)
		private DataBucketBean bucket;
		private Date timeout_on;
		private Set<String> handling_clients;
		
		protected BucketTimeoutMessage() { super(null); }
		
		public BucketTimeoutMessage(final DataBucketBean bucket, final Date timeout_on, final Set<String> handling_clients) {
			super(bucket);
			this.bucket = bucket;
			_id = bucket.full_name();
			this.timeout_on = timeout_on;
			this.handling_clients = handling_clients;
		}
		
		public Object _id() { return _id; }
		public DataBucketBean bucket() { return bucket; }
		public Date timeout_on() { return timeout_on; }
		public Set<String> handling_clients() { return handling_clients; }
	}
}
