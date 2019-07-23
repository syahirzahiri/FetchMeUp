package com.urbantechies.fetch_me_up.passengers;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.urbantechies.fetch_me_up.R;
import com.urbantechies.fetch_me_up.model.Driver;
import com.urbantechies.fetch_me_up.model.Passenger;
import com.urbantechies.fetch_me_up.ui.logindriver;
import com.urbantechies.fetch_me_up.ui.signupdriver;

import static android.text.TextUtils.isEmpty;
import static com.urbantechies.fetch_me_up.util.Check.doStringsMatch;

public class signuppassenger extends AppCompatActivity implements View.OnClickListener {

    //widgets
    private EditText mEmail, mPassword, mConfirmPassword, mPhoneNo, mMatricID, mFirstName, mLastName;
    private ProgressBar mProgressBar;

    //vars
    private FirebaseFirestore mDb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signuppassenger);

        mFirstName = findViewById(R.id.drv_firstname);
        mLastName = findViewById(R.id.drv_lastname);
        mEmail = findViewById(R.id.drv_email);
        mPassword = findViewById(R.id.drv_password);
        mConfirmPassword = findViewById(R.id.drv_confirm_password);
        mPhoneNo = findViewById(R.id.drv_phone);
        mMatricID = findViewById(R.id.drv_matric);
        mProgressBar = findViewById(R.id.progressBarRegister);

        findViewById(R.id.btn_register_drv).setOnClickListener(this);

        mDb = FirebaseFirestore.getInstance();

        hideSoftKeyboard();

    }

    private static final String TAG = "RegisterActivity";

    /**
     * Register a new email and password to Firebase Authentication
     * @param email
     * @param password
     */
    public void registerNewEmail(final String email, String password, final String phoneNo, final String matricID, final String firstName, final String lastName){

        showDialog();

        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        Log.d(TAG, "createUserWithEmail:onComplete:" + task.isSuccessful());

                        if (task.isSuccessful()){
                            Log.d(TAG, "onComplete: AuthState: " + FirebaseAuth.getInstance().getCurrentUser().getUid());

                            //insert some default data
                            Passenger passenger = new Passenger();
                            passenger.setFirst_name(firstName);
                            passenger.setLast_name(lastName);
                            passenger.setEmail(email);
                            passenger.setUsername(email.substring(0, email.indexOf("@")));
                            passenger.setUser_id(FirebaseAuth.getInstance().getUid());
                            passenger.setPhone_no(phoneNo);
                            passenger.setMatric_id(matricID);

                            FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                                    .build();
                            mDb.setFirestoreSettings(settings);

                            DocumentReference newUserRef = mDb
                                    .collection(getString(R.string.collection_passengers))
                                    .document(FirebaseAuth.getInstance().getUid());

                            newUserRef.set(passenger).addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> task) {
                                    hideDialog();

                                    if(task.isSuccessful()){
                                        redirectLoginScreen();
                                    }else{
                                        View parentLayout = findViewById(android.R.id.content);
                                        Snackbar.make(parentLayout, "Something went wrong.", Snackbar.LENGTH_SHORT).show();
                                    }
                                }
                            });

                        }
                        else {
                            View parentLayout = findViewById(android.R.id.content);
                            Snackbar.make(parentLayout, "Something went wrong.", Snackbar.LENGTH_SHORT).show();
                            hideDialog();
                        }

                        // ...
                    }
                });
    }

    /**
     * Redirects the user to the login screen
     */
    private void redirectLoginScreen(){
        Log.d(TAG, "redirectLoginScreen: redirecting to login screen.");

        Intent intent = new Intent(signuppassenger.this, loginpassenger.class);
        startActivity(intent);
        finish();
    }


    private void showDialog(){
        mProgressBar.setVisibility(View.VISIBLE);

    }

    private void hideDialog(){
        if(mProgressBar.getVisibility() == View.VISIBLE){
            mProgressBar.setVisibility(View.INVISIBLE);
        }
    }

    private void hideSoftKeyboard(){
        this.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()){
            case R.id.btn_register_drv:{
                Log.d(TAG, "onClick: attempting to register.");

                //check for null valued EditText fields
                if(!isEmpty(mEmail.getText().toString())
                        && !isEmpty(mPassword.getText().toString())
                        && !isEmpty(mConfirmPassword.getText().toString())){

                    //check if passwords match
                    if(doStringsMatch(mPassword.getText().toString(), mConfirmPassword.getText().toString())){

                        //Initiate registration task
                        registerNewEmail(mEmail.getText().toString(), mPassword.getText().toString(), mPhoneNo.getText().toString(), mMatricID.getText().toString(), mFirstName.getText().toString(), mLastName.getText().toString());
                    }else{
                        Toast.makeText(signuppassenger.this, "Passwords do not Match", Toast.LENGTH_SHORT).show();
                    }

                }else{
                    Toast.makeText(signuppassenger.this, "You must fill out all the fields", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

}
