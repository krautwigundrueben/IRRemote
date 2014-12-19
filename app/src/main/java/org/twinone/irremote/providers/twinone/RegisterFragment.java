package org.twinone.irremote.providers.twinone;

import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.twinone.androidlib.net.HttpJson;
import org.twinone.androidlib.net.HttpJson.ExceptionListener;
import org.twinone.androidlib.net.HttpJson.ResponseListener;
import org.twinone.irremote.Constants;
import org.twinone.irremote.R;
import org.twinone.irremote.compat.ToolbarActivity;
import org.twinone.irremote.providers.twinone.RegisterFragment.RegisterReq;
import org.twinone.irremote.providers.twinone.RegisterFragment.RegisterResp;

public class RegisterFragment extends Fragment implements
        OnFocusChangeListener, OnClickListener, TextWatcher,
        ResponseListener<RegisterReq, RegisterResp>,
        ExceptionListener<RegisterReq, RegisterResp> {

    private static final int GET_ACCOUNT = 0xCACA;

    private static final int STATUS_OK = 0;
    private static final int STATUS_USER_SHORT = 1;
    private static final int STATUS_USER_TAKEN = 2;
    private static final int STATUS_PASS_SHORT = 4;
    private static final int STATUS_EMAIL_INVALID = 8;
    private static final int STATUS_EMAIL_TAKEN = 16;
    private static final int STATUS_USER_LONG = 32;
    private static final int STATUS_USER_INVALID = 64;
    private static final int STATUS_UNKNOWN_ERR = 128;
    private static final int STATUS_DB_FAILED = 256;

    private TextView mMessage;
    private EditText mUsername;
    private EditText mPwd;
    private EditText mPwdConfirm;
    private EditText mEmail;
    private Button mSubmit;
    private LinearLayout mForm;
    private AlertDialog mDialog;
    private boolean mHasErrors = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private ActionBar getSupportActionBar() {
        return ((ToolbarActivity) getActivity()).getSupportActionBar();
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_register, null);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getActivity().setTitle(R.string.title_create_account);

        mForm = (LinearLayout) root.findViewById(R.id.reg_form);
        mMessage = (TextView) root.findViewById(R.id.reg_message);
        UserInfo ui = UserInfo.load(getActivity());
        if (ui != null && ui.username != null) {
            if (ui.access_token == null)
                mMessage.setText(getString(R.string.reg_err_already_registered_novfy, ui.username));
            else
                mMessage.setText(getString(R.string.reg_err_already_registered, ui.username));
            mForm.setVisibility(View.GONE);
            return root;
        }

        mUsername = (EditText) root.findViewById(R.id.reg_username);
        mPwd = (EditText) root.findViewById(R.id.reg_pwd);
        mPwdConfirm = (EditText) root.findViewById(R.id.reg_pwd_confirm);
        mEmail = (EditText) root.findViewById(R.id.reg_email);
        mEmail.setOnFocusChangeListener(this);
        mEmail.setOnClickListener(this);
        mSubmit = (Button) root.findViewById(R.id.reg_submit);
        mSubmit.setOnClickListener(this);

        mUsername.addTextChangedListener(this);
        mPwd.addTextChangedListener(this);
        mPwdConfirm.addTextChangedListener(this);
        mEmail.addTextChangedListener(this);

        return root;
    }

    @Override
    public void onFocusChange(View v, boolean hasFocus) {
        Log.i("a", "OnFocusChange");
        if (hasFocus && mEmail.getText().toString().isEmpty()) {
            startAccountChooser();
        }
    }

    private void startAccountChooser() {
        Log.i("RegisterActivity", "startAccountChooser()");
        Intent intent = AccountManager.newChooseAccountIntent(null, null,
                new String[]{"com.google"}, true, null, null, null, null);
        startActivityForResult(intent, GET_ACCOUNT);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.reg_submit:
                checkFieldsAndSubmit();
                break;
            case R.id.reg_email:
                startAccountChooser();
                break;
        }
    }

    private void addError(int resId, TextView... vs) {
        addError(getString(resId), vs);
    }

    private void addError(String err, TextView... vs) {
        int error = getResources().getColor(R.color.material_red_300);
        Log.d("", "Error: " + err + " , mHasErrors:" + mHasErrors);
        if (!mHasErrors)
            mMessage.setText(err);
        else
            mMessage.append("\n\n" + err);

        mMessage.setTextColor(error);
        mHasErrors = true;

        if (vs != null) {
            for (TextView v : vs)
                v.setTextColor(error);
        }
    }

    private boolean areAllFilledIn(TextView... tvs) {
        for (TextView tv : tvs) {
            if (tv.getText().toString().isEmpty()) {
                addError(R.string.reg_err_all_fields, (TextView[]) null);
                return false;
            }
        }
        return true;
    }

    private void checkFieldsAndSubmit() {

        resetErrors();

        if (!areAllFilledIn(mUsername, mEmail, mPwd, mPwdConfirm))
            return;

        if (mUsername.getText().length() < 5) {
            addError(R.string.reg_err_user_short, mUsername);
        }

        if (mPwd.getText().length() < 8) {
            addError(R.string.reg_err_pwd_short, mPwd);
        }

        if (!mPwd.getText().toString().equals(mPwdConfirm.getText().toString())) {
            addError(R.string.reg_err_pwd_match, mPwd, mPwdConfirm);
        }
        if (!mHasErrors)
            submit();
    }

    private void submit() {
        if (mDialog != null && mDialog.isShowing())
            return;
        if (!isAdded()) {
            Log.d("RegisterFragment", "!isAdded()");
            return;
        }
        AlertDialog.Builder ab = new AlertDialog.Builder(getActivity());
        ab.setTitle(R.string.reg_dlgtit_loading);
        ab.setMessage("");
        ab.setCancelable(false);
        ab.setPositiveButton(android.R.string.ok, null);
        mDialog = ab.show();

        RegisterReq req = new RegisterReq();
        req.username = mUsername.getText().toString();
        req.password = mPwd.getText().toString();
        req.email = mEmail.getText().toString();

        HttpJson<RegisterReq, RegisterResp> hj = new HttpJson<>(
                RegisterResp.class, Constants.URL_REGISTER);
        hj.execute(req, this, this);

    }

    @Override
    public void onServerResponse(int statusCode, RegisterReq req,
                                 RegisterResp resp) {
        if (resp.status == STATUS_OK) {
            UserInfo ui = new UserInfo();
            ui.username = req.username;
            ui.email = req.email;
            ui.save(getActivity());

            mDialog.setTitle(R.string.reg_dlgtit_ok);
            mDialog.setMessage(getString(R.string.reg_dlgmsg_ok));
        } else {
            mMessage.setText(R.string.reg_msg_srv_err);
            if ((STATUS_USER_SHORT & resp.status) != 0)
                addError(R.string.reg_err_user_short, mUsername);
            if ((STATUS_USER_TAKEN & resp.status) != 0)
                addError(getString(R.string.reg_err_user_taken, req.username),
                        mUsername);
            if ((STATUS_PASS_SHORT & resp.status) != 0)
                addError(R.string.reg_err_pass_short, mPwd);
            if ((STATUS_EMAIL_INVALID & resp.status) != 0)
                addError(R.string.reg_err_email_invalid, mEmail);
            if ((STATUS_EMAIL_TAKEN & resp.status) != 0)
                addError(R.string.reg_err_email_taken, mEmail);
            if ((STATUS_USER_LONG & resp.status) != 0)
                addError(R.string.reg_err_user_long, mUsername);
            if ((STATUS_USER_INVALID & resp.status) != 0)
                addError(R.string.reg_err_user_invalid, mUsername);
            if ((STATUS_UNKNOWN_ERR & resp.status) != 0)
                addError(R.string.reg_err_unknown, (TextView[]) null);
            if ((STATUS_DB_FAILED & resp.status) != 0)
                addError(R.string.reg_err_unknown, (TextView[]) null);

            if (mDialog.isShowing())
                mDialog.dismiss();
        }

    }

    @Override
    public void onServerException(Exception e) {
        Log.e("RegisterActivity", "Exception", e);
        if (mDialog != null && mDialog.isShowing())
            mDialog.dismiss();
        addError(R.string.network_error, (TextView[]) null);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK && requestCode == GET_ACCOUNT) {
            String email = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME);
            mEmail.setText(email);
        }
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        resetErrors();
    }

    private void resetErrors() {
        if (!mHasErrors)
            return;
        int def = getResources().getColor(
                R.color.abc_primary_text_material_dark);

        mMessage.setText(R.string.reg_header);

        mMessage.setTextColor(def);
        mUsername.setTextColor(def);
        mEmail.setTextColor(def);
        mPwd.setTextColor(def);
        mPwdConfirm.setTextColor(def);
        mHasErrors = false;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,
                                  int after) {
    }

    @Override
    public void afterTextChanged(Editable s) {

    }

	/*
     * Unused methods...
	 */

    public static class RegisterReq {
        public String username;
        public String email;
        public String password;
    }

    public static class RegisterResp {
        int status;
    }

}
