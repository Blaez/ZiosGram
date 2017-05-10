/*
 * This is the source code of ZiosGram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.blaez.ziosgram;

import android.app.IntentService;
import android.content.Intent;

public class NotificationRepeat extends IntentService {

    public NotificationRepeat() {
        super("NotificationRepeat");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                NotificationsController.getInstance().repeatNotificationMaybe();
            }
        });
    }
}
