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
package com.ikanow.aleph2.management_db.data_model;

import java.util.List;

import org.checkerframework.checker.nullness.qual.NonNull;

import com.ikanow.aleph2.data_model.objects.shared.BasicMessageBean;

/** Just a top level message type for handling bucket actions 
 * @author acp
 */
public class BucketActionReplyMessage {

	private BucketActionReplyMessage() {}
	
	/** System message indicating that not all messages have been replied to in time
	 * @author acp
	 */
	public static class BucketActionTimeoutMessage extends BucketActionReplyMessage {}
	
	/** The message a BucketAction*Actor sends out when it is complete
	 * @author acp
	 */
	public static class BucketActionCollectedRepliesMessage extends BucketActionReplyMessage {
		public BucketActionCollectedRepliesMessage(final @NonNull List<BasicMessageBean> replies, final int timed_out) {
			this.replies = replies;
			this.timed_out = timed_out;			
		}		
		public List<BasicMessageBean> replies() { return replies; }
		public Integer timed_out() { return timed_out; }
		private final List<BasicMessageBean> replies;
		private final Integer timed_out;
	}
	
	/** When a data import manager will accept a bucket action
	 * @author acp
	 */
	public static class BucketActionWillAcceptMessage extends BucketActionReplyMessage {
		
		public BucketActionWillAcceptMessage(final @NonNull String uuid) {
			this.uuid = uuid;
		}
		public String uuid() { return uuid; }
		private final String uuid;
	}
	
	/** When a data import manager cannot or does not wish to handle a bucket action message
	 * @author acp
	 */
	public static class BucketActionIgnoredMessage extends BucketActionReplyMessage {
		
		public BucketActionIgnoredMessage(final @NonNull String uuid) {
			this.uuid = uuid;
		}
		public String uuid() { return uuid; }
		private final String uuid;
	}
	
	/** Encapsulates the reply from any requested bucket action
	 * @author acp
	 */
	public static class BucketActionHandlerMessage extends BucketActionReplyMessage {
		
		public BucketActionHandlerMessage(final @NonNull String uuid, final @NonNull BasicMessageBean reply) {
			this.uuid = uuid;
			this.reply = reply;
		}
		public String uuid() { return uuid; }
		public BasicMessageBean reply() { return reply; }
		
		private final String uuid;
		private final BasicMessageBean reply;
	}
}