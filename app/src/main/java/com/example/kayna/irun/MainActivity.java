package com.example.kayna.irun;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.widget.EditText;
import android.widget.TextView;

//https://stackoverflow.com/questions/12559461/how-to-show-progress-barcircle-in-an-activity-having-a-listview-before-loading
public class MainActivity extends AppCompatActivity {
    Button buttonStart;
    Button buttonStop;
    Button buttonShow;
    ProgressDialog pd;
    private Date startDate;
    private Date endDate;
    private String username;
    private String password;
    private List<Location> finalCoordinates;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);
        buttonShow = (Button) findViewById(R.id.buttonShow);
        buttonStart = (Button) findViewById(R.id.buttonStart);
        buttonStop = (Button) findViewById(R.id.buttonStop);
        pd = new ProgressDialog(this);
        firebaseAuth = FirebaseAuth.getInstance();

        buttonShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intentShow = new Intent(MainActivity.this, ShowActivity.class);
                intentShow.putExtra("coordinates", new ArrayList(finalCoordinates));
                startActivity(intentShow);
            }
        });

        storeCoordinatesAfterFinishDownloading();


    }

    private void storeCoordinatesAfterFinishDownloading() {
        //listen to broadcast intent which are about downloading coordinates
        //https://stackoverflow.com/questions/35278161/cannot-pass-data-from-asynctask-to-my-activity
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                System.out.println("Received intent");
                pd.dismiss();
                //Check if error occurred during downloading
                Exception error = (Exception) intent.getSerializableExtra("error");
                if (error != null) {
                    buttonStop.setEnabled(true);
                    //Toast.makeText(MainActivity.this, "Error occured. Try again!", Toast.LENGTH_LONG).show();
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                    builder.setTitle("coordinates unavailable")
                            .setMessage(error.getMessage())
                            .setNegativeButton("Abort", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    // do nothing
                                }
                            })
                            .setIcon(android.R.drawable.ic_dialog_alert)
                            .show();
                } else {
                    List<Location> allCoordinates= (ArrayList<Location>) intent.getSerializableExtra("coordinates"); //download all coordinates from thingsee website
                    List<Location> filteredCoordinates = filterDates(startDate, endDate, allCoordinates);//filter the coordinates by taking the ones between startDate and stopDate
                    finalCoordinates = sortByTime(filteredCoordinates); //sort the coordinates by time from the oldest to the latest
                    System.out.println("filtered and sorted coordinates " + finalCoordinates);
                    buttonShow.setEnabled(true);
                    buttonStart.setEnabled(true);
                }
            }
        }, new IntentFilter(GetCoordinatesTask.DOWNLOAD_COORDINATES_ACTION_NAME));
    }

    /**
     * Called when the user touches the start button
     */
    public void setStartDate(View view) {
        startDate = new Date();  //set starting time
        buttonStart.setEnabled(false); //after it's clicked, it is disabled
        buttonStop.setEnabled(true); //enable STOP button
        buttonShow.setEnabled(false); //disable SHOW button because there is no stop-time yet
        endDate = null; //reset stop-time
    }


    /**
     * Called when the user touches the stop button
     */
    public void setStopDate(View view) {
        buttonStop.setEnabled(false);//disable STOP button because data downloading starts when STOP button is clicked.
        buttonStart.setEnabled(false); //disable START button
        buttonShow.setEnabled(false);//disable SHOW button
        endDate = new Date(); //set stop-time

        if(username == null || password == null){
            askForCredentialsAndDownloadCoordinates();
        }else {

            downloadCoordinates(); // call downloadCoordinates method to download data from thingsee website
        }



    }

    private void showDownloadInProgress() {
        //https://stackoverflow.com/questions/5442183/using-the-animated-circle-in-an-imageview-while-loading-stuff
        //https://github.com/codepath/android_guides/wiki/Using-DialogFragment#displaying-a-progressdialog

        pd.setTitle("Downloading Coordinates..."); // show downloading in progress
        pd.setMessage("Please wait.");
        pd.setCancelable(false);
        pd.show();
    }


    private void downloadCoordinates() {
        //Toast.makeText(this, "Downloading Coordinates", Toast.LENGTH_LONG).show();
        GetCoordinatesTask getCoordinatesTask = new GetCoordinatesTask(MainActivity.this);//instantiate AsyncTask for downloading data
        getCoordinatesTask.execute(username, password); //start AsyncTask
        showDownloadInProgress();
    }

    private List<Location> sortByTime(List<Location> locations) {

        //sort List<Location> according to dates using Date.compareTo()
        Comparator<Location> comparator = new Comparator<Location>() {
            @Override
            //returns  a negative integer, zero, or a positive integer as the first argument is less than, equal to, or greater than the second.
            public int compare(Location location1, Location location2) {
                Date date1 = new Date(location1.getTime()); //
                Date date2 = new Date(location2.getTime());
                return date1.compareTo(date2);//compare two dates
            }
        };

        Collections.sort(locations, comparator);//rearrange the List<Location> according to the value returned by comparator.compare
        return locations;
    }



    private void askForCredentialsAndDownloadCoordinates() {
        // get prompts.xml view
        LayoutInflater li = LayoutInflater.from(this);
        View promptsView = li.inflate(R.layout.credentials_dialog, null);

        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        // set prompts.xml to alertdialog builder
        alertDialogBuilder.setView(promptsView);

        final TextView dialogMsg      = (TextView) promptsView.findViewById(R.id.textViewDialogMsg);
        final EditText dialogUsername = (EditText) promptsView.findViewById(R.id.editTextDialogUsername);
        final EditText dialogPassword = (EditText) promptsView.findViewById(R.id.editTextDialogPassword);

        dialogMsg.setText("Enter your thingsee account credentials to download coordinates");
        dialogUsername.setText(username);
        dialogPassword.setText(password);

        // Configure alert dialog ( text, buttons, actions when buttons pressed)
        alertDialogBuilder
                .setCancelable(false)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                // get user input and set it to result
                                username = dialogUsername.getText().toString();
                                password = dialogPassword.getText().toString();

                                SharedPreferences prefPut = getSharedPreferences("Credentials", Activity.MODE_PRIVATE);
                                SharedPreferences.Editor prefEditor = prefPut.edit();
                                prefEditor.putString("username", username);
                                prefEditor.putString("password", password);
                                prefEditor.commit();
                                downloadCoordinates();
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,int id) {
                                dialog.cancel();
                            }
                        });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

    //filter the coordinates according to time between startDate and StopDate
    public List<Location> filterDates(Date startDate, Date stopDate, List<Location> locations){
        List<Location> filteredLocation = new ArrayList<>();
        for (Location location:locations) {
            Date time = new Date(location.getTime());
            if(time.compareTo(startDate) >= 0 && time.compareTo(stopDate) <= 0){ //check each coordinate if it is between the startDate and stopDate
                filteredLocation.add(location);
            }
        }
        return filteredLocation;
    }

    //logs out
    private void Logout(){
        firebaseAuth.signOut();
        finish();
        startActivity(new Intent(this, LoginActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
// log out button from the menu
        switch(item.getItemId()){
            case R.id.logOutMenu:{
                Logout();
                break;
            }
            case R.id.homeMenu:
                startActivity(new Intent(this, MainActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}



