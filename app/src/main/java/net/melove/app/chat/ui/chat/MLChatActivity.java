package net.melove.app.chat.ui.chat;

import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import com.hyphenate.EMCallBack;
import com.hyphenate.EMError;
import com.hyphenate.EMMessageListener;
import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMCmdMessageBody;
import com.hyphenate.chat.EMConversation;
import com.hyphenate.chat.EMFileMessageBody;
import com.hyphenate.chat.EMImageMessageBody;
import com.hyphenate.chat.EMMessage;
import com.hyphenate.chat.EMTextMessageBody;

import com.hyphenate.chat.EMVideoMessageBody;
import com.hyphenate.chat.EMVoiceMessageBody;
import net.melove.app.chat.R;
import net.melove.app.chat.module.event.MLMessageEvent;
import net.melove.app.chat.module.listener.MLItemCallBack;
import net.melove.app.chat.ui.MLBaseActivity;
import net.melove.app.chat.MLConstants;
import net.melove.app.chat.ui.contacts.MLUserActivity;
import net.melove.app.chat.ui.conversation.MLConversationExtUtils;
import net.melove.app.chat.ui.widget.recycler.MLLinearLayoutManager;
import net.melove.app.chat.util.MLDateUtil;
import net.melove.app.chat.util.MLFileUtil;
import net.melove.app.chat.ui.widget.MLRecordView;
import net.melove.app.chat.ui.widget.MLRecorder;
import net.melove.app.chat.ui.call.MLCallStatus;
import net.melove.app.chat.ui.call.MLVideoCallActivity;
import net.melove.app.chat.ui.call.MLVoiceCallActivity;
import net.melove.app.chat.module.notification.MLNotifier;
import net.melove.app.chat.util.MLLog;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by lzan13 on 2015/10/12 15:00.
 * 聊天界面，处理并显示聊天双方信息
 */
public class MLChatActivity extends MLBaseActivity implements EMMessageListener {

    // RecyclerView 用来显示消息
    @BindView(R.id.recycler_view) RecyclerView mRecyclerView;
    // 下拉刷新控件
    @BindView(R.id.swipe_refresh_layout) SwipeRefreshLayout mSwipeRefreshLayout;
    // 聊天扩展菜单主要打开图片、视频、文件、位置等
    @BindView(R.id.layout_attach_menu) LinearLayout mAttachMenuLayout;
    @BindView(R.id.grid_view_attach_menu) GridView mAttachMenuGridView;
    // 表情按钮
    @BindView(R.id.img_emotion) View mEmotionView;
    @BindView(R.id.img_keyboard) View mKeyboardView;
    @BindView(R.id.layout_emotion) RelativeLayout mEmotionLayout;
    // 发送按钮
    @BindView(R.id.img_send) View mSendView;
    // 录音控件
    @BindView(R.id.view_record_voice) MLRecordView mRecordView;
    // 聊天内容输入框
    @BindView(R.id.edit_input_message) EditText mInputView;
    // 新消息提醒按钮
    @BindView(R.id.btn_new_message) Button mNewMessageBtn;

    // 聊天界面消息刷新类型
    private final int MSG_REFRESH_ALL = 0;
    private final int MSG_REFRESH_INSERTED = 1;
    private final int MSG_REFRESH_INSERTED_MORE = 2;
    private final int MSG_REFRESH_REMOVED = 3;
    private final int MSG_REFRESH_CHANGED = 4;
    // 对话框
    private AlertDialog.Builder alertDialogBuilder;
    private AlertDialog photoModeDialog;
    private AlertDialog callModeDialog;
    // 进度对话框
    private ProgressDialog progressDialog;

    // 消息监听器
    private EMMessageListener mMessageListener;

    // 当前聊天的 ID
    private String mChatId;
    // 当前会话对象
    private EMConversation mConversation;
    // 当前聊天类型
    private EMConversation.EMConversationType mConversationType;
    // 消息集合
    //private List<EMMessage> mMessages;
    // 设置每次下拉分页加载多少条
    private int mPageSize = 22;

    // 当前列表适配器
    private MLMessageAdapter mAdapter;
    // RecyclerView 的布局管理器
    private LinearLayoutManager mLayoutManger;

    // 当前是否在最底部
    private boolean isBottom = true;

    // 是否发送原图
    private boolean isOrigin = true;

    // 是否为阅后即焚
    private boolean isBurn;
    // 检测输入状态的开始时间
    private long mOldTime;
    // 定时器，主要用来更新对方输入状态
    private Timer mTimer;
    // 记录对方输入状态
    private boolean isInput;

    private Uri mCameraImageUri = null;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        ButterKnife.bind(this);

        init();

        // 初始化下拉刷新
        initSwipeRefreshLayout();
        // 初始化扩展菜单
        initAttachMenuGridView();

        // 初始化当前会话对象
        initConversation();

