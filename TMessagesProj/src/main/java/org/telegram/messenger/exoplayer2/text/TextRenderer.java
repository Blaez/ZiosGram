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
package org.blaez.ziosgram.exoplayer2.text;

import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import org.blaez.ziosgram.exoplayer2.BaseRenderer;
import org.blaez.ziosgram.exoplayer2.C;
import org.blaez.ziosgram.exoplayer2.ExoPlaybackException;
import org.blaez.ziosgram.exoplayer2.Format;
import org.blaez.ziosgram.exoplayer2.FormatHolder;
import org.blaez.ziosgram.exoplayer2.util.Assertions;
import org.blaez.ziosgram.exoplayer2.util.MimeTypes;
import java.util.Collections;
import java.util.List;

/**
 * A renderer for text.
 * <p>
 * {@link Subtitle}s are decoded from sample data using {@link SubtitleDecoder} instances obtained
 * from a {@link SubtitleDecoderFactory}. The actual rendering of the subtitle {@link Cue}s is
 * delegated to an {@link Output}.
 */
public final class TextRenderer extends BaseRenderer implements Callback {

  /**
   * Receives output from a {@link TextRenderer}.
   */
  public interface Output {

    /**
     * Called each time there is a change in the {@link Cue}s.
     *
     * @param cues The {@link Cue}s.
     */
    void onCues(List<Cue> cues);

  }

  private static final int MSG_UPDATE_OUTPUT = 0;

  private final Handler outputHandler;
  private final Output output;
  private final SubtitleDecoderFactory decoderFactory;
  private final FormatHolder formatHolder;

