package info.justaway;

import android.R.color;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import java.util.ArrayList;

import info.justaway.fragment.BaseFragment;
import info.justaway.fragment.DirectMessageFragment;
import info.justaway.fragment.InteractionsFragment;
import info.justaway.fragment.TimelineFragment;
import info.justaway.fragment.UserListFragment;
import info.justaway.model.Row;
import info.justaway.task.DestroyDirectMessageTask;
import info.justaway.task.RefetchFavoriteStatus;
import info.justaway.task.VerifyCredentialsLoader;
import twitter4j.DirectMessage;
import twitter4j.Status;
import twitter4j.StatusDeletionNotice;
import twitter4j.StatusUpdate;
import twitter4j.TwitterStream;
import twitter4j.User;
import twitter4j.UserStreamAdapter;

/**
 * @author aska
 */
public class MainActivity extends FragmentActivity implements LoaderManager.LoaderCallbacks<User> {

    private JustawayApplication mApplication;
    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager viewPager;
    private ProgressDialog mProgressDialog;
    private final int REQUEST_CHOOSE_USER_LIST = 100;
    private final int TAB_ID_TIMELINE = -1;
    private final int TAB_ID_INTERACTIONS = -2;
    private final int TAB_ID_DIRECTMESSAGE = -3;

    /**
     * タブビューを実現するためのもの、とても大事 サポートパッケージv4から、2系でも使えるパッケージを使用
     */
    public ViewPager getViewPager() {
        return viewPager;
    }

    public void setViewPager(ViewPager viewPager) {
        this.viewPager = viewPager;
    }

