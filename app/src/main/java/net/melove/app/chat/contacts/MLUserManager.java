package net.melove.app.chat.contacts;

import android.text.TextUtils;
import com.hyphenate.chat.EMClient;
import com.hyphenate.exceptions.HyphenateException;
import java.util.List;
import java.util.Map;
import net.melove.app.chat.app.MLConstants;
import net.melove.app.chat.database.MLDBHelper;
import net.melove.app.chat.database.MLUserDao;
import net.melove.app.chat.network.MLNetworkManager;
import net.melove.app.chat.util.MLLog;
import net.melove.app.chat.util.MLSPUtil;
import net.melove.app.chat.util.MLStringUtil;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by lzan13 on 2016/12/3.
 * 用户信息管理类，处理联系人和陌生人的数据同步，增删改查等
 */
public class MLUserManager {

    // 私有实例对象
    private static MLUserManager instance;
    // 内存中的联系人集合，防止每次都去 db 读取联系人
    private Map<String, MLUserEntity> contactsMap = null;
    // 内存中的陌生人集合，作用同上
    private Map<String, MLUserEntity> strangerMap = null;

    /**
     * 私有构造方法
     */
    private MLUserManager() {
    }

    /**
     * 获取单例类实例
     */
    public static MLUserManager getInstance() {
        if (instance == null) {
            instance = new MLUserManager();
        }
        return instance;
    }

    /**
     * 保存一个用户，保存在内存和本地
     *
     * @param user 用户实体对象
     */
    public void saveUser(MLUserEntity user) {
        if (contactsMap != null && user.getStatus() == MLDBHelper.STATUS_NORMAL) {
            contactsMap.put(user.getUserName(), user);
        } else if (strangerMap != null && user.getStatus() == MLDBHelper.STATUS_STRANGER) {
            strangerMap.put(user.getUserName(), user);
        }
        MLUserDao.getInstance().saveUser(user);
    }

    /**
     * 删除一个用户，删除内存和本地
     *
     * @param username 需要删除的用户 username
     */
    public void deleteUser(String username) {
        if (contactsMap != null) {
            contactsMap.remove(username);
        }
        if (strangerMap != null) {
            strangerMap.remove(username);
        }
        MLUserDao.getInstance().deleteUser(username);
    }

    /**
     * 修改一个用户信息
     *
     * @param user 用户实体类对象
     */
    public void updateUser(MLUserEntity user) {
        if (contactsMap != null && user.getStatus() == MLDBHelper.STATUS_NORMAL) {
            contactsMap.put(user.getUserName(), user);
        } else if (strangerMap != null && user.getStatus() == MLDBHelper.STATUS_STRANGER) {
            strangerMap.put(user.getUserName(), user);
        }
        MLUserDao.getInstance().updateUser(user);
    }

    /***
     * 根据 username 获取用户信息
     *
     * @param username 需要获取的用户 username
     * @return 返回获取到的用户实体类
     */
    public MLUserEntity getUser(String username) {
        MLUserEntity user = null;
        if (contactsMap != null) {
            user = contactsMap.get(username);
        }
        if (user == null && strangerMap != null) {
            user = strangerMap.get(username);
        }
        if (user == null) {
            user = MLUserDao.getInstance().getContacter(username);
        }
        return user;
    }

    /**
     * 获取联系人集合，这个优先获取内存中的，如果内存为空然后读取数据库
     */
    public Map<String, MLUserEntity> getContactsMap() {
        if (contactsMap == null) {
            contactsMap = MLUserDao.getInstance().getContactsMap();
        }
        return contactsMap;
    }

    /**
     * 同步用户联系人到本地
     */
    public void syncContactsFromServer() {
        // 同步联系人时先将数据清空
        MLUserDao.getInstance().clearTable();
        String accessToken = (String) MLSPUtil.get(MLConstants.ML_USER_ACCESS_TOKEN, "");
        try {
            // 从环信服务器同步好友列表
            List<String> list = EMClient.getInstance().contactManager().getAllContactsFromServer();
            String[] usernames = list.toArray(new String[list.size()]);
            String names = MLStringUtil.arrayToStr(usernames, ",");
            if (TextUtils.isEmpty(names)) {
                return;
            }
            MLUserDao.getInstance().saveUserList(list);
            MLLog.d("同步好友列表完成，好友总数：%d", list.size());

            // 从自己的服务器获取好友详细信息
            JSONObject result =
                    MLNetworkManager.getInstance().getUsersInfo(names, accessToken);
            if (result.optInt("code") != 0) {
                return;
            }
            JSONArray jsonArray = result.optJSONArray("data");
            MLUserEntity userEntity = null;
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject object = jsonArray.optJSONObject(i);
                userEntity = new MLUserEntity();
                userEntity.setUserName(object.optString(MLDBHelper.COL_USERNAME));
                userEntity.setNickName(object.optString(MLDBHelper.COL_NICKNAME));
                userEntity.setEmail(object.optString(MLDBHelper.COL_EMAIL));
                userEntity.setAvatar(object.optString(MLDBHelper.COL_AVATAR));
                userEntity.setCover(object.optString(MLDBHelper.COL_COVER));
                userEntity.setGender(object.optInt(MLDBHelper.COL_GENDER));
                userEntity.setLocation(object.optString(MLDBHelper.COL_LOCATION));
                userEntity.setSignature(object.optString(MLDBHelper.COL_SIGNATURE));
                userEntity.setCreateAt(object.optString(MLDBHelper.COL_CREATE_AT));
                userEntity.setUpdateAt(object.optString(MLDBHelper.COL_UPDATE_AT));
                userEntity.setStatus(0);
                MLUserDao.getInstance().updateUser(userEntity);
            }
            MLLog.d("获取好友详细信息完成，获取到详情数：%d", jsonArray.length());
        } catch (HyphenateException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
}
