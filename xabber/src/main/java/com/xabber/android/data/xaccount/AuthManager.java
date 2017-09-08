package com.xabber.android.data.xaccount;

import android.util.Base64;

import com.google.gson.Gson;
import com.xabber.android.data.SettingsManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import rx.Single;
import rx.functions.Func1;

/**
 * Created by valery.miller on 17.07.17.
 */

public class AuthManager {

    public static final String PROVIDER_FACEBOOK = "facebook";
    public static final String PROVIDER_TWITTER = "twitter";
    public static final String PROVIDER_GITHUB = "github";
    public static final String PROVIDER_GOOGLE = "google";

    public static Single<XAccountTokenDTO> login(String login, String pass) {

        String credentials = login + ":" + pass;
        byte[] data = credentials.getBytes();
        String encodedCredentials = Base64.encodeToString(data, Base64.NO_WRAP);

        return HttpApiManager.getXabberApi().login("Basic " + encodedCredentials);
    }

    public static Single<ResponseBody> logout(final boolean deleteAccounts) {

        return HttpApiManager.getXabberApi().logout(getXabberTokenHeader())
                .flatMap(new Func1<ResponseBody, Single<? extends ResponseBody>>() {
                    @Override
                    public Single<? extends ResponseBody> call(ResponseBody responseBody) {
                        if (XabberAccountManager.getInstance().deleteXabberAccountFromRealm())
                            return Single.just(responseBody);
                        else return Single.error(new Throwable("Realm: xabber account deletion error"));
                    }
                })
                .flatMap(new Func1<ResponseBody, Single<? extends ResponseBody>>() {
                    @Override
                    public Single<? extends ResponseBody> call(ResponseBody responseBody) {
                        if (deleteAccounts) {
                            if (XabberAccountManager.getInstance().deleteSyncedXMPPAccountsFromRealm())
                                return Single.just(responseBody);
                            else
                                return Single.error(new Throwable("Realm: xmpp accounts deletion error"));
                        } else return Single.just(responseBody);
                    }
                })
                .flatMap(new Func1<ResponseBody, Single<? extends ResponseBody>>() {
                    @Override
                    public Single<? extends ResponseBody> call(ResponseBody responseBody) {
                        if (XabberAccountManager.getInstance().deleteDeadXMPPAccountsFromRealm())
                            return Single.just(responseBody);
                        else
                            return Single.error(new Throwable("Realm: dead xmpp accounts deletion error"));
                    }
                });
    }

    public static Single<XAccountTokenDTO> loginSocial(String provider, String socialToken) {

        Gson gson = new Gson();
        String credentials = gson.toJson(new AccessToken(socialToken));
        return HttpApiManager.getXabberApi().loginSocial(new SocialAuthRequest(provider, credentials));
    }

    public static Single<XAccountTokenDTO> loginSocialTwitter(
           String socialToken, String twitterTokenSecret, String secret, String key) {

        Gson gson = new Gson();
        String credentials = gson.toJson(new TwitterAccessToken(new TwitterTokens(twitterTokenSecret, socialToken), secret, key));
        return HttpApiManager.getXabberApi().loginSocial(new SocialAuthRequest(PROVIDER_TWITTER, credentials));
    }

    public static Single<XabberAccount> getAccount(final String token) {
        return HttpApiManager.getXabberApi().getAccount("Token " + token)
                .flatMap(new Func1<XabberAccountDTO, Single<? extends XabberAccount>>() {
                    @Override
                    public Single<? extends XabberAccount> call(XabberAccountDTO xabberAccountDTO) {
                        return XabberAccountManager.getInstance().saveOrUpdateXabberAccountToRealm(xabberAccountDTO, token);
                    }
                });
    }

