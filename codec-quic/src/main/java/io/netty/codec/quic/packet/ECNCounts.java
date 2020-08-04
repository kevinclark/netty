/*
 * Copyright 2020 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.netty.codec.quic.packet;

import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

// TODO: Debatable whether this class needs to exist.
public class ECNCounts {
    /*
     ECT0 Count: A variable-length integer representing the total number
     of packets received with the ECT(0) codepoint in the packet number
     space of the ACK frame.
     */
    public final long ect0Count;

    /*
     ECT1 Count: A variable-length integer representing the total number
     of packets received with the ECT(1) codepoint in the packet number
     space of the ACK frame.
     */
    public final long ect1Count;

    /*
     CE Count: A variable-length integer representing the total number of
     packets received with the CE codepoint in the packet number space
     of the ACK frame.
     */
    public long ceCount;

    public ECNCounts(long ect0Count, long ect1Count, long ceCount) {
        this.ect0Count = ect0Count;
        this.ect1Count = ect1Count;
        this.ceCount = ceCount;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.ect0Count, this.ect1Count, this.ceCount);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        final ECNCounts other = (ECNCounts)obj;

        return this.ect0Count == other.ect0Count &&
               this.ect1Count == other.ect1Count &&
               this.ceCount == other.ceCount;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("ect0Count", this.ect0Count)
                .add("ect1Count", this.ect1Count)
                .add("ceCount", this.ceCount)
                .toString();
    }
}
