/*
 * This is the source code of ZiosGram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.blaez.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.blaez.ziosgram.AndroidUtilities;
import org.blaez.ui.Components.LayoutHelper;
import org.blaez.ui.Components.RadialProgressView;

public class LoadingCell extends FrameLayout {

    private RadialProgressView progressBar;

    public LoadingCell(Context context) {
        super(context);

        progressBar = new RadialProgressView(context);
        addView(progressBar, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(54), MeasureSpec.EXACTLY));
    }
}