    public static Single<List<XMPPAccountSettings>> getClientSettings() {
        return HttpApiManager.getXabberApi().getClientSettings(getXabberTokenHeader())
                .flatMap(new Func1<ListClientSettingsDTO, Single<? extends List<XMPPAccountSettings>>>() {
                    @Override
                    public Single<? extends List<XMPPAccountSettings>> call(ListClientSettingsDTO listClientSettingsDTO) {
                        // convert dto to pojo
                        return XabberAccountManager.getInstance().clientSettingsDTOListToPOJO(listClientSettingsDTO);
                    }
                })
                .flatMap(new Func1<List<XMPPAccountSettings>, Single<? extends List<XMPPAccountSettings>>>() {
                    @Override
                    public Single<? extends List<XMPPAccountSettings>> call(List<XMPPAccountSettings> xmppAccounts) {
                        // add only new accounts from server to sync map
                        Map<String, Boolean> syncState = new HashMap<>();
                        for (XMPPAccountSettings account : xmppAccounts) {
                            if (XabberAccountManager.getInstance().getAccountSyncState(account.getJid()) == null)
                                syncState.put(account.getJid(), true);
                        }
                        XabberAccountManager.getInstance().setAccountSyncState(syncState);

                        // update local accounts
                        return XabberAccountManager.getInstance().updateLocalAccounts(xmppAccounts);
                    }
                });
    }

    // ONLY FOR TEST !!
//    public static Single<List<XMPPAccountSettings>> getClientSettingsForStart() {
//        return HttpApiManager.getXabberApi().getClientSettings(getXabberTokenHeader())
//                .flatMap(new Func1<ListClientSettingsDTO, Single<? extends List<XMPPAccountSettings>>>() {
//                    @Override
//                    public Single<? extends List<XMPPAccountSettings>> call(ListClientSettingsDTO listClientSettingsDTO) {
//                        return XabberAccountManager.getInstance().clientSettingsDTOListToPOJO(listClientSettingsDTO);
//                    }
//                })
//                .flatMap(new Func1<List<XMPPAccountSettings>, Single<? extends List<XMPPAccountSettings>>>() {
//                    @Override
//                    public Single<? extends List<XMPPAccountSettings>> call(List<XMPPAccountSettings> xmppAccounts) {
//                        // add only new accounts from server to sync map
//                        Map<String, Boolean> syncState = new HashMap<>();
//                        for (XMPPAccountSettings account : xmppAccounts) {
//                            if (XabberAccountManager.getInstance().getAccountSyncState(account.getJid()) == null)
//                                syncState.put(account.getJid(), true);
//                        }
//                        XabberAccountManager.getInstance().setAccountSyncState(syncState);
//
//                        // update local accounts
//                        return XabberAccountManager.getInstance().updateLocalAccounts(xmppAccounts);
//                    }
//                });
//    }

    public static Single<List<XMPPAccountSettings>> getClientSettingsWithoutSavingToRealm() {
        return HttpApiManager.getXabberApi().getClientSettings(getXabberTokenHeader())
                .flatMap(new Func1<ListClientSettingsDTO, Single<? extends List<XMPPAccountSettings>>>() {
                    @Override
                    public Single<? extends List<XMPPAccountSettings>> call(ListClientSettingsDTO listClientSettingsDTO) {
                        return XabberAccountManager.getInstance().clientSettingsDTOListToPOJO(listClientSettingsDTO);
                    }
                })
                .flatMap(new Func1<List<XMPPAccountSettings>, Single<? extends List<XMPPAccountSettings>>>() {
                    @Override
                    public Single<? extends List<XMPPAccountSettings>> call(List<XMPPAccountSettings> xmppAccounts) {
                        // add only new accounts from server to sync map
                        Map<String, Boolean> syncState = new HashMap<>();
                        for (XMPPAccountSettings account : xmppAccounts) {
                            if (XabberAccountManager.getInstance().getAccountSyncState(account.getJid()) == null)
                                syncState.put(account.getJid(), true);
                        }
                        XabberAccountManager.getInstance().setAccountSyncState(syncState);

                        return Single.just(xmppAccounts);
                    }
                });
    }

