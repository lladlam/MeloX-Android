package com.lladlam.melox.core.network

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BoundedStreamsTest {
    @Test
    fun readsPayloadAtTheLimit() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4))

        assertArrayEquals(byteArrayOf(1, 2, 3, 4), input.readBoundedBytes(4))
    }

    @Test
    fun rejectsPayloadAfterDecompressionLimit() {
        val input = ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5))

        assertThrows(BoundedStreamExceededException::class.java) {
            input.readBoundedBytes(4)
        }
    }
}
