package spin.core.type;

import spin.core.util.ObjectChecker;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.util.Arrays;

/**
 * A circular byte buffer that allows for both reading and writing bytes from and to the buffer.
 */
public final class CircularByteBuffer {
    // Note that writeHead == readHead could mean either the buffer is empty or full. We need an unambiguous
    // interpretation and opt for the former since this is consistent with the buffer's initial state. For this reason,
    // the writeHead can never overtake the readHead or else that's overflow. Whereas the readHead can overtake the
    // writeHead iff it does not explicitly pass it.
    private final byte[] buffer;
    private int writeHead = 0;
    private int readHead = 0;

    private CircularByteBuffer(int capacity) {
        this.buffer = new byte[capacity];
    }

    public static CircularByteBuffer withCapacity(int capacity) {
        // We require 1 extra byte because the write head is never allowed to pass or touch the read head and vice versa.
        // This allows us to write the full capacity amount and end up in an unambiguous end state.
        return new CircularByteBuffer(capacity + 1);
    }

    /**
     * Returns the number of bytes that can be written to this buffer.
     *
     * @return the amount of space left for bytes to be written to the buffer.
     */
    public int availableSpace() {
        if (this.writeHead < this.readHead) {
            return this.readHead - this.writeHead - 1;
        } else if (this.writeHead > this.readHead) {
            return this.buffer.length - this.writeHead + this.readHead - 1;
        } else {
            return this.buffer.length - 1;
        }
    }

    /**
     * Returns {@code true} if and only if the buffer has no bytes to be read from it.
     *
     * @return whether or not the buffer is empty and has nothing to read.
     */
    public boolean isEmpty() {
        return this.readHead == this.writeHead;
    }

    /**
     * Writes all of the specified bytes into the buffer.
     *
     * @param bytes The bytes to write.
     * @throws BufferOverflowException If the buffer has insufficient capacity to hold the new bytes.
     */
    public void writeBytes(byte[] bytes) {
        ObjectChecker.assertNonNull(bytes);
        if (this.writeHead < this.readHead) {
            sequentialWriteUpToReadHead(bytes);
        } else {
            wrappingWriteUpToReadHead(bytes);
        }
    }

    /**
     * Attempts to read the bytes of the buffer up to and including the specified terminatingByte if and only if the
     * terminatingByte is encountered.
     *
     * If none of the available bytes to read in the buffer contain the terminatingByte then this method does not
     * perform a read at all and returns {@code null}.
     *
     * Otherwise, this method will read up to the first occurrence of the terminating byte and return all of the bytes
     * read up to and including it.
     *
     * @param terminatingByte The terminating byte at which to stop the read.
     * @return the terminated bytes or null if no terminator is present.
     */
    public byte[] readBytesUpToIfPresent(byte terminatingByte) {
        return (this.readHead <= this.writeHead)
                ? sequentialReadUpToWriteHead(terminatingByte)
                : wrappingReadUpToWriteHead(terminatingByte);
    }

    /**
     * Rolls back the readHead of this buffer by the specified number of bytes.
     *
     * WARNING: The caller must take care to use this method appropriately. The number of bytes being rolled back must
     * have been read otherwise this method will rollback the readHead further than it should but has no way of knowing.
     * This will cause the subsequent read to read whatever junk data is still in the buffer at those earlier positions.
     *
     * @param numBytes The number of bytes to rollback by.
     */
    public void rollbackReadBy(int numBytes) {
        if (numBytes < 0) {
            throw new IllegalArgumentException("numBytes must be non-negative.");
        }
        if (numBytes > this.buffer.length - 1) {
            throw new BufferUnderflowException();
        }

        // Since we are rolling back a read the previous state of the buffer must have been non-empty. This means that
        // we cannot overtake the writeHead when we rollback or we violate the readHead==writeHead=>empty invariant.

        if (this.writeHead == this.readHead) {
            if (numBytes <= this.readHead) {
                this.readHead -= numBytes;
            } else {
                this.readHead = this.buffer.length - numBytes + this.readHead;
            }
        } else if (this.writeHead < this.readHead) {
            if (this.readHead - this.writeHead - 1 < numBytes) {
                throw new BufferUnderflowException();
            }
            this.readHead -= numBytes;
        } else {
            if (numBytes <= this.readHead) {
                this.readHead -= numBytes;
            } else {
                if (this.buffer.length - numBytes + this.readHead <= this.writeHead) {
                    throw new BufferUnderflowException();
                }
                this.readHead = this.buffer.length - numBytes + this.readHead;
            }
        }
    }

    private void sequentialWriteUpToReadHead(byte[] bytes) {
        // Minus one to prevent the writeHead from ever overtaking the readHead.
        if (this.readHead - this.writeHead - 1 < bytes.length) {
            throw new BufferOverflowException();
        }
        System.arraycopy(bytes, 0, this.buffer, this.writeHead, bytes.length);
        this.writeHead += bytes.length;
    }

    private void wrappingWriteUpToReadHead(byte[] bytes) {
        // Minus one to prevent the writeHead from ever overtaking the readHead.
        if (this.buffer.length - this.writeHead + this.readHead - 1 < bytes.length) {
            throw new BufferOverflowException();
        }
        if (this.buffer.length - this.writeHead >= bytes.length) {
            System.arraycopy(bytes, 0, this.buffer, this.writeHead, bytes.length);
            this.writeHead += bytes.length;
        } else {
            System.arraycopy(bytes, 0, this.buffer, this.writeHead, this.buffer.length - this.writeHead);
            System.arraycopy(bytes, this.buffer.length - this.writeHead, this.buffer, 0, bytes.length - this.buffer.length + this.writeHead);
            this.writeHead = bytes.length - this.buffer.length + this.writeHead;
        }
    }

    private byte[] sequentialReadUpToWriteHead(byte terminatingByte) {
        int terminatingIndex = -1;

        for (int i = this.readHead; i < this.writeHead; i++) {
            if (this.buffer[i] == terminatingByte) {
                terminatingIndex = i;
                break;
            }
        }

        if (terminatingIndex == -1) {
            return null;
        } else {
            byte[] read = Arrays.copyOfRange(this.buffer, this.readHead, terminatingIndex + 1);
            this.readHead += read.length;
            return read;
        }
    }

    private byte[] wrappingReadUpToWriteHead(byte terminatingByte) {
        int terminatingIndex = -1;

        for (int i = this.readHead; i < this.buffer.length; i++) {
            if (this.buffer[i] == terminatingByte) {
                terminatingIndex = i;
                break;
            }
        }

        // If we found our terminating byte then all we needed was a sequential read, we are done.
        if (terminatingIndex != -1) {
            byte[] read = Arrays.copyOfRange(this.buffer, this.readHead, terminatingIndex + 1);
            this.readHead += read.length;
            return read;
        }

        // Otherwise, we need to wrap around and continue our search.
        for (int i = 0; i < this.writeHead; i++) {
            if (this.buffer[i] == terminatingByte) {
                terminatingIndex = i;
                break;
            }
        }

        if (terminatingIndex == -1) {
            return null;
        } else {
            byte[] read = new byte[this.buffer.length - this.readHead + terminatingIndex + 1];
            System.arraycopy(this.buffer, this.readHead, read, 0, this.buffer.length - this.readHead);
            System.arraycopy(this.buffer, 0, read, this.buffer.length - this.readHead, read.length - this.buffer.length + this.readHead);
            this.readHead = terminatingIndex + 1;
            return read;
        }
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + " { capacity: " + this.buffer.length + ", readHead: " + this.readHead + ", writeHead: " + this.writeHead + " }";
    }
}