    public static Single<List<XMPPAccountSettings>> updateClientSettings(List<XMPPAccountSettings> accountSettingsList) {

        List<ClientSettingsDTO> list = new ArrayList<>();
        List<OrderDTO> listOrder = new ArrayList<>();

        for (XMPPAccountSettings account : accountSettingsList) {
            // add to sync only accounts required sync
            if (account.isSynchronization() || SettingsManager.isSyncAllAccounts()) {
                list.add(new ClientSettingsDTO(account.getJid(), new SettingsValuesDTO(account.getOrder(),
                        account.getColor(), account.getToken(), account.getUsername()), account.getTimestamp()));
                if (account.getOrder() > 0)
                    listOrder.add(new OrderDTO(account.getJid(), account.getOrder()));
            }
        }

        OrderDataDTO orderDataDTO = new OrderDataDTO(listOrder, XabberAccountManager.getInstance().getLastOrderChangeTimestamp());

        ClientSettingsWithoutOrderDTO listClientSettingsDTO = new ClientSettingsWithoutOrderDTO(list);
        final ClientSettingsOrderDTO clientSettingsOrderDTO = new ClientSettingsOrderDTO(orderDataDTO);

        return HttpApiManager.getXabberApi().updateClientSettings(getXabberTokenHeader(), listClientSettingsDTO)
                .flatMap(new Func1<ListClientSettingsDTO, Single<? extends ListClientSettingsDTO>>() {
                    @Override
                    public Single<? extends ListClientSettingsDTO> call(ListClientSettingsDTO listClientSettingsDTO) {
                        return HttpApiManager.getXabberApi().updateClientSettings(getXabberTokenHeader(), clientSettingsOrderDTO);
                    }
                })
                .flatMap(new Func1<ListClientSettingsDTO, Single<? extends ListClientSettingsDTO>>() {
                    @Override
                    public Single<? extends ListClientSettingsDTO> call(ListClientSettingsDTO listClientSettingsDTO) {
                        return HttpApiManager.getXabberApi().getClientSettings(getXabberTokenHeader());
                    }
                })
                .flatMap(new Func1<ListClientSettingsDTO, Single<? extends List<XMPPAccountSettings>>>() {
                    @Override
                    public Single<? extends List<XMPPAccountSettings>> call(ListClientSettingsDTO listClientSettingsDTO) {
                        return XabberAccountManager.getInstance().saveOrUpdateXMPPAccountSettingsToRealm(listClientSettingsDTO);
                    }
                })
                .flatMap(new Func1<List<XMPPAccountSettings>, Single<? extends List<XMPPAccountSettings>>>() {
                    @Override
                    public Single<? extends List<XMPPAccountSettings>> call(List<XMPPAccountSettings> xmppAccounts) {
                        return XabberAccountManager.getInstance().updateLocalAccounts(xmppAccounts);
                    }
                });
    }

    public static Single<List<XMPPAccountSettings>> patchClientSettings(List<XMPPAccountSettings> accountSettingsList) {
        List<OrderDTO> listOrder = new ArrayList<>();
        List<ClientSettingsDTO> listSettings = new ArrayList<>();

        // divide all data into two lists: settings and orders
        for (XMPPAccountSettings account : accountSettingsList) {
            listSettings.add(new ClientSettingsDTO(account.getJid(), new SettingsValuesDTO(account.getOrder(),
                    account.getColor(), account.getToken(), account.getUsername()), account.getTimestamp()));

            if (account.getOrder() > 0)
                listOrder.add(new OrderDTO(account.getJid(), account.getOrder()));
        }

        // prepare dto for settings
        ClientSettingsWithoutOrderDTO settingsDTO = new ClientSettingsWithoutOrderDTO(listSettings);

        // prepare dto for orders
        OrderDataDTO orderDataDTO = new OrderDataDTO(listOrder, XabberAccountManager.getInstance().getLastOrderChangeTimestamp());
        final ClientSettingsOrderDTO orderDTO = new ClientSettingsOrderDTO(orderDataDTO);

        // patch settings to server
        return HttpApiManager.getXabberApi().updateClientSettings(getXabberTokenHeader(), settingsDTO)
                .flatMap(new Func1<ListClientSettingsDTO, Single<? extends ListClientSettingsDTO>>() {
                    @Override
                    public Single<? extends ListClientSettingsDTO> call(ListClientSettingsDTO listClientSettingsDTO) {
                        // patch orders to server
                        return HttpApiManager.getXabberApi().updateClientSettings(getXabberTokenHeader(), orderDTO);
                    }
                })
                .flatMap(new Func1<ListClientSettingsDTO, Single<? extends List<XMPPAccountSettings>>>() {
                    @Override
                    public Single<? extends List<XMPPAccountSettings>> call(ListClientSettingsDTO listClientSettingsDTO) {
                        // convert dto to pojo
                        return XabberAccountManager.getInstance().clientSettingsDTOListToPOJO(listClientSettingsDTO);
                    }
                })
                .flatMap(new Func1<List<XMPPAccountSettings>, Single<? extends List<XMPPAccountSettings>>>() {
                    @Override
                    public Single<? extends List<XMPPAccountSettings>> call(List<XMPPAccountSettings> xmppAccounts) {
                        // add only new accounts from server to sync map
                        Map<String, Boolean> syncState = new HashMap<>();
                        for (XMPPAccountSettings account : xmppAccounts) {
                            if (XabberAccountManager.getInstance().getAccountSyncState(account.getJid()) == null)
                                    syncState.put(account.getJid(), true);
                        }
                        XabberAccountManager.getInstance().setAccountSyncState(syncState);

                        // update local accounts
                        return XabberAccountManager.getInstance().updateLocalAccounts(xmppAccounts);
                    }
                });
    }

