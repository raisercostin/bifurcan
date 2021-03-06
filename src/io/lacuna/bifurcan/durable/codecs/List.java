package io.lacuna.bifurcan.durable.codecs;

import io.lacuna.bifurcan.*;
import io.lacuna.bifurcan.durable.BlockPrefix;
import io.lacuna.bifurcan.durable.BlockPrefix.BlockType;
import io.lacuna.bifurcan.durable.Util;
import io.lacuna.bifurcan.durable.io.DurableBuffer;

import java.util.Iterator;

import static io.lacuna.bifurcan.durable.codecs.Core.encodeBlock;

/**
 * An indexed list, encoded as:
 * - the number of elements [VLQ]
 * - number of SkipTable tiers [uint8]
 * - a SkipTable over the blocks of elements (unless number of tiers is 0)
 * - zero or more ENCODED blocks generated by {@link IDurableEncoding.List#elementEncoding()}
 */
public class List {

  public static <V> void encode(Iterator<V> it, IDurableEncoding.List listEncoding, DurableOutput out) {
    SkipTable.Writer skipTable = new SkipTable.Writer();
    DurableBuffer elements = new DurableBuffer();

    IDurableEncoding elementEncoding = listEncoding.elementEncoding();
    Iterator<IList<V>> blocks = Util.partitionBy(
        it,
        DurableEncodings.blockSize(elementEncoding),
        elementEncoding::isSingleton
    );

    long index = 0;
    while (blocks.hasNext()) {
      IList<V> b = blocks.next();
      skipTable.append(index, elements.written());
      encodeBlock((IList<Object>) b, elementEncoding, elements);
      index += b.size();
    }

    long size = index;
    DurableBuffer.flushTo(out, BlockType.LIST, acc -> {
      acc.writeUVLQ(size);
      skipTable.flushTo(acc);
      elements.flushTo(acc);
    });
  }

  private static final ISortedMap<Long, Long> DEFAULT_TABLE = new SortedMap<Long, Long>().put(0L, 0L);

  public static DurableList decode(
      IDurableEncoding.List encoding,
      IDurableCollection.Root root,
      DurableInput.Pool pool
  ) {
    DurableInput in = pool.instance();

    BlockPrefix prefix = in.readPrefix();
    assert (prefix.type == BlockType.LIST);
    long pos = in.position();

    long size = in.readUVLQ();

    ISortedMap<Long, Long> indexTable = SkipTable.decode(root, in);
    if (indexTable.size() == 0) {
      indexTable = DEFAULT_TABLE;
    }

    DurableInput.Pool elements = in.sliceBytes((pos + prefix.length) - in.position()).pool();

    return new DurableList(pool, root, size, indexTable, elements, encoding);
  }
}
