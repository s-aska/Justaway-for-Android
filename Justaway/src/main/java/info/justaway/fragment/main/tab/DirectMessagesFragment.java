package info.justaway.fragment.main.tab;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;

import java.util.Collections;
import java.util.Comparator;

import info.justaway.JustawayApplication;
import info.justaway.R;
import info.justaway.adapter.TwitterAdapter;
import info.justaway.event.model.DestroyDirectMessageEvent;
import info.justaway.model.Row;
import twitter4j.DirectMessage;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Twitter;

public class DirectMessagesFragment extends BaseFragment {

    private Boolean mAutoLoader = false;
    private Boolean mReload = false;
    private long mDirectMessagesMaxId = 0L;
    private long mSentDirectMessagesMaxId = 0L;
    private ProgressBar mFooter;

    public long getTabId() {
        return -3L;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);
        mFooter = (ProgressBar) v.findViewById(R.id.guruguru);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        if (mDirectMessagesMaxId == 0L && mSentDirectMessagesMaxId == 0L) {
            mDirectMessagesMaxId = -1L;
            new DirectMessagesTask().execute();
        }
    }

    @Override
    public void reload() {
        mReload = true;
        clear();
        getPullToRefreshLayout().setRefreshing(true);
        new DirectMessagesTask().execute();
    }

    @Override
    public void clear() {
        mDirectMessagesMaxId = 0L;
        mSentDirectMessagesMaxId = 0L;
        TwitterAdapter adapter = getListAdapter();
        if (adapter != null) {
            adapter.clear();
        }
    }

    @Override
    public void onRefreshStarted(View view) {
        reload();
    }

    @Override
    protected void additionalReading() {
        if (!mAutoLoader || mReload) {
            return;
        }
        mFooter.setVisibility(View.VISIBLE);
        mAutoLoader = false;
        new DirectMessagesTask().execute();
    }

    public void onEventMainThread(DestroyDirectMessageEvent event) {
        remove(event.getStatusId());
    }

    @Override
    protected boolean skip(Row row) {
        return !row.isDirectMessage();
    }

    public void remove(final long directMessageId) {
        ListView listView = getListView();
        if (listView == null) {
            return;
        }

        final TwitterAdapter adapter = (TwitterAdapter) listView.getAdapter();
        listView.post(new Runnable() {
            @Override
            public void run() {
                adapter.removeDirectMessage(directMessageId);
            }
        });
    }

    private class DirectMessagesTask extends AsyncTask<Void, Void, ResponseList<DirectMessage>> {
        @Override
        protected ResponseList<DirectMessage> doInBackground(Void... params) {
            try {
                JustawayApplication application = JustawayApplication.getApplication();
                Twitter twitter = application.getTwitter();

                // 受信したDM
                Paging directMessagesPaging = new Paging();
                if (mDirectMessagesMaxId > 0) {
                    directMessagesPaging.setMaxId(mDirectMessagesMaxId - 1);
                    directMessagesPaging.setCount(application.getPageCount() / 2);
                } else {
                    directMessagesPaging.setCount(10);
                }
                ResponseList<DirectMessage> directMessages = twitter.getDirectMessages(directMessagesPaging);
                for (DirectMessage directMessage : directMessages) {
                    if (mDirectMessagesMaxId <= 0L || mDirectMessagesMaxId > directMessage.getId()) {
                        mDirectMessagesMaxId = directMessage.getId();
                    }
                }

                // 送信したDM
                Paging sentDirectMessagesPaging = new Paging();
                if (mSentDirectMessagesMaxId > 0) {
                    sentDirectMessagesPaging.setMaxId(mSentDirectMessagesMaxId - 1);
                    sentDirectMessagesPaging.setCount(application.getPageCount() / 2);
                } else {
                    sentDirectMessagesPaging.setCount(10);
                }
                ResponseList<DirectMessage> sentDirectMessages = twitter.getSentDirectMessages(sentDirectMessagesPaging);
                for (DirectMessage directMessage : sentDirectMessages) {
                    if (mSentDirectMessagesMaxId <= 0L || mSentDirectMessagesMaxId > directMessage.getId()) {
                        mSentDirectMessagesMaxId = directMessage.getId();
                    }
                }

                directMessages.addAll(sentDirectMessages);

                // 日付でソート
                Collections.sort(directMessages, new Comparator<DirectMessage>() {

                    @Override
                    public int compare(DirectMessage arg0, DirectMessage arg1) {
                        return arg1.getCreatedAt().compareTo(
                                arg0.getCreatedAt());
                    }
                });
                return directMessages;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(ResponseList<DirectMessage> statuses) {
            mFooter.setVisibility(View.GONE);
            if (statuses == null || statuses.size() == 0) {
                mReload = false;
                getPullToRefreshLayout().setRefreshComplete();
                getListView().setVisibility(View.VISIBLE);
                return;
            }
            TwitterAdapter adapter = getListAdapter();
            if (mReload) {
                adapter.clear();
                for (DirectMessage status : statuses) {
                    adapter.add(Row.newDirectMessage(status));
                }
                mReload = false;
                getPullToRefreshLayout().setRefreshComplete();
            } else {
                for (DirectMessage status : statuses) {
                    adapter.extensionAdd(Row.newDirectMessage(status));
                }
                mAutoLoader = true;
                getListView().setVisibility(View.VISIBLE);
            }
        }
    }
}