    public static Single<XAccountTokenDTO> signup(String email) {
        return HttpApiManager.getXabberApi().signup(new Email(email));
    }

    public static Single<XabberAccount> confirmEmail(String code) {
        return HttpApiManager.getXabberApi().confirmEmail(getXabberTokenHeader(), new Code(code))
                .flatMap(new Func1<XabberAccountDTO, Single<? extends XabberAccount>>() {
                    @Override
                    public Single<? extends XabberAccount> call(XabberAccountDTO xabberAccountDTO) {
                        return XabberAccountManager.getInstance().saveOrUpdateXabberAccountToRealm(xabberAccountDTO, getXabberToken());
                    }
                });
    }

    public static Single<XabberAccount> confirmEmailWithKey(String key) {
        return HttpApiManager.getXabberApi().confirmEmail(new Key(key))
                .flatMap(new Func1<XabberAccountDTO, Single<? extends XabberAccount>>() {
                    @Override
                    public Single<? extends XabberAccount> call(XabberAccountDTO xabberAccountDTO) {
                        return XabberAccountManager.getInstance().saveOrUpdateXabberAccountToRealm(xabberAccountDTO, getXabberToken());
                    }
                });
    }

    public static Single<XabberAccount> completeRegister(String username, String pass, String confirmPass,
                                                         String firstName, String lastName, String host) {
        return HttpApiManager.getXabberApi().completeRegister(getXabberTokenHeader(),
                new CompleteRegister(username, pass, confirmPass, firstName, lastName, host))
                .flatMap(new Func1<XabberAccountDTO, Single<? extends XabberAccount>>() {
                    @Override
                    public Single<? extends XabberAccount> call(XabberAccountDTO xabberAccountDTO) {
                        return XabberAccountManager.getInstance().saveOrUpdateXabberAccountToRealm(xabberAccountDTO, getXabberToken());
                    }
                });
    }

    public static Single<ResponseBody> addEmail(String email) {
        return HttpApiManager.getXabberApi().addEmail(getXabberTokenHeader(), new Email(email));
    }

    // support

    private static String getXabberTokenHeader() {
        return "Token " + getXabberToken();
    }

    private static String getXabberToken() {
        XabberAccount account = XabberAccountManager.getInstance().getAccount();
        if (account != null)
            return account.getToken();
        else return null;
    }

    // models

    public static class CompleteRegister {
        final String username;
        final String password;
        final String confirm_password;
        final String first_name;
        final String last_name;
        final String host;

        public CompleteRegister(String username, String password, String confirm_password, String first_name, String last_name, String host) {
            this.username = username;
            this.password = password;
            this.confirm_password = confirm_password;
            this.first_name = first_name;
            this.last_name = last_name;
            this.host = host;
        }
    }

    public static class Key {
        final String key;

        public Key(String key) {
            this.key = key;
        }
    }