        // 设置消息点击监听
        setItemClickListener();
    }

    /**
     * 初始化界面控件
     */
    private void init() {
        mActivity = this;
        mMessageListener = this;

        // 初始化阅后即焚模式为false
        isBurn = false;

        // 获取当前聊天对象的id
        mChatId = getIntent().getStringExtra(MLConstants.ML_EXTRA_CHAT_ID);
        mConversationType = (EMConversation.EMConversationType) getIntent().getExtras()
                .get(MLConstants.ML_EXTRA_TYPE);

        // 初始化输入框控件，并添加输入框监听
        mInputView = (EditText) findViewById(R.id.edit_input_message);
        mOldTime = 0;
        // 设置输入框的内容变化简体呢
        setEditTextWatcher();

        // 设置录音控件回调
        mRecordView.setRecordCallback(recordCallback);

        getToolbar().setTitle(mChatId);
        setSupportActionBar(getToolbar());
        // 设置toolbar图标
        getToolbar().setNavigationIcon(R.mipmap.ic_arrow_back_white_24dp);
        // 设置Toolbar图标点击事件，Toolbar上图标的id是 -1
        getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                onFinish();
            }
        });
    }

    /**
     * 初始化会话对象，并且根据需要加载更多消息
     */
    private void initConversation() {
        /**
         * 初始化会话对象，这里有三个参数，
         * 第一个表示会话的当前聊天的 useranme 或者 groupid
         * 第二个是绘画类型可以为空
         * 第三个表示如果会话不存在是否创建
         */
        mConversation = EMClient.getInstance()
                .chatManager()
                .getConversation(mChatId, mConversationType, true);
        // 设置当前会话未读数为 0
        mConversation.markAllMessagesAsRead();
        MLConversationExtUtils.setConversationUnread(mConversation, false);
        int count = mConversation.getAllMessages().size();
        if (count < mConversation.getAllMsgCount() && count < mPageSize) {
            // 获取已经在列表中的最上边的一条消息id
            String msgId = mConversation.getAllMessages().get(0).getMsgId();
            // 分页加载更多消息，需要传递已经加载的消息的最上边一条消息的id，以及需要加载的消息的条数
            mConversation.loadMoreMsgFromDB(msgId, mPageSize - count);
        }

        // 检查是否有草稿没有发出
        String draft = MLConversationExtUtils.getConversationDraft(mConversation);
        if (!TextUtils.isEmpty(draft)) {
            mInputView.setText(draft);
        }

        /**
         * 为RecyclerView 设置布局管理器，这里使用线性布局
         * RececlerView 默认的布局管理器：
         * LinearLayoutManager          显示垂直滚动列表或水平的项目
         * GridLayoutManager            显示在一个网格项目
         * StaggeredGridLayoutManager   显示在交错网格项目()
         * 自定义的布局管理器，需要继承 {@link android.support.v7.widget.RecyclerView.LayoutManager}
         */
        mLayoutManger = new MLLinearLayoutManager(mActivity);
        // 设置 RecyclerView 的布局方向
        mLayoutManger.setOrientation(LinearLayoutManager.VERTICAL);
        // 设置 RecyclerView 显示状态固定到底部
        //mLayoutManger.setStackFromEnd(true);
        // 初始化ListView控件对象
        mRecyclerView = (RecyclerView) findViewById(R.id.recycler_view);
        // 实例化消息适配器
        mAdapter = new MLMessageAdapter(mActivity, mChatId);

        // 设置 RecyclerView 布局管理器
        mRecyclerView.setLayoutManager(mLayoutManger);
        /**
         *  设置 RecyclerView Item 动画
         * add/remove items时的动画是默认启用的。
         * 自定义这些动画需要继承{@link android.support.v7.widget.RecyclerView.ItemAnimator}，
         * 并实现{@link RecyclerView#setItemAnimator(RecyclerView.ItemAnimator)}
         */
        //mRecyclerView.setItemAnimator(null);
        mRecyclerView.setAdapter(mAdapter);
        mRecyclerView.addOnScrollListener(new MLRecyclerViewListener());
        mRecyclerView.scrollToPosition(mConversation.getAllMessages().size() - 1);
    }

    private void initSwipeRefreshLayout() {
        // 初始化下拉刷新控件对象
        mSwipeRefreshLayout = (SwipeRefreshLayout) findViewById(R.id.swipe_refresh_layout);
        // 设置下拉刷新控件颜色
        mSwipeRefreshLayout.setColorSchemeResources(R.color.ml_red_100, R.color.ml_blue_100,
                R.color.ml_green_100);
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override public void onRefresh() {
                // 防止在下拉刷新的时候，当前界面关闭导致错误
                if (mActivity.isFinishing()) {
                    return;
                }
                // 只有当前会话不为空时才可以下拉加载更多，否则会出现错误
                if (mConversation.getAllMessages().size() > 0) {
                    // 加载更多消息到当前会话的内存中
                    List<EMMessage> messages = mConversation.loadMoreMsgFromDB(
                            mConversation.getAllMessages().get(0).getMsgId(), mPageSize);
                    if (messages.size() > 0) {
                        refreshInsertedMore(0, messages.size());
                    } else {
                        Snackbar.make(getRootView(), R.string.ml_hint_msg_no_more,
                                Snackbar.LENGTH_SHORT).show();
                    }
                }
                // 取消刷新布局
                mSwipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    /**
     * 初始化附件菜单网格列表，并设置网格点击监听
     */
    private void initAttachMenuGridView() {
        mAttachMenuGridView = (GridView) findViewById(R.id.grid_view_attach_menu);
        int[] menuPhotos = {
                R.mipmap.ic_attach_photo, R.mipmap.ic_attach_video, R.mipmap.ic_attach_file,
                R.mipmap.ic_attach_location, R.mipmap.ic_attach_gift, R.mipmap.ic_attach_contacts
        };
        String[] menuTitles = {
                mActivity.getString(R.string.ml_photo), mActivity.getString(R.string.ml_video),
                mActivity.getString(R.string.ml_file), mActivity.getString(R.string.ml_location),
                mActivity.getString(R.string.ml_gift), mActivity.getString(R.string.ml_contacts)
        };
        String[] from = { "photo", "title" };
        int[] to = { R.id.img_menu_photo, R.id.text_menu_title };
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        Map<String, Object> map = null;
        for (int i = 0; i < menuPhotos.length; i++) {
            map = new HashMap<String, Object>();
            map.put("photo", menuPhotos[i]);
            map.put("title", menuTitles[i]);
            list.add(map);
        }
        SimpleAdapter adapter =
                new SimpleAdapter(mActivity, list, R.layout.item_gridview_menu, from, to);
        mAttachMenuGridView.setAdapter(adapter);
        mAttachMenuGridView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        // 弹出选择图片方式对话框
                        selectPhotoMode();
                        break;
                    case 1:
                        // 视频
                        break;
                    case 2:
                        // 选择文件
                        openFileManager();
                        break;
                    case 3:
                        // 发送位置
                        break;
                    case 4:
                        // 礼物
                        break;
                    case 5:
                        // 联系人 名片
                        break;
                    default:
                        Snackbar.make(getRootView(), R.string.ml_toast_unrealized,
                                Snackbar.LENGTH_SHORT).show();
                        break;
                }
                onAttachMenu();
            }
        });
    }

    /**
     * 设置输入框内容的监听 在调用此方法的地方要加一个判断，只有当聊天为群聊时才需要监视
     */
    private void setEditTextWatcher() {
        mInputView.addTextChangedListener(new TextWatcher() {
            /**
             * 输入框内容改变之前
             * params s         输入框内容改变前的内容
             * params start     输入框内容开始变化的索引位置，从0开始计数
             * params count     输入框内容将要减少的变化的字符数 大于0 表示删除字符
             * params after     输入框内容将要增加的文本的长度，大于0 表示增加字符
             */
            @Override public void beforeTextChanged(CharSequence s, int start, int count,
                    int after) {
                MLLog.d("beforeTextChanged s-%s, start-%d, count-%d, after-%d", s, start, count,
                        after);
            }

            /**
             * 输入框内容改变
             * params s         输入框内容改变后的内容
             * params start     输入框内容开始变化的索引位置，从0开始计数
             * params before    输入框内容减少的文本的长度
             * params count     输入框内容改变的字符数量
             */
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                MLLog.d("onTextChanged s-%s, start-%d, before-%d, count-%d", s, start, before,
                        count);
                // 当新增内容长度为1时采取判断增加的字符是否为@符号
                if (mConversation.getType() == EMConversation.EMConversationType.Chat) {
                    if ((MLDateUtil.getCurrentMillisecond() - mOldTime)
                            > MLConstants.ML_TIME_INPUT_STATUS) {
                        mOldTime = System.currentTimeMillis();
                        // 调用发送输入状态方法
                        MLMessageUtils.sendInputStatusMessage(mChatId);
                    }
                }
            }

            /**
             * 输入框内容改变之后
             * params s 输入框最终的内容
             */
            @Override public void afterTextChanged(Editable s) {
                MLLog.d("afterTextChanged s-" + s);
                if (s.toString().equals("")) {
                    mSendView.setVisibility(View.GONE);
                    mRecordView.setVisibility(View.VISIBLE);
                } else {
                    mSendView.setVisibility(View.VISIBLE);
                    mRecordView.setVisibility(View.GONE);
                }
            }
        });
    }

    /**
     * 因为RecyclerView 不支持直接设置长按和点击监听，这里通过回调的方式去进行实现
     */
    private void setItemClickListener() {
        /**
         * 这里实现在{@link MLMessageAdapter MLOnItemClickListener}定义的接口，
         * 实现聊天信息ItemView需要操作的一些方法
         */
        mAdapter.setOnItemClickListener(new MLItemCallBack() {

            /**
             * Item 点击及长按事件的处理
             *
             * @param action 长按菜单需要处理的动作，
             * @param tag 需要操作的 Item 的 tag，这里定义为一个 Object，可以根据需要进行类型转换
             */
            @Override public void onAction(int action, Object tag) {
                EMMessage message = (EMMessage) tag;
                int position = mConversation.getMessagePosition(message);
                switch (action) {
                    case R.id.img_avatar:
                        avatarClick(message);
                        break;
                    case MLConstants.ML_ACTION_CLICK:
                        // 消息点击事件，不同的消息有不同的触发
                        itemClick(message);
                        break;
                    case MLConstants.ML_ACTION_LONG_CLICK:
                        itemLongClick(message);
                        break;
                    case MLConstants.ML_ACTION_RESEND:
                        // 重发消息
                        resendMessage(position, message);
                        break;
                    case MLConstants.ML_ACTION_COPY:
                        // 复制消息，只有文本类消息才可以复制
                        copyMessage(message);
                        break;
                    case MLConstants.ML_ACTION_FORWARD:
                        // 转发消息
                        forwardMessage(message);
                        break;
                    case MLConstants.ML_ACTION_DELETE:
                        // 删除消息
                        deleteMessage(position, message);
                        break;
                    case MLConstants.ML_ACTION_RECALL:
                        // 撤回消息
                        recallMessage(message);
                        break;
                }
            }
        });
    }

    /**
     * 头像点击触发
     *
     * @param message 当前点击的消息项
     */
    private void avatarClick(EMMessage message) {
        Intent intent = new Intent();
        intent.setClass(mActivity, MLUserActivity.class);
        intent.putExtra(MLConstants.ML_EXTRA_CHAT_ID, message.getFrom());
        mActivity.startActivity(intent);
    }

    /**
     * 消息点击事件，不同的消息有不同的触发
     *
     * @param message 点击的消息
     */
    private void itemClick(EMMessage message) {
        if (message.getType() == EMMessage.Type.IMAGE) {
            // 图片
            Intent intent = new Intent();
            intent.setClass(mActivity, MLBigImageActivity.class);
            // 将被点击的消息ID传递过去
            intent.putExtra(MLConstants.ML_EXTRA_MSG_ID, message.getMsgId());
            mActivity.startActivity(intent);
        } else if (message.getBooleanAttribute(MLConstants.ML_ATTR_CALL_VIDEO, false)) {
            // 如果进行语音通话中，就不能进行视频通话
            if (MLCallStatus.getInstance().getCallType() == MLCallStatus.CALL_TYPE_VOICE) {
                Snackbar.make(getRootView(), R.string.ml_call_voice_calling, Snackbar.LENGTH_SHORT)
                        .show();
            } else {
                // 视频通话
                Intent intent = new Intent();
                intent.setClass(mActivity, MLVideoCallActivity.class);
                // 设置被呼叫放的username
                intent.putExtra(MLConstants.ML_EXTRA_CHAT_ID, mChatId);
                // 设置通话为自己呼叫出的
                intent.putExtra(MLConstants.ML_EXTRA_IS_INCOMING_CALL, false);
                // 设置当前通话类型为视频通话
                MLCallStatus.getInstance().setCallType(MLCallStatus.CALL_TYPE_VIDEO);
                mActivity.startActivity(intent);
            }
        } else if (message.getBooleanAttribute(MLConstants.ML_ATTR_CALL_VOICE, false)) {
            // 同理，如果进行视频通话中，就不能进行语音通话
            if (MLCallStatus.getInstance().getCallType() == MLCallStatus.CALL_TYPE_VIDEO) {
                Snackbar.make(getRootView(), R.string.ml_call_video_calling, Snackbar.LENGTH_SHORT)
                        .show();
            } else {
                // 语音通话
                Intent intent = new Intent();
                intent.setClass(mActivity, MLVoiceCallActivity.class);
                // 设置被呼叫放的username
                intent.putExtra(MLConstants.ML_EXTRA_CHAT_ID, mChatId);
                // 设置通话为自己呼叫出的
                intent.putExtra(MLConstants.ML_EXTRA_IS_INCOMING_CALL, false);
                // 设置当前通话类型为视频通话
                MLCallStatus.getInstance().setCallType(MLCallStatus.CALL_TYPE_VOICE);
                mActivity.startActivity(intent);
            }
        }
    }

    /**
     * 当前列表项长按事件
     *
     * @param message 点击长按的消息
     */
    private void itemLongClick(EMMessage message) {

    }

    /**
     * 重发消息方法，这里会先复制一份 EMMessage 对象，先删除失败的消息，然后修改消息时间重新发送 TODO 如果修改了消息时间，会导致conversation里有两条同一个id的消息，所以先删除，后修改时间
     */
    public void resendMessage(int position, EMMessage message) {
        EMMessage msg = null;
        switch (message.getType()) {
            case TXT:
                EMTextMessageBody txtBody = (EMTextMessageBody) message.getBody();
                msg = EMMessage.createTxtSendMessage(txtBody.getMessage(), message.getTo());
                break;
            case IMAGE:
                EMImageMessageBody imgBody = (EMImageMessageBody) message.getBody();
                msg = EMMessage.createImageSendMessage(imgBody.getLocalUrl(), isOrigin,
                        message.getTo());
                break;
            case FILE:
                EMFileMessageBody fileBody = (EMFileMessageBody) message.getBody();
                msg = EMMessage.createFileSendMessage(fileBody.getLocalUrl(), message.getTo());
                break;
            case LOCATION:
                break;
            case VIDEO:
                EMVideoMessageBody videoBody = (EMVideoMessageBody) message.getBody();
                msg = EMMessage.createVideoSendMessage(videoBody.getLocalUrl(),
                        videoBody.getLocalThumb(), videoBody.getDuration(), message.getTo());
                break;
            case VOICE:
                EMVoiceMessageBody voiceBody = (EMVoiceMessageBody) message.getBody();
                msg = EMMessage.createVoiceSendMessage(voiceBody.getLocalUrl(),
                        voiceBody.getLength(), message.getTo());
                break;
        }
        // 将失败的消息从 conversation对象中删除
        mConversation.removeMessage(message.getMsgId());
        // 删除时先刷新下
        refreshRemoved(position);
        // 更新消息时间
        message.setMsgTime(MLDateUtil.getCurrentMillisecond());
        // 重新调用发送消息方法
        sendMessage(msg);
    }

    /**
     * 复制消息到剪切板，只有 Text 文本类型的消息才能复制
     *
     * @param message 需要复制的消息对象
     */
    private void copyMessage(EMMessage message) {
        // 获取剪切板管理者
        ClipboardManager clipboardManager =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // 创建剪切板数据对象
        ClipData clipData = ClipData.newPlainText("message",
                ((EMTextMessageBody) message.getBody()).getMessage());
        // 将刚创建的数据对象添加到剪切板
        clipboardManager.setPrimaryClip(clipData);
        // 弹出提醒
        Snackbar.make(getRootView(), R.string.ml_toast_copy_success, Snackbar.LENGTH_SHORT).show();
    }

    /**
     * 转发消息
     *
     * @param message 需要转发的消息对象
     */
    private void forwardMessage(EMMessage message) {

    }

    /**
     * 删除一条消息
     *
     * @param message 需要删除的消息
     */
    private void deleteMessage(int position, EMMessage message) {
        // 删除消息，此方法会同时删除内存和数据库中的数据
        mConversation.removeMessage(message.getMsgId());
        // 弹出操作提示
        Snackbar.make(getRootView(), R.string.ml_toast_delete_success, Snackbar.LENGTH_SHORT)
                .show();
        refreshRemoved(position);
    }

    /**
     * 撤回消息，将已经发送成功的消息进行撤回
     *
     * @param message 需要撤回的消息
     */
    private void recallMessage(final EMMessage message) {
        // 显示撤回消息操作的 dialog
        progressDialog = new ProgressDialog(mActivity);
        progressDialog.setMessage("正在撤回 请稍候~~");
        progressDialog.show();
        MLMessageUtils.sendRecallMessage(message, new EMCallBack() {
            @Override public void onSuccess() {
                // 关闭进度对话框
                progressDialog.dismiss();
                // 设置扩展为撤回消息类型，是为了区分消息的显示
                message.setAttribute(MLConstants.ML_ATTR_RECALL, true);
                // 更新消息
                EMClient.getInstance().chatManager().updateMessage(message);
                // 撤回成功，刷新 UI
                refreshChanged(mConversation.getMessagePosition(message));
            }

            /**
             * 撤回消息失败
             * @param i 失败的错误码
             * @param s 失败的错误信息
             */
            @Override public void onError(final int i, final String s) {
                progressDialog.dismiss();
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        // 弹出错误提示
                        if (s.equals(MLConstants.ML_ERROR_S_RECALL_TIME)) {
                            Snackbar.make(getRootView(), R.string.ml_toast_recall_failed_max_time,
                                    Snackbar.LENGTH_SHORT).show();
                        } else {
                            Snackbar.make(getRootView(), mActivity.getResources()
                                            .getString(R.string.ml_toast_recall_failed) + i + "-" + s,
                                    Snackbar.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override public void onProgress(int i, String s) {

            }
        });
    }

    /**
     * 设置消息扩展
     *
     * @param message 需要发送的消息
     */
    private void setMessageAttribute(EMMessage message) {
        if (isBurn) {
            // 设置消息扩展类型为阅后即焚
            message.setAttribute(MLConstants.ML_ATTR_BURN, true);
        }

        message.setAttribute("em_force_notification", true);
        JSONObject extJson = new JSONObject();
        try {
            extJson.put("em_push_title", "这是推送标题");
            extJson.put("extern", "这是扩展内容");
            message.setAttribute("em_apns_ext", extJson);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * 最终调用发送信息方法
     *
     * @param message 需要发送的消息
     */
    private void sendMessage(final EMMessage message) {
        // 设置不同的会话类型，
        if (mConversationType == EMConversation.EMConversationType.Chat) {
            message.setChatType(EMMessage.ChatType.Chat);
        } else if (mConversationType == EMConversation.EMConversationType.GroupChat) {
            message.setChatType(EMMessage.ChatType.GroupChat);
        } else if (mConversationType == EMConversation.EMConversationType.ChatRoom) {
            message.setChatType(EMMessage.ChatType.ChatRoom);
        }
        // 调用设置消息扩展方法
        setMessageAttribute(message);

        // 发送一条新消息时插入新消息的位置，这里直接用插入新消息前的消息总数来作为新消息的位置
        int position = mConversation.getAllMessages().size();
        /**
         *  调用sdk的消息发送方法发送消息，发送消息时要尽早的设置消息监听，防止消息状态已经回调，
         *  但是自己没有注册监听，导致检测不到消息状态的变化
         *  所以这里在发送之前先设置消息的状态回调
         */
        message.setMessageStatusCallback(new EMCallBack() {
            @Override public void onSuccess() {
                MLLog.i("消息发送成功 msgId %s, content %s", message.getMsgId(), message.getBody());
                // 创建并发出一个可订阅的关于的消息事件
                MLMessageEvent event = new MLMessageEvent();
                event.setMessage(message);
                event.setStatus(message.status());
                EventBus.getDefault().post(event);
            }

            @Override public void onError(final int i, final String s) {
                MLLog.i("消息发送失败 code: %d, error: %s", i, s);
                // 创建并发出一个可订阅的关于的消息事件
                MLMessageEvent event = new MLMessageEvent();
                event.setMessage(message);
                event.setStatus(message.status());
                // 设置消息 erorCode
                event.setErrorCode(i);
                // 设置消息 errorMessage
                event.setErrorMessage(s);
                EventBus.getDefault().post(event);
            }

            @Override public void onProgress(int i, String s) {
                // TODO 消息发送进度，这里不处理，留给消息Item自己去更新
                MLLog.i("消息发送中 progress: %d, %s", i, s);
                // 创建并发出一个可订阅的关于的消息事件
                MLMessageEvent event = new MLMessageEvent();
                event.setMessage(message);
                event.setStatus(message.status());
                // 带上消息发送进度
                event.setProgress(i);
                EventBus.getDefault().post(event);
            }
        });
        // 发送消息
        EMClient.getInstance().chatManager().sendMessage(message);
        // 刷新 UI 界面
        refreshInserted(mConversation.getMessagePosition(message));
    }

    /**
     * 发送文本消息
     */
    private void sendTextMessage() {
        String content = mInputView.getText().toString();
        mInputView.setText("");
        // 设置界面按钮状态
        mSendView.setVisibility(View.GONE);
        mRecordView.setVisibility(View.VISIBLE);
        // 创建一条文本消息
        EMMessage textMessage = EMMessage.createTxtSendMessage(content, mChatId);
        // 调用刷新消息的方法，
        sendMessage(textMessage);
    }

    /**
     * 发送图片消息
     *
     * @param path 要发送的图片的路径
     */
    private void sendImageMessage(String path) {
        /**
         * 根据图片路径创建一条图片消息，需要三个参数，
         * path     图片路径
         * isOrigin 是否发送原图
         * mChatId  接收者
         */
        EMMessage imgMessage = EMMessage.createImageSendMessage(path, isOrigin, mChatId);
        sendMessage(imgMessage);
    }

    /**
     * 发送文件消息
     *
     * @param path 要发送的文件的路径
     */
    private void sendFileMessage(String path) {
        /**
         * 根据文件路径创建一条文件消息
         */
        EMMessage fileMessage = EMMessage.createFileSendMessage(path, mChatId);
        sendMessage(fileMessage);
    }

    /**
     * 发送语音消息
     *
     * @param path 语音文件的路径
     */
    private void sendVoiceMessage(String path, int time) {
        EMMessage voiceMessage = EMMessage.createVoiceSendMessage(path, time, mChatId);
        sendMessage(voiceMessage);
    }

    /**
     * 录音控件回调接口，用来监听录音控件录制结果
     */
    private MLRecordView.MLRecordCallback recordCallback = new MLRecordView.MLRecordCallback() {
        @Override public void onCancel() {

        }

        @Override public void onFailed(int error) {
            switch (error) {
                case MLRecorder.ERROR_FAILED:
                    // 录音失败，一般是权限问题
                    Snackbar.make(getRootView(), R.string.ml_error_voice_failed,
                            Snackbar.LENGTH_SHORT).show();
                    break;
                case MLRecorder.ERROR_SHORT:
                    // 录音时间过短
                    Snackbar.make(getRootView(), R.string.ml_error_voice_short,
                            Snackbar.LENGTH_SHORT).show();
                    break;
                case MLRecorder.ERROR_SYSTEM:
                    // 系统问题
                    Snackbar.make(getRootView(), R.string.ml_error_voice_system,
                            Snackbar.LENGTH_SHORT).show();
                    break;
            }
        }

        @Override public void onStart() {

        }

        @Override public void onSuccess(String path, int time) {
            sendVoiceMessage(path, time);
        }
    };

    /**
     * 处理Activity的返回值得方法
     *
     * @param requestCode 请求码
     * @param resultCode 返回码
     * @param data 返回的数据
     */
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        MLLog.d("onActivityResult requestCode %d, resultCode %d", requestCode, resultCode);
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case MLConstants.ML_REQUEST_CODE_CAMERA:
                // 相机拍摄的图片
                sendImageMessage(mCameraImageUri.getPath());
                break;
            case MLConstants.ML_REQUEST_CODE_GALLERY:
                // 图库选择的图片，选择图片后返回获取返回的图片路径，然后发送图片
                if (data != null) {
                    String imagePath = MLFileUtil.getPath(mActivity, data.getData());
                    if (TextUtils.isEmpty(imagePath) || !new File(imagePath).exists()) {
                        Snackbar.make(getRootView(), "图片不存在", Snackbar.LENGTH_SHORT).show();
                        return;
                    }
                    sendImageMessage(imagePath);
                }
                break;
            case MLConstants.ML_REQUEST_CODE_VIDEO:
                // 视频文件 TODO 可以自定义实现录制小视频
                break;
            case MLConstants.ML_REQUEST_CODE_FILE:
                // 选择文件后返回获取返回的文件路径，然后发送文件
                if (data != null) {
                    String filePath = MLFileUtil.getPath(mActivity, data.getData());
                    sendFileMessage(filePath);
                }
                break;
            case MLConstants.ML_REQUEST_CODE_LOCATION:
                // TODO 发送位置消息
                break;
            case MLConstants.ML_REQUEST_CODE_GIFT:
                // TODO 发送礼物
                break;
            case MLConstants.ML_REQUEST_CODE_USER:
                // TODO 发送联系人名片
                break;
            default:
                break;
        }
    }

    /**
     * 聊天界面按钮监听事件
     */
    @OnClick({
            R.id.img_emotion, R.id.img_keyboard, R.id.img_send, R.id.layout_attach_menu,
            R.id.btn_new_message
    }) void onClick(View view) {
        switch (view.getId()) {
            case R.id.img_emotion:
            case R.id.img_keyboard:
                // 表情按钮
                onEmotion();
                break;
            case R.id.img_send:
                // 发送按钮
                sendTextMessage();
                break;
            case R.id.layout_attach_menu:
                // 附件菜单背景，点击空白处用来关闭菜单
                onAttachMenu();
                break;
            case R.id.btn_new_message:
                break;
        }
    }

    /**
     * 弹出选择图片发方式，是使用相机还是图库
     */
    private void selectPhotoMode() {
        String[] menus = {
                mActivity.getString(R.string.ml_menu_chat_camera),
                mActivity.getString(R.string.ml_menu_chat_gallery)
        };
        if (alertDialogBuilder == null) {
            alertDialogBuilder = new AlertDialog.Builder(mActivity);
        }
        // 设置弹出框 title
        //        alertDialogBuilder.setTitle(mActivity.getString(R.string.ml_dialog_title_select_photo_mode));
        // 设置弹出框的菜单项及点击事件
        alertDialogBuilder.setItems(menus, new DialogInterface.OnClickListener() {
            @Override public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0:
                        // 打开相机直接拍照
                        openCamera();
                        break;
                    case 1:
                        // 打开图库选择图片
                        openGallery();
                        break;
                    default:
                        openGallery();
                        break;
                }
            }
        });
        photoModeDialog = alertDialogBuilder.create();
        photoModeDialog.show();
    }

    /**
     * 打开相机去拍摄图片发送
     */
    private void openCamera() {
        // 定义拍照后图片保存的路径以及文件名
        String imagePath =
                MLFileUtil.getDCIM() + "IMG" + MLDateUtil.getDateTimeNoSpacing() + ".jpg";
        // 激活相机
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // 判断存储卡是否可以用，可用进行存储
        if (MLFileUtil.hasSdcard()) {
            // 根据文件路径解析成Uri
            mCameraImageUri = Uri.fromFile(new File(imagePath));
            // 将Uri设置为媒体输出的目标，目的就是为了等下拍照保存在自己设定的路径
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mCameraImageUri);
        }
        // 根据 Intent 启动一个带有返回值的 Activity，这里启动的就是相机，返回选择图片的地址
        mActivity.startActivityForResult(intent, MLConstants.ML_REQUEST_CODE_CAMERA);
    }

    /**
     * 打开系统图库，去进行选择图片
     */
    private void openGallery() {
        Intent intent = null;
        if (Build.VERSION.SDK_INT < 19) {
            intent = new Intent();
            intent.setAction(Intent.ACTION_GET_CONTENT);
            // 设置intent要选择的文件类型，这里用设置为image 图片类型
            intent.setType("image/*");
        } else {
            // 在Android 系统版本大于19 上，调用系统选择图片方法稍有不同
            intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        mActivity.startActivityForResult(intent, MLConstants.ML_REQUEST_CODE_GALLERY);
    }

    /**
     * 打开系统文件管理器，去选择文件
     */
    private void openFileManager() {
        // 设置intent属性，跳转到系统文件选择界面
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_GET_CONTENT);
        // 设置intent要选择的文件类型，这里用 * 表示选择全部类型
        intent.setType("*/*");
        startActivityForResult(intent, MLConstants.ML_REQUEST_CODE_FILE);
    }

    /**
     * 附件菜单
     */
    private void onAttachMenu() {
        // 判断当前附件菜单显示状态，显示就关闭，不显示就打开
        if (mAttachMenuLayout.isShown()) {
            mAttachMenuLayout.setVisibility(View.GONE);
        } else {
            mAttachMenuLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 选择通话模式
     */
    private void selectCallMode() {
        if (MLCallStatus.getInstance().getCallState() == MLCallStatus.CALL_STATUS_NORMAL) {
            String[] menus = {
                    mActivity.getString(R.string.ml_video_call),
                    mActivity.getString(R.string.ml_voice_call)
            };
            if (alertDialogBuilder == null) {
                alertDialogBuilder = new AlertDialog.Builder(mActivity);
            }
            // 设置菜单项及点击监听
            alertDialogBuilder.setItems(menus, new DialogInterface.OnClickListener() {
                @Override public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent();
                    switch (which) {
                        case 0:
                            // 视频通话
                            intent.setClass(mActivity, MLVideoCallActivity.class);
                            // 设置当前通话类型为视频通话
                            MLCallStatus.getInstance().setCallType(MLCallStatus.CALL_TYPE_VIDEO);
                            break;
                        case 1:
                            // 语音通话
                            intent.setClass(mActivity, MLVoiceCallActivity.class);
                            // 设置当前通话类型为语音通话
                            MLCallStatus.getInstance().setCallType(MLCallStatus.CALL_TYPE_VOICE);
                            break;
                        default:
                            break;
                    }
                    // 设置被呼叫放的username
                    intent.putExtra(MLConstants.ML_EXTRA_CHAT_ID, mChatId);
                    // 设置通话为自己呼叫出的
                    intent.putExtra(MLConstants.ML_EXTRA_IS_INCOMING_CALL, false);
                    mActivity.startActivity(intent);
                }
            });
            callModeDialog = alertDialogBuilder.create();
            callModeDialog.show();
        } else if (MLCallStatus.getInstance().getCallType() == MLCallStatus.CALL_TYPE_VIDEO) {
            // 视频通话
            Intent intent = new Intent(mActivity, MLVideoCallActivity.class);
            // 设置被呼叫放的username
            intent.putExtra(MLConstants.ML_EXTRA_CHAT_ID, mChatId);
            // 设置通话为自己呼叫出的
            intent.putExtra(MLConstants.ML_EXTRA_IS_INCOMING_CALL, false);
            mActivity.startActivity(intent);
        } else if (MLCallStatus.getInstance().getCallType() == MLCallStatus.CALL_TYPE_VOICE) {
            // 语音通话
            Intent intent = new Intent(mActivity, MLVoiceCallActivity.class);
            // 设置被呼叫放的username
            intent.putExtra(MLConstants.ML_EXTRA_CHAT_ID, mChatId);
            // 设置通话为自己呼叫出的
            intent.putExtra(MLConstants.ML_EXTRA_IS_INCOMING_CALL, false);
            mActivity.startActivity(intent);
        }
    }

    /**
     * 表情菜单
     */
    private void onEmotion() {
        // 判断表情界面是否显示状态，显示则关闭，隐藏则显示
        if (mEmotionLayout.isShown()) {
            mEmotionView.setVisibility(View.VISIBLE);
            mKeyboardView.setVisibility(View.GONE);
            mEmotionLayout.setVisibility(View.GONE);
        } else {
            mEmotionView.setVisibility(View.GONE);
            mKeyboardView.setVisibility(View.VISIBLE);
            mEmotionLayout.setVisibility(View.VISIBLE);
        }
    }

    /**
     * 设置对方正在输入状态，主要是接收到CMD消息后，得知对方正在输入
     */
    private void setInputStatus() {
        // 设置title为正在输入状态
        getToolbar().setTitle(R.string.ml_title_input_status);
        if (mTimer == null) {
            mTimer = new Timer();
        } else {
            mTimer.purge();
        }
        // 创建定时器任务
        TimerTask task = new TimerTask() {
            @Override public void run() {
                // 执行定时器把当前对方输入状态设置为false
                isInput = false;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        getToolbar().setTitle(mChatId);
                    }
                });
            }
        };
        // 设置定时任务
        mTimer.schedule(task, MLConstants.ML_TIME_INPUT_STATUS);
    }

    /**
     * 使用注解的方式订阅消息改变事件，主要监听发送消息的回调状态改变： SUCCESS      成功 FAIL         失败 INPROGRESS   进度 CREATE
     * 创建
     *
     * @param event 包含消息的事件
     */
    @Subscribe(threadMode = ThreadMode.MAIN) public void onEventBus(MLMessageEvent event) {
        // 获取订阅事件包含的消息对象
        EMMessage message = event.getMessage();
        // 判断是不是对方输入状态的信息
        if (message.getType() == EMMessage.Type.CMD && isInput) {
            setInputStatus();
            return;
        }
        // 获取消息状态
        EMMessage.Status status = event.getStatus();
        if (status == EMMessage.Status.FAIL) {
            String errorMessage = null;
            int errorCode = event.getErrorCode();
            if (errorCode == EMError.MESSAGE_INCLUDE_ILLEGAL_CONTENT) {
                // 消息包含敏感词
                errorMessage = mActivity.getString(R.string.ml_toast_have_illegal);
            } else if (errorCode == EMError.GROUP_PERMISSION_DENIED) {
                // 不在群里内
                errorMessage = mActivity.getString(R.string.ml_toast_not_join_group);
            } else if (errorCode == EMError.FILE_INVALID) {
                // 文件过大
                errorMessage = mActivity.getString(R.string.ml_toast_file_too_big);
            } else {
                // 发送失败
                errorMessage = mActivity.getString(R.string.ml_toast_send_failed);
            }
            Snackbar.make(getRootView(), errorMessage + event.getErrorMessage() + "-" + errorCode,
                    Snackbar.LENGTH_SHORT).show();
            // 消息状态回调完毕，刷新当前消息显示，这里不论成功失败都要刷新
            mAdapter.notifyItemChanged(mConversation.getMessagePosition(message));
        } else if (status == EMMessage.Status.SUCCESS) {
            // 消息状态回调完毕，刷新当前消息显示，这里不论成功失败都要刷新
            mAdapter.notifyItemChanged(mConversation.getMessagePosition(message));
        } else {
            // 如果是进度变化就不做刷新，留给Item自己刷新
        }
    }

    /**
     * 重写菜单项的选择事件
     *
     * @param item 点击的是哪一个菜单项
     */
    @Override public boolean onOptionsItemSelected(MenuItem item) {
        // 获取当前会话内存中的消息数量
        int count = mConversation.getAllMessages().size();

        switch (item.getItemId()) {
            case R.id.action_call:
                selectCallMode();
                break;
            case R.id.action_attachment:
                // 打开或关闭附件菜单
                onAttachMenu();
                break;
            case R.id.action_delete:
                // 清空会话信息，此方法只清除内存中加载的消息，并没有清除数据库中保存的消息
                // mConversation.clear();
                // 清除全部信息，包括数据库中的
                mConversation.clearAllMessages();
                refreshAll();
                break;
            case R.id.action_settings:

                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        if (mConversationType == EMConversation.EMConversationType.GroupChat) {
            menu.findItem(R.id.action_call).setVisible(false);
        }
        return super.onCreateOptionsMenu(menu);
    }

    /**
     * 自定义返回方法，做一些不能在 onDestroy 里做的操作
     */
    @Override protected void onFinish() {
        /**
         * 当会话聊天界面销毁的时候，
         * 通过{@link MLConversationExtUtils#setConversationLastTime(EMConversation)}设置会话的最后时间
         */
        MLConversationExtUtils.setConversationLastTime(mConversation);
        /**
         * 减少内存用量，这里设置为当前会话内存中多于3条时清除内存中的消息，只保留一条
         */
        if (mConversation.getAllMessages().size() > mPageSize) {
            // 将会话内的消息从内存中清除，节省内存，但是还要在重新加载一条
            List<String> list = new ArrayList<String>();
            list.add(mConversation.getLastMessage().getMsgId());
            // 清除内存中的消息，此方法不清空DBadd
            mConversation.clear();
            // mConversation.clearAllMessages();
            // 加载消息到内存
            mConversation.loadMessages(list);
        }

        /**
         * 判断聊天输入框内容是否为空，不为空就保存输入框内容到{@link EMConversation}的扩展中
         * 调用{@link MLConversationExtUtils#setConversationDraft(EMConversation, String)}方法
         */
        String draft = mInputView.getText().toString().trim();
        if (!TextUtils.isEmpty(draft)) {
            // 将输入框的内容保存为草稿
            MLConversationExtUtils.setConversationDraft(mConversation, draft);
        } else {
            // 清空会话对象扩展中保存的草稿
            MLConversationExtUtils.setConversationDraft(mConversation, "");
        }

        // 这里将父类的方法在后边调用
        super.onFinish();
    }

    /**
     * 重写父类的onNewIntent方法，防止打开两个聊天界面
     *
     * @param intent 带有参数的intent
     */
    @Override protected void onNewIntent(Intent intent) {
        String chatId = intent.getStringExtra(MLConstants.ML_EXTRA_CHAT_ID);
        // 判断 intent 携带的数据是否是当前聊天对象
        if (mChatId.equals(chatId)) {
            super.onNewIntent(intent);
        } else {
            onFinish();
            startActivity(intent);
        }
    }

    @Override public void onBackPressed() {
        // super.onBackPressed();
        onFinish();
    }

    @Override protected void onResume() {
        super.onResume();
        // 注册环信的消息监听器
        EMClient.getInstance().chatManager().addMessageListener(mMessageListener);
    }

    @Override protected void onStop() {
        super.onStop();
        // 再当前界面处于非活动状态时 移除消息监听
        EMClient.getInstance().chatManager().removeMessageListener(mMessageListener);
    }

    @Override protected void onDestroy() {
        mActivity = null;
        // 检测弹出框是否显示状态，如果是显示中则销毁，避免 activity 的销毁导致异常
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        if (photoModeDialog != null && photoModeDialog.isShowing()) {
            photoModeDialog.dismiss();
        }
        if (callModeDialog != null && callModeDialog.isShowing()) {
            callModeDialog.dismiss();
        }
        super.onDestroy();
    }

    /**
     * --------------------------- RecyclerView 滚动监听 --------------------------------
     * 自定义实现RecyclerView的滚动监听，监听
     */
    class MLRecyclerViewListener extends RecyclerView.OnScrollListener {
        /**
         * 监听 RecyclerView 滚动状态的变化
         *
         * @param recyclerView 当前监听的 RecyclerView 控件
         * @param newState RecyclerView 变化的状态
         */
        @Override public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            // 当 RecyclerView 停止滚动后判断当前是否在底部
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                int lastItem = mLayoutManger.findLastVisibleItemPosition();
                if (lastItem == (mAdapter.getItemCount() - 1)) {
                    isBottom = true;
                } else {
                    isBottom = false;
                }
            }
            // MLLog.i("onScrollStateChanged - isNeedScroll:%b, isSmoothScroll:%b, newState:%d, isBottom:%b", isNeedScroll, isSmoothScroll, newState, isBottom);
        }

        /**
         * RecyclerView 正在滚动中
         *
         * @param recyclerView 当前监听的 RecyclerView 控件
         * @param dx 水平变化值，表示水平滚动，正表示向右，负表示向左
         * @param dy 垂直变化值，表示上下滚动，正表示向下，负表示向上
         */
        @Override public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);
            // 如果正在向上滚动，则也设置 isBottom 状态为false
            if (dy < 0) {
                isBottom = false;
            }
            // MLLog.i("onScrolled - isNeedScroll:%b, isSmoothScroll:%b, dy:%d", isNeedScroll, isSmoothScroll, dy);
        }
    }

    /**
     * ----------------------------------- 聊天界面刷新 ------------------------------------------
     * 刷新聊天界面 Handler，
     */
    Handler handler = new Handler() {
        @Override public void handleMessage(Message msg) {
            int what = msg.what;
            int position = msg.arg1;
            int count = msg.arg2;
            switch (what) {
                case MSG_REFRESH_ALL:
                    mAdapter.refreshAll();
                    break;
                case MSG_REFRESH_INSERTED:
                    mAdapter.refreshInserted(position);
                    mRecyclerView.smoothScrollToPosition(position);
                    break;
                case MSG_REFRESH_INSERTED_MORE:
                    mAdapter.refreshInsertedMore(position, count);
                    mRecyclerView.smoothScrollToPosition(position);
                    break;
                case MSG_REFRESH_REMOVED:
                    mAdapter.refreshRemoved(position);
                    break;
                case MSG_REFRESH_CHANGED:
                    mAdapter.refreshChanged(position);
                    break;
            }
        }
    };

    /**
     * 刷新界面
     */
    private void refreshAll() {
        handler.sendMessage(handler.obtainMessage(MSG_REFRESH_ALL));
    }

    /**
     * 有新消息来时的刷新方法
     *
     * @param position 数据添加位置
     */
    private void refreshInserted(int position) {
        Message msg = handler.obtainMessage(MSG_REFRESH_INSERTED);
        msg.arg1 = position;
        handler.sendMessage(msg);
    }

    /**
     * 加载更多消息时的刷新方法
     *
     * @param position 数据添加位置
     * @param count 数据添加数量
     */
    private void refreshInsertedMore(int position, int count) {
        Message msg = handler.obtainMessage(MSG_REFRESH_INSERTED_MORE);
        msg.arg1 = position;
        msg.arg2 = count;
        handler.sendMessage(msg);
    }

    /**
     * 删除消息时的刷新方法
     *
     * @param position 需要刷新的位置
     */
    private void refreshRemoved(int position) {
        Message msg = handler.obtainMessage(MSG_REFRESH_REMOVED);
        msg.arg1 = position;
        handler.sendMessage(msg);
    }

    /**
     * 消息改变时刷新方法
     *
     * @param position 数据改变的位置
     */
    private void refreshChanged(int position) {
        Message msg = handler.obtainMessage(MSG_REFRESH_CHANGED);
        msg.arg1 = position;
        handler.sendMessage(msg);
    }

    /**
     * --------------------------------- Message Listener -------------------------------------
     * 环信消息监听主要方法
     *
     * 收到新消息
     *
     * @param list 收到的新消息集合
     */
    @Override public void onMessageReceived(List<EMMessage> list) {
        MLLog.i("onMessageReceived list.size:%d", list.size());
        boolean isNotify = false;
        // 循环遍历当前收到的消息
        for (EMMessage message : list) {
            String conversationId = "";
            if (mConversationType == EMConversation.EMConversationType.Chat) {
                conversationId = message.getFrom();
            } else {
                conversationId = message.getTo();
            }
            // 判断消息是否是当前会话的消息
            if (mChatId.equals(conversationId)) {
                // 设置消息为已读
                mConversation.markMessageAsRead(message.getMsgId());
                // 收到新消息就把对方正在输入状态设置为false
                isInput = false;

                MLLog.d("messages -1- position - %d, adapter - %d",
                        mConversation.getMessagePosition(message), mAdapter.getItemCount());
                // 刷新界面
                refreshInserted(mConversation.getMessagePosition(message));
            } else {
                // 不发送通知
                isNotify = true;
            }
        }
        if (isNotify) {
            // 如果消息不是当前会话的消息发送通知栏通知
            MLNotifier.getInstance().sendNotificationMessageList(list);
        }
    }

    /**
     * 收到新的 CMD 消息nani
     */
    @Override public void onCmdMessageReceived(List<EMMessage> list) {
        for (int i = 0; i < list.size(); i++) {
            // 透传消息
            EMMessage cmdMessage = list.get(i);
            EMCmdMessageBody body = (EMCmdMessageBody) cmdMessage.getBody();
            // 判断是不是撤回消息的透传
            if (body.action().equals(MLConstants.ML_ATTR_RECALL)) {
                // 收到透传的CMD消息后，调用撤回消息方法进行处理
                boolean result = MLMessageUtils.receiveRecallMessage(cmdMessage);
                // 撤回消息之后，判断是否当前聊天界面，用来刷新界面
                if (mChatId.equals(cmdMessage.getFrom()) && result) {
                    String msgId = cmdMessage.getStringAttribute(MLConstants.ML_ATTR_MSG_ID, null);
                    int position =
                            mConversation.getMessagePosition(mConversation.getMessage(msgId, true));
                    refreshChanged(position);
                }
            }
            // 判断消息是否是当前会话的消息，并且收到的CMD是否是输入状态的消息
            if (mChatId.equals(cmdMessage.getFrom())
                    && body.action()
                    .equals(MLConstants.ML_ATTR_INPUT_STATUS)
                    && cmdMessage.getChatType() == EMMessage.ChatType.Chat
                    && cmdMessage.getFrom().equals(mChatId)) {
                // 收到输入状态新消息则把对方正在输入状态设置为true
                isInput = true;
                // 创建并发出一个可订阅的关于的消息事件
                MLMessageEvent event = new MLMessageEvent();
                event.setMessage(cmdMessage);
                EventBus.getDefault().post(event);
            }
        }
    }

    /**
     * 收到消息的已读回执
     *
     * @param list 收到消息已读回执
     */
    @Override public void onMessageReadAckReceived(List<EMMessage> list) {
        MLLog.i("onMessageReadAckReceived list.size:%d", list.size());
        for (EMMessage message : list) {
            // 判断消息是否是当前会话的消息，并且是自己发送的消息，只有是发送的消息才需要判断 ACK
            if (mChatId.equals(message.getTo())) {
                // 调用刷新方法，因为到来的消息可能不是当前会话的，所以要循环判断
                int position = mConversation.getMessagePosition(message);
                refreshChanged(position);
            }
        }
    }

    /**
     * 收到消息的已送达回执
     *
     * @param list 收到发送回执的消息集合
     */
    @Override public void onMessageDeliveryAckReceived(List<EMMessage> list) {
        MLLog.i("onMessageDeliveryAckReceived list.size:%d", list.size());
        for (EMMessage message : list) {
            // 判断消息是否是当前会话的消息，并且是自己发送的消息，只有是发送的消息才需要判断 ACK
            if (mChatId.equals(message.getTo())) {
                // 调用刷新方法，因为到来的消息可能不是当前会话的，所以要循环判断
                int position = mConversation.getMessagePosition(message);
                refreshChanged(position);
            }
        }
    }

    /**
     * 消息的改变
     *
     * @param message 发生改变的消息
     * @param object 包含改变的信息
     */
    @Override public void onMessageChanged(EMMessage message, Object object) {
        MLLog.i("onMessageChanged message:%s, object:%s", message.getMsgId(), object);
        //int position = mConversation.getMessagePosition(message);
        //refreshChanged(position);
    }
}
