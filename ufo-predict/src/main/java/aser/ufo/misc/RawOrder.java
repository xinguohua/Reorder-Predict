package aser.ufo.misc;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import trace.DeallocNode;
import trace.MemAccNode;

public class RawOrder {
  public final RacePair racePair;
  public final LongArrayList schedule;

  public RawOrder(RacePair racePair, LongArrayList schedule) {
    this.racePair = racePair;
    this.schedule = schedule;
  }
}