    public static class Code {
        final String code;

        public Code(String code) {
            this.code = code;
        }
    }

    public static class Email {
        final String email;

        public Email(String email) {
            this.email = email;
        }
    }

    public static class SocialAuthRequest {
        final String provider;
        final String credentials;

        public SocialAuthRequest(String provider, String credentials) {
            this.provider = provider;
            this.credentials = credentials;
        }
    }

    public static class AccessToken {
        final String access_token;

        public AccessToken(String access_token) {
            this.access_token = access_token;
        }
    }

    public static class TwitterAccessToken {
        final TwitterTokens access_token;
        final String secret;
        final String key;

        public TwitterAccessToken(TwitterTokens access_token, String secret, String key) {
            this.access_token = access_token;
            this.secret = secret;
            this.key = key;
        }
    }

    public static class TwitterTokens {
        final String oauth_token_secret;
        final String oauth_token;

        public TwitterTokens(String oauth_token_secret, String oauth_token) {
            this.oauth_token_secret = oauth_token_secret;
            this.oauth_token = oauth_token;
        }
    }

    public static class UpdateClientSettings {
        final String jid;
        final UpdateSettingsValues settings;

        public UpdateClientSettings(String jid, UpdateSettingsValues settings) {
            this.jid = jid;
            this.settings = settings;
        }
    }

    public static class UpdateSettingsValues {
        final int order;

        public UpdateSettingsValues(int order) {
            this.order = order;
        }
    }

    public static class ListClientSettingsDTO {
        final List<ClientSettingsDTO> settings_data;
        final OrderDataDTO order_data;

        public ListClientSettingsDTO(List<ClientSettingsDTO> settings_data, OrderDataDTO order_data) {
            this.settings_data = settings_data;
            this.order_data = order_data;
        }

        public List<ClientSettingsDTO> getSettings() {
            return settings_data;
        }

        public OrderDataDTO getOrderData() {
            return order_data;
        }
    }

    public static class ClientSettingsWithoutOrderDTO {
        final List<ClientSettingsDTO> settings_data;

        public ClientSettingsWithoutOrderDTO(List<ClientSettingsDTO> settings_data) {
            this.settings_data = settings_data;
        }

        public List<ClientSettingsDTO> getSettings() {
            return settings_data;
        }
    }

    public static class ClientSettingsOrderDTO {
        final OrderDataDTO order_data;

        public ClientSettingsOrderDTO(OrderDataDTO order_data) {
            this.order_data = order_data;
        }

        public OrderDataDTO getOrder_data() {
            return order_data;
        }
    }

    public static class OrderDataDTO {
        final List<OrderDTO> settings;
        final int timestamp;

        public OrderDataDTO(List<OrderDTO> settings, int timestamp) {
            this.settings = settings;
            this.timestamp = timestamp;
        }

        public List<OrderDTO> getSettings() {
            return settings;
        }

        public int getTimestamp() {
            return timestamp;
        }
    }

    public static class OrderDTO {
        final String jid;
        final int order;

        public OrderDTO(String jid, int order) {
            this.jid = jid;
            this.order = order;
        }

        public String getJid() {
            return jid;
        }

        public int getOrder() {
            return order;
        }
    }

    public static class ClientSettingsDTO {
        final String jid;
        final SettingsValuesDTO settings;
        final int timestamp;

        public ClientSettingsDTO(String jid, SettingsValuesDTO settings, int timestamp) {
            this.jid = jid;
            this.settings = settings;
            this.timestamp = timestamp;
        }

        public String getJid() {
            return jid;
        }

        public SettingsValuesDTO getSettings() {
            return settings;
        }

        public int getTimestamp() {
            return timestamp;
        }
    }

    public static class SettingsValuesDTO {
        final int order;
        final String color;
        final String token;
        final String username;

        public SettingsValuesDTO(int order, String color, String token, String username) {
            this.order = order;
            this.color = color;
            this.token = token;
            this.username = username;
        }

        public int getOrder() {
            return order;
        }

        public String getColor() {
            return color;
        }

        public String getToken() {
            return token;
        }

        public String getUsername() {
            return username;
        }
    }
}
