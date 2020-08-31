package spin.core;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import spin.core.helper.AssertHelper;
import spin.core.type.CircularByteBuffer;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

@RunWith(Parameterized.class)
public class CircularByteBufferTest {
    private static final Random RANDOM = new Random();
    private static final byte TERMINATOR = '\n';
    private final int capacity;
    private final CircularByteBuffer buffer;

    public CircularByteBufferTest(int capacity) {
        this.capacity = capacity;
        this.buffer = CircularByteBuffer.withCapacity(this.capacity);
    }

    @Parameters
    public static Collection<Integer> capacities() {
        return Arrays.asList(0, 1, 2, 3, 4, 5, 8, 16, 64, 512);
    }

    @Test
    public void testOverflowWrite() {
        AssertHelper.assertThrows(BufferOverflowException.class, () -> {
            // Tests overflowing when our write is completely sequential.
            this.buffer.writeBytes(new byte[this.capacity + 1]);
        });
        if (this.capacity > 1) {
            AssertHelper.assertThrows(BufferOverflowException.class, () -> {
                // We do a halfway write,read to get into the middle of the buffer so our overflowing write is a wrapping write.
                byte[] bytesToWrite = new byte[this.capacity / 2];
                bytesToWrite[bytesToWrite.length - 1] = TERMINATOR;
                this.buffer.writeBytes(bytesToWrite);
                this.buffer.readBytesUpToIfPresent(TERMINATOR);
                this.buffer.writeBytes(new byte[this.capacity + 1]);
            });
        }
    }

    @Test
    public void testUnderflowRollback() {
        if (this.capacity > 0) {
            AssertHelper.assertThrows(BufferUnderflowException.class, () -> {
                // Tests underflowing when our read is completely sequential.
                byte[] bytesToWrite = new byte[this.capacity];
                bytesToWrite[bytesToWrite.length - 1] = TERMINATOR;
                this.buffer.writeBytes(bytesToWrite);
                this.buffer.readBytesUpToIfPresent(TERMINATOR);
                this.buffer.rollbackReadBy(this.capacity + 1);
            });
        }
        if (this.capacity > 1) {
            AssertHelper.assertThrows(BufferUnderflowException.class, () -> {
                // We do a halfway write,read to get into the middle of the buffer so our overflowing write is a wrapping write.
                byte[] bytesToWrite = new byte[this.capacity / 2];
                bytesToWrite[bytesToWrite.length - 1] = TERMINATOR;
                this.buffer.writeBytes(bytesToWrite);
                this.buffer.readBytesUpToIfPresent(TERMINATOR);
                this.buffer.rollbackReadBy(this.capacity + 1);
            });
        }
    }

    @Test
    public void testWriteAndReadCapacityBytes() {
        byte[] bytesToWrite = this.capacity == 0 ? new byte[this.capacity] : randomTerminatingArray(this.capacity);

        // Verify the buffer is empty and the full capacity is available to be written to.
        Assert.assertEquals(this.capacity, this.buffer.availableSpace());
        Assert.assertTrue(this.buffer.isEmpty());

        // Write the bytes and verify that there is no space to write to and that the buffer is non-empty (unless the
        // capacity is zero of course, then it must always be empty and we wrote zero bytes to it above).
        this.buffer.writeBytes(bytesToWrite);
        Assert.assertEquals(0, this.buffer.availableSpace());
        if (this.capacity == 0) {
            Assert.assertTrue(this.buffer.isEmpty());
        } else {
            Assert.assertFalse(this.buffer.isEmpty());
        }

        // Read the bytes and verify it is empty again and the full capacity is available to be written to. If capacity
        // is zero we couldn't write the terminator byte b/c we wrote zero bytes so null is returned.
        byte[] bytesRead = this.buffer.readBytesUpToIfPresent(TERMINATOR);
        if (this.capacity == 0) {
            Assert.assertNull(bytesRead);
        } else {
            Assert.assertArrayEquals(bytesToWrite, bytesRead);
        }
        Assert.assertEquals(this.capacity, this.buffer.availableSpace());
        Assert.assertTrue(this.buffer.isEmpty());
    }

