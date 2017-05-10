/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.blaez.ziosgram.exoplayer2.source.hls;

import android.util.SparseArray;
import org.blaez.ziosgram.exoplayer2.extractor.TimestampAdjuster;

/**
 * Provides {@link TimestampAdjuster} instances for use during HLS playbacks.
 */
public final class TimestampAdjusterProvider {

  // TODO: Prevent this array from growing indefinitely large by removing adjusters that are no
  // longer required.
  private final SparseArray<TimestampAdjuster> timestampAdjusters;

  public TimestampAdjusterProvider() {
    timestampAdjusters = new SparseArray<>();
  }

  /**
   * Returns a {@link TimestampAdjuster} suitable for adjusting the pts timestamps contained in
   * a chunk with a given discontinuity sequence.
   *
   * @param discontinuitySequence The chunk's discontinuity sequence.
   * @param startTimeUs The chunk's start time.
   * @return A {@link TimestampAdjuster}.
   */
  public TimestampAdjuster getAdjuster(int discontinuitySequence, long startTimeUs) {
    TimestampAdjuster adjuster = timestampAdjusters.get(discontinuitySequence);
    if (adjuster == null) {
      adjuster = new TimestampAdjuster(startTimeUs);
      timestampAdjusters.put(discontinuitySequence, adjuster);
    }
    return adjuster;
  }

  /**
   * Resets the provider.
   */
  public void reset() {
    timestampAdjusters.clear();
  }

}