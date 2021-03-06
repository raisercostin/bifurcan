package io.lacuna.bifurcan.durable.allocator;

import io.lacuna.bifurcan.IntMap;

/**
 * @author ztellman
 */
public interface IAllocator {

  class Range {
    public final long start, end;

    public Range(long start, long end) {
      this.start = start;
      this.end = end;
    }

    public long size() {
      return end - start;
    }

    @Override
    public int hashCode() {
      long h = start ^ end;
      return (int) (h ^ (h >>> 32));
    }

    @Override
    public boolean equals(Object obj) {
      if (obj instanceof Range) {
        Range r = (Range) obj;
        return start == r.start && end == r.end;
      }
      return false;
    }

    @Override
    public String toString() {
      return "[" + start + ", " + end + ")";
    }
  }

  Range acquire(long quantity);

  void release(Range range);

  IntMap<Range> acquired();

  Iterable<Range> available();

}