    /**
     * @param savedInstanceState
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mApplication = JustawayApplication.getApplication();

        // スリープさせない指定
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // アクセストークンがない場合に認証用のアクティビティを起動する
        if (!mApplication.hasAccessToken()) {
            Intent intent = new Intent(this, SigninActivity.class);
            startActivity(intent);
            finish();
        } else if (mApplication.getUserId() < 0 || mApplication.getScreenName() == null) {
            /**
             * onCreateLoader => onLoadFinished と繋がる
             */
            getSupportLoaderManager().initLoader(0, null, this);
        } else {
            setup();
        }

        /**
         * 違うタブだったら移動、同じタブだったら最上部にスクロールという美しい実装
         * ActionBarのタブに頼っていない為、自力でsetCurrentItemでタブを動かしている
         * タブの切替がスワイプだけで良い場合はこの処理すら不要
         */
        Typeface fontello = Typeface.createFromAsset(getAssets(), "fontello.ttf");
        Button home = (Button) findViewById(R.id.action_timeline);
        Button interactions = (Button) findViewById(R.id.action_interactions);
        Button directmessage = (Button) findViewById(R.id.action_directmessage);
        Button tweet = (Button) findViewById(R.id.action_tweet);
        Button send = (Button) findViewById(R.id.send);
        home.setTypeface(fontello);
        interactions.setTypeface(fontello);
        directmessage.setTypeface(fontello);
        tweet.setTypeface(fontello);
        send.setTypeface(fontello);
        home.setOnClickListener(tabMenuOnClickListener(0));
        interactions.setOnClickListener(tabMenuOnClickListener(1));
        directmessage.setOnClickListener(tabMenuOnClickListener(2));
        tweet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), PostActivity.class);
                if (findViewById(R.id.singleLineTweet).getVisibility() == View.VISIBLE) {
                    EditText status = (EditText) findViewById(R.id.editStatus);
                    String msg = status.getText().toString();
                    if (msg != null && msg.length() > 0) {
                        Long inReplyToStatusId = mApplication.getInReplyToStatusId();
                        intent.putExtra("status", msg);
                        intent.putExtra("selection", msg.length());
                        if (inReplyToStatusId != null && inReplyToStatusId > 0) {
                            intent.putExtra("inReplyToStatusId", inReplyToStatusId);
                        }
                        status.setText("");
                        status.clearFocus();
                    }
                }
                startActivity(intent);
            }
        });
        tweet.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                if (findViewById(R.id.singleLineTweet).getVisibility() == View.VISIBLE) {
                    hideQuickPanel();
                } else {
                    showQuickPanel();
                }
                return true;
            }
        });
        send.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText status = (EditText) findViewById(R.id.editStatus);
                String msg = status.getText().toString();
                if (msg != null && msg.length() > 0) {
                    showProgressDialog("送信中！！１１１１１");
                    StatusUpdate super_sugoi = new StatusUpdate(msg);
                    Long inReplyToStatusId = mApplication.getInReplyToStatusId();
                    if (inReplyToStatusId != null && inReplyToStatusId > 0) {
                        super_sugoi.setInReplyToStatusId(inReplyToStatusId);
                        mApplication.setInReplyToStatusId((long) 0);
                    }
                    new UpdateStatusTask().execute(super_sugoi);
                }
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
    }

    public void showQuickPanel() {
        findViewById(R.id.singleLineTweet).setVisibility(View.VISIBLE);
        EditText editStatus = (EditText) findViewById(R.id.editStatus);
        editStatus.setFocusable(true);
        editStatus.setFocusableInTouchMode(true);
        editStatus.setEnabled(true);
        mApplication.setQuickMod(true);
    }

    public void hideQuickPanel() {
        EditText editStatus = (EditText) findViewById(R.id.editStatus);
        editStatus.setFocusable(false);
        editStatus.setFocusableInTouchMode(false);
        editStatus.setEnabled(false);
        editStatus.clearFocus();
        findViewById(R.id.singleLineTweet).setVisibility(View.GONE);
        mApplication.setInReplyToStatusId((long) 0);
        mApplication.setQuickMod(false);
    }

    public void initTab() {
        LinearLayout tab_menus = (LinearLayout) findViewById(R.id.tab_menus);

        int count = tab_menus.getChildCount();
        // 4つめ以降のタブを消す
        if (count > 3) {
            for (int position = count - 1; position > 2; position--) {
                tab_menus.removeView(tab_menus.getChildAt(position));
                mSectionsPagerAdapter.removeTab(position);
            }
            mSectionsPagerAdapter.notifyDataSetChanged();
        }

        ArrayList<Integer> tabs = mApplication.loadTabs();
        int position = 2;
        for (Integer tab : tabs) {
            Typeface fontello = Typeface.createFromAsset(getAssets(), "fontello.ttf");
            // 標準のタブを動的に生成する時に実装する
            if (tab == -1) {

            } else if (tab == -2) {

            } else if (tab == -3) {

            } else if (tab > 0) {
                Button button = new Button(this);
                button.setWidth(60);
                button.setTypeface(fontello);
                button.setTextSize(22);
                button.setBackgroundColor(getResources().getColor(R.color.menu_background));
                button.setText(R.string.fontello_list);
                button.setOnClickListener(tabMenuOnClickListener(++position));
                final int fp = position;
                button.setOnLongClickListener(new View.OnLongClickListener() {

                    @Override
                    public boolean onLongClick(View v) {
                        UserListFragment f = (UserListFragment) mSectionsPagerAdapter
                                .findFragmentByPosition(fp);
                        f.reload();
                        return false;
                    }

                });
                tab_menus.addView(button);
                Bundle args = new Bundle();
                args.putInt("userListId", tab);
                mSectionsPagerAdapter.addTab(UserListFragment.class, args, "list", tab);
            }
        }
        mSectionsPagerAdapter.notifyDataSetChanged();
    }

    @Override
    protected void onStart() {
        super.onStart();

        // 前回バグで強制終了した場合はダイアログ表示、Yesでレポート送信
        MyUncaughtExceptionHandler.showBugReportDialogIfExist(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {

        // ちゃんと接続を切らないとアプリが凍結されるらしい
        TwitterStream twitterStream = mApplication.getTwitterStream();
        if (twitterStream != null) {
            twitterStream.cleanUp();
            twitterStream.shutdown();
        }

        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQUEST_CHOOSE_USER_LIST:
                if (resultCode == RESULT_OK) {
                    Bundle bundle = data.getExtras();
                    ArrayList<Integer> lists = bundle.getIntegerArrayList("lists");
                    ArrayList<Integer> tabs = new ArrayList<Integer>();
                    // 後々タブ設定画面に標準のタブを含める
                    tabs.add(-1);
                    tabs.add(-2);
                    tabs.add(-3);
                    tabs.addAll(lists);
                    mApplication.saveTabs(tabs);
                    mApplication.setLists(lists);
                    initTab();
                } else if (resultCode == RESULT_CANCELED) {
                    ArrayList<Integer> lists = new ArrayList<Integer>();
                    ArrayList<Integer> tabs = new ArrayList<Integer>();
                    // 後々タブ設定画面に標準のタブを含める
                    tabs.add(-1);
                    tabs.add(-2);
                    tabs.add(-3);
                    mApplication.saveTabs(tabs);
                    mApplication.setLists(lists);
                    initTab();
                }
                break;
            default:
                break;
        }
    }

    private View.OnClickListener tabMenuOnClickListener(final int position) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                BaseFragment f = mSectionsPagerAdapter.findFragmentByPosition(position);
                if (f == null) {
                    return;
                }
                int id = viewPager.getCurrentItem();
                if (id != position) {
                    viewPager.setCurrentItem(position);
                    if (f.isTop()) {
                        showTopView();
                    }
                } else {
                    f.goToTop();
                }
            }
        };
    }

    /**
     * 認証済みのユーザーアカウントを取得
     *
     * @param id
     * @param args
     * @return User 認証済みのユーザー
     */
    @Override
    public Loader<User> onCreateLoader(int id, Bundle args) {
        return new VerifyCredentialsLoader(this);
    }

    @Override
    public void onLoadFinished(Loader<User> loader, User user) {

        // VerifyCredentialsLoaderが失敗する場合も考慮
        if (user == null) {
            mApplication.resetAccessToken();
            Intent intent = new Intent(this, SigninActivity.class);
            startActivity(intent);
            finish();
        } else {
            JustawayApplication.showToast(user.getScreenName() + " さんこんにちわ！！！！");
            mApplication.setUserId(user.getId());
            mApplication.setScreenName(user.getScreenName());
            setup();
        }
    }

    public void setup() {

        /**
         * スワイプで動かせるタブを実装するのに最低限必要な実装
         */
        ViewPager viewPager = (ViewPager) findViewById(R.id.pager);
        mSectionsPagerAdapter = new SectionsPagerAdapter(this, viewPager);
        setViewPager(viewPager);

        mSectionsPagerAdapter.addTab(TimelineFragment.class, null, "Home", TAB_ID_TIMELINE);
        mSectionsPagerAdapter.addTab(InteractionsFragment.class, null, "Home",
                TAB_ID_INTERACTIONS);
        mSectionsPagerAdapter.addTab(DirectMessageFragment.class, null, "Home",
                TAB_ID_DIRECTMESSAGE);
        initTab();

        findViewById(R.id.footer).setVisibility(View.VISIBLE);

        /**
         * タブは前後タブまでは状態が保持されるがそれ以上離れるとViewが破棄されてしまう、
         * あまりに使いづらいの上限を増やしている、指定値＋前後のタブまでが保持されるようになる
         * デフォルト値は1（表示しているタブの前後までしか保持されない）
         */
        viewPager.setOffscreenPageLimit(10);

        /**
         * スワイプ移動でも移動先が未読アプしている場合、アピ解除判定を行う
         */
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                BaseFragment f = mSectionsPagerAdapter.findFragmentByPosition(position);
                if (f.isTop()) {
                    showTopView();
                }
                LinearLayout tab_menus = (LinearLayout) findViewById(R.id.tab_menus);
                int count = tab_menus.getChildCount();
                for (int i = 0; i < count; i++) {
                    Button button = (Button) tab_menus.getChildAt(i);
                    if (i == position) {
                        button.setBackgroundColor(getResources().getColor(
                                R.color.menu_active_background));
                    } else {
                        button.setBackgroundColor(getResources().getColor(
                                R.color.menu_background));
                    }
                }
            }
        });

        if (mApplication.getQuickMode()) {
            showQuickPanel();
        }

        TwitterStream twitterStream = mApplication.getTwitterStream();
        twitterStream.addListener(getUserStreamAdapter());
        twitterStream.user();
    }

    @Override
    public void onLoaderReset(Loader<User> arg0) {

    }

    /**
     * 新しいツイートが来たアピ
     */
    public void onNewTimeline(Boolean autoScroll) {
        // 表示中のタブかつ自動スクロール時はハイライトしない
        if (viewPager.getCurrentItem() == 0 && autoScroll == true) {
            return;
        }
        Button button = (Button) findViewById(R.id.action_timeline);
        button.setTextColor(getResources().getColor(color.holo_blue_bright));
    }

    /**
     * 新しいリプが来たアピ
     */
    public void onNewInteractions(Boolean autoScroll) {
        // 表示中のタブかつ自動スクロール時はハイライトしない
        if (viewPager.getCurrentItem() == 1 && autoScroll == true) {
            return;
        }
        Button button = (Button) findViewById(R.id.action_interactions);
        button.setTextColor(getResources().getColor(color.holo_blue_bright));
    }

    /**
     * 新しいDMが来たアピ
     */
    public void onNewDirectMessage(Boolean autoScroll) {
        // 表示中のタブかつ自動スクロール時はハイライトしない
        if (viewPager.getCurrentItem() == 2 && autoScroll == true) {
            return;
        }
        Button button = (Button) findViewById(R.id.action_directmessage);
        button.setTextColor(getResources().getColor(color.holo_blue_bright));
    }

    /**
     * 新しいツイートが来たアピ
     */
    public void onNewListStatus(int listId, Boolean autoScroll) {
        // 表示中のタブかつ自動スクロール時はハイライトしない
        int position = mSectionsPagerAdapter.findPositionById(listId);
        if (viewPager.getCurrentItem() == position && autoScroll == true) {
            return;
        }
        Log.d("Justaway", "listId: " + listId);
        Log.d("Justaway", "position: " + position);
        if (position >= 0) {
            LinearLayout tab_menus = (LinearLayout) findViewById(R.id.tab_menus);
            Button button = (Button) tab_menus.getChildAt(position);
            button.setTextColor(getResources().getColor(color.holo_blue_bright));
        }
    }

    /**
     * 新しいレコードを見たアピ
     */
    public void showTopView() {
        LinearLayout tab_menus = (LinearLayout) findViewById(R.id.tab_menus);
        Button button = (Button) tab_menus.getChildAt(viewPager.getCurrentItem());
        if (button != null) {
            button.setTextColor(getResources().getColor(color.white));
        }
    }

    /**
     * タブの切替毎に必要なFragmentを取得するためのAdapterクラス
     */
    public static class SectionsPagerAdapter extends FragmentPagerAdapter {
        private final Context mContext;
        private final ViewPager mViewPager;
        private final ArrayList<TabInfo> mTabs = new ArrayList<TabInfo>();

        private static final class TabInfo {
            private final Class<?> clazz;
            private final Bundle args;
            private final String tabTitle;
            private final int id;

            /**
             * タブ内のActivity、引数を設定する。
             *
             * @param clazz    タブ内のv4.Fragment
             * @param args     タブ内のv4.Fragmentに対する引数
             * @param tabTitle タブに表示するタイトル
             */
            TabInfo(Class<?> clazz, Bundle args, String tabTitle, int id) {
                this.clazz = clazz;
                this.args = args;
                this.tabTitle = tabTitle;
                this.id = id;
            }
        }

        public SectionsPagerAdapter(FragmentActivity context, ViewPager viewPager) {
            super(context.getSupportFragmentManager());
            viewPager.setAdapter(this);
            mContext = context;
            mViewPager = viewPager;
        }

        @Override
        public BaseFragment getItem(int position) {
            TabInfo info = mTabs.get(position);
            return (BaseFragment) Fragment.instantiate(mContext, info.clazz.getName(), info.args);
        }

        @Override
        public long getItemId(int position) {
            return mTabs.get(position).id;
        }

        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }

        public BaseFragment findFragmentByPosition(int position) {
            return (BaseFragment) instantiateItem(mViewPager, position);
        }

        public int findPositionById(int id) {
            int position = 0;
            for (TabInfo tab : mTabs) {
                if (tab.id == id) {
                    return position;
                }
                position++;
            }
            return -1;
        }

        public BaseFragment findFragmentById(int id) {
            int position = 0;
            for (TabInfo tab : mTabs) {
                if (tab.id == id) {
                    return (BaseFragment) instantiateItem(mViewPager, position);
                }
                position++;
            }
            return null;
        }

        /**
         * タブ内に起動するActivity、引数、タイトルを設定する
         *
         * @param clazz    起動するv4.Fragmentクラス
         * @param args     v4.Fragmentに対する引数
         * @param tabTitle タブのタイトル
         */
        public void addTab(Class<?> clazz, Bundle args, String tabTitle, Integer id) {
            TabInfo info = new TabInfo(clazz, args, tabTitle, id);
            mTabs.add(info);
        }

        public void removeTab(int position) {
            mTabs.remove(position);
        }

        @Override
        public int getCount() {
            // タブ数
            return mTabs.size();
        }
    }

    /**
     * 弄らないとアプリをバックボタンで閉じる度にタイムラインが初期化されてしまう（アクティビティがfinishされる）
     * moveTaskToBackはホームボタンを押した時と同じ動き
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            EditText editText = (EditText) findViewById(R.id.editStatus);
            if (editText.getText().length() > 0) {
                editText.setText("");
                mApplication.setInReplyToStatusId((long) 0);
                return false;
            }
            finish();
        }
        return false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.signout) {
            JustawayApplication.getApplication().resetAccessToken();
            finish();
        } else if (itemId == R.id.onore) {
            Intent intent = new Intent(this, ProfileActivity.class);
            intent.putExtra("screenName", mApplication.getScreenName());
            startActivity(intent);
        } else if (itemId == R.id.user_list) {
            Intent intent = new Intent(this, ChooseUserListsActivity.class);
            startActivityForResult(intent, REQUEST_CHOOSE_USER_LIST);
        } else if (itemId == R.id.search) {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
        }
        return true;
    }

    /**
     * ストリーミング受信時の処理
     */
    private UserStreamAdapter getUserStreamAdapter() {
        final View view = findViewById(R.id.action_interactions);
        return new UserStreamAdapter() {

            @Override
            public void onStatus(Status status) {

                /**
                 * ツイートを表示するかどうかはFragmentに任せる
                 */
                int count = mSectionsPagerAdapter.getCount();
                for (int id = 0; id < (count - 1); id++) {
                    BaseFragment fragmen = (BaseFragment) mSectionsPagerAdapter
                            .findFragmentByPosition(id);
                    if (fragmen != null) {
                        fragmen.add(Row.newStatus(status));
                    }
                }
            }

            @Override
            public void onDeletionNotice(StatusDeletionNotice statusDeletionNotice) {
                super.onDeletionNotice(statusDeletionNotice);
                int count = mSectionsPagerAdapter.getCount();
                for (int id = 0; id < (count - 1); id++) {
                    BaseFragment fragmen = (BaseFragment) mSectionsPagerAdapter
                            .findFragmentByPosition(id);
                    if (fragmen != null) {
                        fragmen.removeStatus(statusDeletionNotice.getStatusId());
                    }
                }
            }

            @Override
            public void onFavorite(User source, User target, Status status) {
                // 自分の fav を反映
                if (source.getId() == mApplication.getUserId()) {
                    mApplication.setFav(status.getId());
                    return;
                }
                Row row = Row.newFavorite(source, target, status);
                BaseFragment fragmen = (BaseFragment) mSectionsPagerAdapter
                        .findFragmentById(TAB_ID_INTERACTIONS);
                new RefetchFavoriteStatus(fragmen).execute(row);
            }

            @Override
            public void onUnfavorite(User arg0, User arg1, Status arg2) {

                final User source = arg0;
                final Status status = arg2;

                // 自分の unfav を反映
                if (source.getId() == mApplication.getUserId()) {
                    mApplication.removeFav(status.getId());
                    return;
                }

                view.post(new Runnable() {
                    @Override
                    public void run() {
                        JustawayApplication.showToast(source.getScreenName() + " unfav "
                                + status.getText());
                    }
                });
            }

            @Override
            public void onDirectMessage(DirectMessage directMessage) {
                super.onDirectMessage(directMessage);
                BaseFragment fragmen = (BaseFragment) mSectionsPagerAdapter
                        .findFragmentById(TAB_ID_DIRECTMESSAGE);
                if (fragmen != null) {
                    fragmen.add(Row.newDirectMessage(directMessage));
                }
            }

            @Override
            public void onDeletionNotice(long directMessageId, long userId) {
                super.onDeletionNotice(directMessageId, userId);
                DirectMessageFragment fragmen = (DirectMessageFragment) mSectionsPagerAdapter
                        .findFragmentById(TAB_ID_DIRECTMESSAGE);
                if (fragmen != null) {
                    fragmen.remove(directMessageId);
                }
            }
        };
    }

    public class UpdateStatusTask extends AsyncTask<StatusUpdate, Void, Boolean> {
        @Override
        protected Boolean doInBackground(StatusUpdate... params) {
            StatusUpdate super_sugoi = params[0];
            try {
                mApplication.getTwitter().updateStatus(super_sugoi);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            dismissProgressDialog();
            if (success) {
                EditText status = (EditText) findViewById(R.id.editStatus);
                status.setText("");
            } else {
                mApplication.showToast("残念~！もう一回！！");
            }
        }
    }

    private void showProgressDialog(String message) {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage(message);
        mProgressDialog.show();
    }

    private void dismissProgressDialog() {
        if (mProgressDialog != null)
            mProgressDialog.dismiss();
    }

    public void doDestroyDirectMessage(Long id) {
        new DestroyDirectMessageTask().execute(id);
        // 自分宛のDMを消してもStreaming APIで拾えないで自力で消す
        DirectMessageFragment fragmen = (DirectMessageFragment) mSectionsPagerAdapter
                .findFragmentById(TAB_ID_DIRECTMESSAGE);
        if (fragmen != null) {
            fragmen.remove(id);
        }
    }
}
