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

package io.netty.codec.quic;

import org.junit.Test;

import static org.junit.Assert.*;

public class QUICPacketNumberTest {

    @Test
    public void encodedLengthWith1ByteValueIs0() {
        assertEquals(0, new QUICPacketNumber(1).encodedLength);
        assertEquals(0, new QUICPacketNumber(1 << 7).encodedLength);
    }

    @Test
    public void encodedLengthWith2ByteValueIs1() {
        assertEquals(1, new QUICPacketNumber(1 << 8).encodedLength);
        assertEquals(1, new QUICPacketNumber(1 << 15).encodedLength);
    }

    @Test
    public void encodedLengthWith3ByteValueIs2() {
        assertEquals(2, new QUICPacketNumber(1 << 16).encodedLength);
        assertEquals(2, new QUICPacketNumber(1 << 23).encodedLength);
    }

    @Test
    public void encodedLengthWith4ByteValueIs3() {
        assertEquals(3, new QUICPacketNumber(1 << 24).encodedLength);
    }
}