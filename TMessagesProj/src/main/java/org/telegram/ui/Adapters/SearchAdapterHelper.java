/*
 * This is the source code of ZiosGram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.blaez.ui.Adapters;

import org.blaez.SQLite.SQLiteCursor;
import org.blaez.SQLite.SQLitePreparedStatement;
import org.blaez.ziosgram.AndroidUtilities;
import org.blaez.ziosgram.FileLog;
import org.blaez.ZiosGram.messagesStorage;
import org.blaez.tgnet.ConnectionsManager;
import org.blaez.tgnet.RequestDelegate;
import org.blaez.tgnet.TLObject;
import org.blaez.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SearchAdapterHelper {

    public static class HashtagObject {
        String hashtag;
        int date;
    }

    public interface SearchAdapterHelperDelegate {
        void onDataSetChanged();
        void onSetHashtags(ArrayList<HashtagObject> arrayList, HashMap<String, HashtagObject> hashMap);
    }

    private SearchAdapterHelperDelegate delegate;

    private int reqId = 0;
    private int lastReqId;
    private String lastFoundUsername = null;
    private ArrayList<TLObject> globalSearch = new ArrayList<>();

    private ArrayList<HashtagObject> hashtags;
    private HashMap<String, HashtagObject> hashtagsByText;
    private boolean hashtagsLoadedFromDb = false;

    public void queryServerSearch(final String query, final boolean allowChats, final boolean allowBots, final boolean allowSelf) {
        if (reqId != 0) {
            ConnectionsManager.getInstance().cancelRequest(reqId, true);
            reqId = 0;
        }
        if (query == null || query.length() < 5) {
            globalSearch.clear();
            lastReqId = 0;
            delegate.onDataSetChanged();
            return;
        }
        TLRPC.TL_contacts_search req = new TLRPC.TL_contacts_search();
        req.q = query;
        req.limit = 50;
        final int currentReqId = ++lastReqId;
        reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentReqId == lastReqId) {
                            if (error == null) {
                                TLRPC.TL_contacts_found res = (TLRPC.TL_contacts_found) response;
                                globalSearch.clear();
                                if (allowChats) {
                                    for (int a = 0; a < res.chats.size(); a++) {
                                        globalSearch.add(res.chats.get(a));
                                    }
                                }
                                for (int a = 0; a < res.users.size(); a++) {
                                    TLRPC.User user = res.users.get(a);
                                    if (!allowBots && user.bot || !allowSelf && user.self) {
                                        continue;
                                    }
                                    globalSearch.add(res.users.get(a));
                                }
                                lastFoundUsername = query;
                                delegate.onDataSetChanged();
                            }
                        }
                        reqId = 0;
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public void unloadRecentHashtags() {
        hashtagsLoadedFromDb = false;
    }

    public boolean loadRecentHashtags() {
        if (hashtagsLoadedFromDb) {
            return true;
        }
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT id, date FROM hashtag_recent_v2 WHERE 1");
                    final ArrayList<HashtagObject> arrayList = new ArrayList<>();
                    final HashMap<String, HashtagObject> hashMap = new HashMap<>();
                    while (cursor.next()) {
                        HashtagObject hashtagObject = new HashtagObject();
                        hashtagObject.hashtag = cursor.stringValue(0);
                        hashtagObject.date = cursor.intValue(1);
                        arrayList.add(hashtagObject);
                        hashMap.put(hashtagObject.hashtag, hashtagObject);
                    }
                    cursor.dispose();
                    Collections.sort(arrayList, new Comparator<HashtagObject>() {
                        @Override
                        public int compare(HashtagObject lhs, HashtagObject rhs) {
                            if (lhs.date < rhs.date) {
                                return 1;
                            } else if (lhs.date > rhs.date) {
                                return -1;
                            } else {
                                return 0;
                            }
                        }
                    });
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            setHashtags(arrayList, hashMap);
                        }
                    });
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
        return false;
    }

    public void setDelegate(SearchAdapterHelperDelegate searchAdapterHelperDelegate) {
        delegate = searchAdapterHelperDelegate;
    }

    public void addHashtagsFromMessage(CharSequence message) {
        if (message == null) {
            return;
        }
        boolean changed = false;
        Pattern pattern = Pattern.compile("(^|\\s)#[\\w@\\.]+");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (message.charAt(start) != '@' && message.charAt(start) != '#') {
                start++;
            }
            String hashtag = message.subSequence(start, end).toString();
            if (hashtagsByText == null) {
                hashtagsByText = new HashMap<>();
                hashtags = new ArrayList<>();
            }
            HashtagObject hashtagObject = hashtagsByText.get(hashtag);
            if (hashtagObject == null) {
                hashtagObject = new HashtagObject();
                hashtagObject.hashtag = hashtag;
                hashtagsByText.put(hashtagObject.hashtag, hashtagObject);
            } else {
                hashtags.remove(hashtagObject);
            }
            hashtagObject.date = (int) (System.currentTimeMillis() / 1000);
            hashtags.add(0, hashtagObject);
            changed = true;
        }
        if (changed) {
            putRecentHashtags(hashtags);
        }
    }

    private void putRecentHashtags(final ArrayList<HashtagObject> arrayList) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance().getDatabase().beginTransaction();
                    SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO hashtag_recent_v2 VALUES(?, ?)");
                    for (int a = 0; a < arrayList.size(); a++) {
                        if (a == 100) {
                            break;
                        }
                        HashtagObject hashtagObject = arrayList.get(a);
                        state.requery();
                        state.bindString(1, hashtagObject.hashtag);
                        state.bindInteger(2, hashtagObject.date);
                        state.step();
                    }
                    state.dispose();
                    MessagesStorage.getInstance().getDatabase().commitTransaction();
                    if (arrayList.size() >= 100) {
                        MessagesStorage.getInstance().getDatabase().beginTransaction();
                        for (int a = 100; a < arrayList.size(); a++) {
                            MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM hashtag_recent_v2 WHERE id = '" + arrayList.get(a).hashtag + "'").stepThis().dispose();
                        }
                        MessagesStorage.getInstance().getDatabase().commitTransaction();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public ArrayList<TLObject> getGlobalSearch() {
        return globalSearch;
    }

    public ArrayList<HashtagObject> getHashtags() {
        return hashtags;
    }

    public String getLastFoundUsername() {
        return lastFoundUsername;
    }

    public void clearRecentHashtags() {
        hashtags = new ArrayList<>();
        hashtagsByText = new HashMap<>();
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM hashtag_recent_v2 WHERE 1").stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public void setHashtags(ArrayList<HashtagObject> arrayList, HashMap<String, HashtagObject> hashMap) {
        hashtags = arrayList;
        hashtagsByText = hashMap;
        hashtagsLoadedFromDb = true;
        delegate.onSetHashtags(arrayList, hashMap);
    }
}
