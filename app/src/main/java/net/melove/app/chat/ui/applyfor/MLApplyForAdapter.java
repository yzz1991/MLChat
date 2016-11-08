package net.melove.app.chat.ui.applyfor;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.hyphenate.chat.EMClient;
import com.hyphenate.chat.EMConversation;
import com.hyphenate.chat.EMMessage;

import net.melove.app.chat.R;
import net.melove.app.chat.MLConstants;
import net.melove.app.chat.util.MLLog;
import net.melove.app.chat.ui.widget.MLImageView;

import java.util.Collections;
import java.util.List;

/**
 * Created by lzan13 on 2016/3/17.
 * 申请信息适配器类
 */
public class MLApplyForAdapter extends RecyclerView.Adapter<MLApplyForAdapter.InvitedViewHolder> {

    // 上下文对象
    private Context mContext;

    private LayoutInflater mInflater;

    // 当前会话对象
    private EMConversation mConversation;
    private List<EMMessage> mMessages;

    // 自定义的回调接口
    private MLOnItemClickListener mOnItemClickListener;

    public MLApplyForAdapter(Context context) {
        mContext = context;
        mInflater = LayoutInflater.from(mContext);
        /**
         * 初始化会话对象，这里有三个参数么，
         * mChatid 第一个表示会话的当前聊天的 useranme 或者 groupid
         * null 第二个是会话类型可以为空
         * true 第三个表示如果会话不存在是否创建
         */
        mConversation = EMClient.getInstance()
                .chatManager()
                .getConversation(MLConstants.ML_CONVERSATION_APPLY, null, true);
        mMessages = mConversation.getAllMessages();
        // 将list集合倒序排列
        Collections.reverse(mMessages);
    }

    @Override public InvitedViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = mInflater.inflate(R.layout.item_apply, parent, false);
        InvitedViewHolder holder = new InvitedViewHolder(view);
        return holder;
    }

    @Override public void onBindViewHolder(InvitedViewHolder holder, final int position) {
        MLLog.i("MLApplyForAdapter - onBindViewHolder - %d", position);
        EMMessage message = mMessages.get(position);

        holder.imageViewAvatar.setImageResource(R.mipmap.ic_character_blackcat);

        String username = message.getStringAttribute(MLConstants.ML_ATTR_USERNAME, "");
        // 设置申请的人
        holder.textViewUsername.setText(username);
        // 设置申请理由
        String reason = message.getStringAttribute(MLConstants.ML_ATTR_REASON, "reason");
        holder.textViewReason.setText(reason);
        
        String status = message.getStringAttribute(MLConstants.ML_ATTR_STATUS, "");
        if (TextUtils.isEmpty(status)) {
            holder.btnAgree.setVisibility(View.VISIBLE);
            holder.textViewStatus.setVisibility(View.GONE);
        } else {
            holder.btnAgree.setVisibility(View.GONE);
            holder.textViewStatus.setVisibility(View.GONE);
            holder.textViewStatus.setText(status);
        }

        holder.btnAgree.setTag(position);
        holder.btnAgree.setOnClickListener(viewListener);
        // 给当前 ItemView 设置点击和长按监听
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                // 设置点击动作
                mOnItemClickListener.onItemAction(position, MLConstants.ML_ACTION_APPLY_FOR_CLICK);
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                // 这里直接给长按设置删除操作
                mOnItemClickListener.onItemAction(position, MLConstants.ML_ACTION_APPLY_FOR_DELETE);
                return true;
            }
        });
    }

    @Override public int getItemCount() {
        return mMessages.size();
    }

    /**
     * 申请与通知列表内Button点击事件
     */
    private View.OnClickListener viewListener = new View.OnClickListener() {
        @Override public void onClick(View v) {
            int position = (int) v.getTag();
            switch (v.getId()) {
                case R.id.ml_btn_agree:
                    mOnItemClickListener.onItemAction(position,
                            MLConstants.ML_ACTION_APPLY_FOR_AGREE);
                    break;
            }
        }
    };

    /**
     * 自定义回调接口，用来实现 RecyclerView 中 Item 长按和点击事件监听
     */
    protected interface MLOnItemClickListener {
        /**
         * Item 点击及长按事件的处理
         * 这里Item的点击及长按监听都在当前的 MLApplyForAdapter 实现
         *
         * @param position 需要操作的Item的位置
         * @param action 长按菜单需要处理的动作，
         */
        public void onItemAction(int position, int action);
    }

    /**
     * 设置回调监听
     *
     * @param listener 自定义回调接口
     */
    public void setOnItemClickListener(MLOnItemClickListener listener) {
        mOnItemClickListener = listener;
    }

    /**
     * 自定义ViewHolder
     */
    protected static class InvitedViewHolder extends RecyclerView.ViewHolder {
        MLImageView imageViewAvatar;
        TextView textViewUsername;
        TextView textViewReason;
        TextView textViewStatus;
        Button btnAgree;

        /**
         * 构造方法，初始化列表项的各个控件
         *
         * @param itemView item项的父控件
         */
        public InvitedViewHolder(View itemView) {
            super(itemView);
            imageViewAvatar = (MLImageView) itemView.findViewById(R.id.ml_img_avatar);
            textViewUsername = (TextView) itemView.findViewById(R.id.ml_text_username);
            textViewReason = (TextView) itemView.findViewById(R.id.ml_text_reason);
            textViewStatus = (TextView) itemView.findViewById(R.id.ml_text_status);
            btnAgree = (Button) itemView.findViewById(R.id.ml_btn_agree);
        }
    }
}
