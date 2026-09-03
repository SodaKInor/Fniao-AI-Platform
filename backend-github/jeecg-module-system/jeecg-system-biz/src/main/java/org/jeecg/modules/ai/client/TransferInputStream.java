package org.jeecg.modules.ai.client;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Enforces a byte budget, including skip, without owning any business storage. */
public final class TransferInputStream extends FilterInputStream {
    private final long limit;
    private final Long expected;
    private long count;

    public TransferInputStream(InputStream input, long limit, Long expected) {
        super(input);
        if (limit <= 0 || (expected != null && (expected < 0 || expected > limit))) {
            throw new IllegalArgumentException("Invalid transfer limit");
        }
        this.limit = limit;
        this.expected = expected;
    }

    private int account(int n) throws IOException {
        if (n < 0) {
            if (expected != null && count != expected) throw new IOException("Incomplete transfer");
        } else {
            count += n;
            if (count > limit || (expected != null && count > expected)) {
                throw new IOException("Transfer exceeds limit");
            }
        }
        return n;
    }

    @Override public int read() throws IOException {
        int value = in.read();
        account(value < 0 ? -1 : 1);
        return value;
    }

    @Override public int read(byte[] bytes, int offset, int length) throws IOException {
        if (length == 0) return 0;
        int allowed = (int) Math.min(length, Math.min(Integer.MAX_VALUE, limit - count + 1));
        return account(in.read(bytes, offset, allowed));
    }

    @Override public long skip(long n) throws IOException {
        byte[] buffer = new byte[8192];
        long skipped = 0;
        while (skipped < n) {
            int read = read(buffer, 0, (int) Math.min(buffer.length, n - skipped));
            if (read < 0) break;
            skipped += read;
        }
        return skipped;
    }

    @Override public boolean markSupported() { return false; }
    @Override public synchronized void mark(int readLimit) { }
    @Override public synchronized void reset() throws IOException { throw new IOException("Not rewindable"); }
}
