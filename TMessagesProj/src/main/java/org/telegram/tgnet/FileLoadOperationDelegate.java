/*
 * This is the source code of ZiosGram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.blaez.tgnet;

public interface FileLoadOperationDelegate {
    void onFinished(String path);
    void onFailed(int reason);
    void onProgressChanged(float progress);
}
