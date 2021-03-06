package net.melove.app.chat.setting;

import android.os.Bundle;
import android.view.View;
import butterknife.ButterKnife;
import com.hyphenate.chat.EMClient;
import net.melove.app.chat.R;
import net.melove.app.chat.app.MLBaseActivity;

/**
 * Created by lzan13 on 2016/10/31.
 * 应用设置界面
 */
public class MLSettingsActivity extends MLBaseActivity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        mActivity = this;

        ButterKnife.bind(this);

        initView();
    }

    /**
     * UI 界面初始化
     */
    private void initView() {
        setSupportActionBar(getToolbar());
        getToolbar().setTitle(R.string.ml_settings);
        getToolbar().setNavigationIcon(R.mipmap.ic_arrow_back_white_24dp);
        getToolbar().setNavigationOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                onFinish();
            }
        });
        // Display the fragment as the main content.
        getFragmentManager().beginTransaction()
                .replace(R.id.layout_container, new MLSettingsPreference())
                .commit();

        // 更新推送昵称
        EMClient.getInstance().pushManager().updatePushNickname("");
    }
}