    @Test
    public void testWriteAndReadBytesMultipleTimes() {
        // This test really proves the point of the circularity. Here we continually write,read to the buffer CAP-1 bytes
        // and we do this CAP+1 times so we wrap around the buffer at a different offset each time until we're back where
        // we started.
        if (this.capacity > 1) {
            for (int i = 0; i <= this.capacity; i++) {
                byte[] bytesToWrite = randomTerminatingArray(this.capacity - 1);

                Assert.assertEquals(this.capacity, this.buffer.availableSpace());
                this.buffer.writeBytes(bytesToWrite);
                Assert.assertEquals(1, this.buffer.availableSpace());
                Assert.assertArrayEquals(bytesToWrite, this.buffer.readBytesUpToIfPresent(TERMINATOR));
            }
        }
    }

    @Test
    public void testWriteAndReadWhenWithPartialMessageWrites() {
        // We break the message up into 3 writes and so we expect to do write,read(fail),write,read(fail),write,read(ok).
        // Like above, we will do this multiple times to exploit & test the circular properties of the buffer.
        if (this.capacity > 3) {
            byte[] messageStart = new byte[]{'a'};
            byte[] messageMiddle = new byte[]{'z'};
            byte[] messageEnd = new byte[this.capacity - 3];
            messageEnd[messageEnd.length - 1] = TERMINATOR;

            byte[] completeMessage = new byte[this.capacity - 1];
            completeMessage[0] = messageStart[0];
            completeMessage[1] = messageMiddle[0];
            System.arraycopy(messageEnd, 0, completeMessage, 2, messageEnd.length);

            for (int i = 0; i <= this.capacity; i++) {
                this.buffer.writeBytes(messageStart);
                Assert.assertNull(this.buffer.readBytesUpToIfPresent(TERMINATOR));
                this.buffer.writeBytes(messageMiddle);
                Assert.assertNull(this.buffer.readBytesUpToIfPresent(TERMINATOR));
                this.buffer.writeBytes(messageEnd);
                Assert.assertArrayEquals(completeMessage, this.buffer.readBytesUpToIfPresent(TERMINATOR));
            }
        }
    }

    @Test
    public void testReadAndRollback() {
        if (this.capacity > 0) {
            byte[] bytesToWrite = randomTerminatingArray(this.capacity);

            Assert.assertTrue(this.buffer.isEmpty());
            this.buffer.writeBytes(bytesToWrite);
            Assert.assertFalse(this.buffer.isEmpty());
            Assert.assertArrayEquals(bytesToWrite, this.buffer.readBytesUpToIfPresent(TERMINATOR));
            Assert.assertTrue(this.buffer.isEmpty());
            this.buffer.rollbackReadBy(bytesToWrite.length);
            Assert.assertFalse(this.buffer.isEmpty());
            Assert.assertArrayEquals(bytesToWrite, this.buffer.readBytesUpToIfPresent(TERMINATOR));
        }
    }

    @Test
    public void testReadAndPartialRollback() {
        if (this.capacity > 1) {
            byte[] bytesToWrite = randomTerminatingArray(this.capacity);

            Assert.assertTrue(this.buffer.isEmpty());
            this.buffer.writeBytes(bytesToWrite);
            Assert.assertFalse(this.buffer.isEmpty());
            Assert.assertArrayEquals(bytesToWrite, this.buffer.readBytesUpToIfPresent(TERMINATOR));
            Assert.assertTrue(this.buffer.isEmpty());
            this.buffer.rollbackReadBy(this.capacity / 2);
            Assert.assertFalse(this.buffer.isEmpty());
            byte[] secondHalf = this.buffer.readBytesUpToIfPresent(TERMINATOR);
            Assert.assertTrue(this.buffer.isEmpty());

            byte[] reconstructedRead = Arrays.copyOf(bytesToWrite, bytesToWrite.length);
            System.arraycopy(secondHalf, 0, reconstructedRead, this.capacity - (this.capacity / 2), this.capacity / 2);
            Assert.assertArrayEquals(bytesToWrite, reconstructedRead);
        }
    }

    private static byte[] randomTerminatingArray(int len) {
        byte[] bytes = new byte[len];
        RANDOM.nextBytes(bytes);
        bytes[bytes.length - 1] = TERMINATOR;
        for (int i = 0; i < bytes.length - 1; i++) {
            if (bytes[i] == TERMINATOR) {
                bytes[i] = 'a';
            }
        }
        return bytes;
    }
}