  private boolean inputStreamEnded;
  private boolean outputStreamEnded;
  private SubtitleDecoder decoder;
  private SubtitleInputBuffer nextInputBuffer;
  private SubtitleOutputBuffer subtitle;
  private SubtitleOutputBuffer nextSubtitle;
  private int nextSubtitleEventIndex;

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be
   *     called. If the output makes use of standard Android UI components, then this should
   *     normally be the looper associated with the application's main thread, which can be obtained
   *     using {@link android.app.Activity#getMainLooper()}. Null may be passed if the output
   *     should be called directly on the player's internal rendering thread.
   */
  public TextRenderer(Output output, Looper outputLooper) {
    this(output, outputLooper, SubtitleDecoderFactory.DEFAULT);
  }

  /**
   * @param output The output.
   * @param outputLooper The looper associated with the thread on which the output should be
   *     called. If the output makes use of standard Android UI components, then this should
   *     normally be the looper associated with the application's main thread, which can be obtained
   *     using {@link android.app.Activity#getMainLooper()}. Null may be passed if the output
   *     should be called directly on the player's internal rendering thread.
   * @param decoderFactory A factory from which to obtain {@link SubtitleDecoder} instances.
   */
  public TextRenderer(Output output, Looper outputLooper, SubtitleDecoderFactory decoderFactory) {
    super(C.TRACK_TYPE_TEXT);
    this.output = Assertions.checkNotNull(output);
    this.outputHandler = outputLooper == null ? null : new Handler(outputLooper, this);
    this.decoderFactory = decoderFactory;
    formatHolder = new FormatHolder();
  }

  @Override
  public int supportsFormat(Format format) {
    return decoderFactory.supportsFormat(format) ? FORMAT_HANDLED
        : (MimeTypes.isText(format.sampleMimeType) ? FORMAT_UNSUPPORTED_SUBTYPE
        : FORMAT_UNSUPPORTED_TYPE);
  }

  @Override
  protected void onStreamChanged(Format[] formats) throws ExoPlaybackException {
    if (decoder != null) {
      decoder.release();
      nextInputBuffer = null;
    }
    decoder = decoderFactory.createDecoder(formats[0]);
  }

  @Override
  protected void onPositionReset(long positionUs, boolean joining) {
    clearOutput();
    resetBuffers();
    decoder.flush();
    inputStreamEnded = false;
    outputStreamEnded = false;
  }

  @Override
  public void render(long positionUs, long elapsedRealtimeUs) throws ExoPlaybackException {
    if (outputStreamEnded) {
      return;
    }

    if (nextSubtitle == null) {
      decoder.setPositionUs(positionUs);
      try {
        nextSubtitle = decoder.dequeueOutputBuffer();
      } catch (SubtitleDecoderException e) {
        throw ExoPlaybackException.createForRenderer(e, getIndex());
      }
    }

    if (getState() != STATE_STARTED) {
      return;
    }

    boolean textRendererNeedsUpdate = false;
    if (subtitle != null) {
      // We're iterating through the events in a subtitle. Set textRendererNeedsUpdate if we
      // advance to the next event.
      long subtitleNextEventTimeUs = getNextEventTime();
      while (subtitleNextEventTimeUs <= positionUs) {
        nextSubtitleEventIndex++;
        subtitleNextEventTimeUs = getNextEventTime();
        textRendererNeedsUpdate = true;
      }
    }

    if (nextSubtitle != null) {
      if (nextSubtitle.isEndOfStream()) {
        if (!textRendererNeedsUpdate && getNextEventTime() == Long.MAX_VALUE) {
          if (subtitle != null) {
            subtitle.release();
            subtitle = null;
          }
          nextSubtitle.release();
          nextSubtitle = null;
          outputStreamEnded = true;
        }
      } else if (nextSubtitle.timeUs <= positionUs) {
        // Advance to the next subtitle. Sync the next event index and trigger an update.
        if (subtitle != null) {
          subtitle.release();
        }
        subtitle = nextSubtitle;
        nextSubtitle = null;
        nextSubtitleEventIndex = subtitle.getNextEventTimeIndex(positionUs);
        textRendererNeedsUpdate = true;
      }
    }

    if (textRendererNeedsUpdate) {
      // textRendererNeedsUpdate is set and we're playing. Update the renderer.
      updateOutput(subtitle.getCues(positionUs));
    }

    try {
      while (!inputStreamEnded) {
        if (nextInputBuffer == null) {
          nextInputBuffer = decoder.dequeueInputBuffer();
          if (nextInputBuffer == null) {
            return;
          }
        }
        // Try and read the next subtitle from the source.
        int result = readSource(formatHolder, nextInputBuffer);
        if (result == C.RESULT_BUFFER_READ) {
          // Clear BUFFER_FLAG_DECODE_ONLY (see [Internal: b/27893809]) and queue the buffer.
          nextInputBuffer.clearFlag(C.BUFFER_FLAG_DECODE_ONLY);
          if (nextInputBuffer.isEndOfStream()) {
            inputStreamEnded = true;
          } else {
            nextInputBuffer.subsampleOffsetUs = formatHolder.format.subsampleOffsetUs;
            nextInputBuffer.flip();
          }
          decoder.queueInputBuffer(nextInputBuffer);
          nextInputBuffer = null;
        } else if (result == C.RESULT_NOTHING_READ) {
          break;
        }
      }
    } catch (SubtitleDecoderException e) {
      throw ExoPlaybackException.createForRenderer(e, getIndex());
    }
  }

  @Override
  protected void onDisabled() {
    clearOutput();
    resetBuffers();
    decoder.release();
    decoder = null;
    super.onDisabled();
  }

  @Override
  public boolean isEnded() {
    return outputStreamEnded;
  }

  @Override
  public boolean isReady() {
    // Don't block playback whilst subtitles are loading.
    // Note: To change this behavior, it will be necessary to consider [Internal: b/12949941].
    return true;
  }

  private void resetBuffers() {
    nextInputBuffer = null;
    nextSubtitleEventIndex = C.INDEX_UNSET;
    if (subtitle != null) {
      subtitle.release();
      subtitle = null;
    }
    if (nextSubtitle != null) {
      nextSubtitle.release();
      nextSubtitle = null;
    }
  }

  private long getNextEventTime() {
    return ((nextSubtitleEventIndex == C.INDEX_UNSET)
        || (nextSubtitleEventIndex >= subtitle.getEventTimeCount())) ? Long.MAX_VALUE
        : (subtitle.getEventTime(nextSubtitleEventIndex));
  }

  private void updateOutput(List<Cue> cues) {
    if (outputHandler != null) {
      outputHandler.obtainMessage(MSG_UPDATE_OUTPUT, cues).sendToTarget();
    } else {
      invokeUpdateOutputInternal(cues);
    }
  }

  private void clearOutput() {
    updateOutput(Collections.<Cue>emptyList());
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean handleMessage(Message msg) {
    switch (msg.what) {
      case MSG_UPDATE_OUTPUT:
        invokeUpdateOutputInternal((List<Cue>) msg.obj);
        return true;
    }
    return false;
  }

  private void invokeUpdateOutputInternal(List<Cue> cues) {
    output.onCues(cues);
  }

}
