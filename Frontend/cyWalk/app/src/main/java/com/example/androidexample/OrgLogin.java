package com.example.androidexample;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

/**
 * The organizations login page that they see before logging in
 * */
public class OrgLogin extends AppCompatActivity {

    private EditText usernameEditText;  // define username edittext variable
    private EditText passwordEditText;  // define password edittext variable
    private EditText companyUsernameEditText;  // define username edittext variable
    private EditText companyPasswordEditText;
    private TextView errorMsg;
    private Button loginButton;         // define login button variable
    private static String URL_LOGIN = "http://coms-3090-072.class.las.iastate.edu:8080/admin/login";
    private static String URL_SIGNUP = "http://coms-3090-072.class.las.iastate.edu:8080/signup/organization";
    private String userKey = "";
    private String orgId="";
    private String username;
    private String password;
    private String orgUsername;
    private Button signUpButton;        // define signup button variable
    private TextView userSwitch;

    /**
     *creates the page for the organization users to be able to login in or create their organization
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.orglogin);            // link to Login activity XML

        /* initialize UI elements */
        usernameEditText = findViewById(R.id.login_username_edt);
        passwordEditText = findViewById(R.id.login_password_edt);
        companyUsernameEditText = findViewById(R.id.company_username_edit);
        loginButton = findViewById(R.id.login_login_btn);
        errorMsg = findViewById(R.id.errorMsg);
        signUpButton = findViewById(R.id.login_signup_btn);
        userSwitch = findViewById(R.id.switchUserView);

        /* click listener on login button pressed */
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                /* grab strings from user inputs */
                username = usernameEditText.getText().toString();
                password = passwordEditText.getText().toString();
                orgUsername = companyUsernameEditText.getText().toString();

                try {
                    makeLoginReq();
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        /* click listener on sign up button pressed */
        // Sign up is post
        signUpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                /* grab strings from user inputs */
                username = usernameEditText.getText().toString();
                password = passwordEditText.getText().toString();
                orgUsername = companyUsernameEditText.getText().toString();

                try {
                    makeSignUpReq();;
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        });

        userSwitch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(OrgLogin.this, Login.class);
                startActivity(intent);
            }
        });
    }

    /**
     * attempts to log the organization user in using their inputted username and password. If the organization doesn't exist
     * or the credentials are wrong will throw an error and let the user know that something went wrong
     */
    private void makeLoginReq() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("organizationName", orgUsername);
        jsonObject.put("adminName", username);
        jsonObject.put("password", password);

        final String requestBody = jsonObject.toString();

        //errorMsg.setText(requestBody);

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(
                Request.Method.PUT, URL_LOGIN, jsonObject,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("Volley Response", response.toString());
                        try {
                            // Parse JSON object data
                            userKey = response.getString("id");
                            orgId = response.getString("orgId");
                            if(!userKey.isEmpty()) {
                                Intent intent = new Intent(OrgLogin.this, OrgSetGoals.class);
                                intent.putExtra("id", userKey);
                                intent.putExtra("orgId",orgId);
                                //errorMsg.setText("success " + jsonObject);
                                startActivity(intent);
                            } else {
                                //errorMsg.setText("failed " + userKey);
                                errorMsg.setText("Error invalid username/password");
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Volley Error", error.toString());
                        errorMsg.setText("an error has occured please try again later");
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
//                headers.put("Authorization", "Bearer YOUR_ACCESS_TOKEN");
//                headers.put("Content-Type", "application/json");
                return headers;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
//                params.put("param1", "value1");
//                params.put("param2", "value2");
                return params;
            }
        };

        // Adding request to request queue
        VolleySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsonObjReq);
    }

    /**
     * attempts to sign the user up using the inputted credentials if all is good moves the organization on to the organizations landing page
     * if there is an issue like the organization already exists it will throw an error and let the user know that something went wrong
     */
    private void makeSignUpReq() throws JSONException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("organizationName", orgUsername);
        jsonObject.put("adminName", username);
        jsonObject.put("password", password);

        //final String requestBody = jsonObject.toString();
        //errorMsg.setText(requestBody);

        JsonObjectRequest jsonObjReq = new JsonObjectRequest(
                Request.Method.POST, URL_SIGNUP, jsonObject,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        Log.d("Volley Response", response.toString());
                        try {
                            // Parse JSON object data
                            userKey = response.getString("id");
                            orgId = response.getString("orgId");
                            if(!userKey.isEmpty()) {
                                Intent intent = new Intent(OrgLogin.this, OrgSetGoals.class);
                                intent.putExtra("id", userKey);
                                intent.putExtra("orgId",orgId);
                                //errorMsg.setText("success " + jsonObject);
                                startActivity(intent);
                            }

                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Log.e("Volley Error", error.toString());
                        //errorMsg.setText(error.toString());
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                HashMap<String, String> headers = new HashMap<String, String>();
//                headers.put("Authorization", "Bearer YOUR_ACCESS_TOKEN");
//                headers.put("Content-Type", "application/json");
                return headers;
            }

            @Override
            protected Map<String, String> getParams() {
                Map<String, String> params = new HashMap<String, String>();
//                params.put("param1", "value1");
//                params.put("param2", "value2");
                return params;
            }
        };

        // Adding request to request queue
        VolleySingleton.getInstance(getApplicationContext()).addToRequestQueue(jsonObjReq);
    }
}