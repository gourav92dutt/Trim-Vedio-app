package com.riziliant.myapplication;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.MediaController;
import android.widget.Toast;
import android.widget.VideoView;

import java.io.File;
import java.io.IOException;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private VideoView videoView;
    private MediaController mediaController;
    private EditText editText;

    ActivityResultLauncher<Intent> takeOrSelectVideoResultLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK &&
                        result.getData() != null) {
                    Intent data = result.getData();
                    //check video duration if needed
           /*        if (TrimmerUtils.getDuration(this,data.getData())<=30){
                    Toast.makeText(this,"Video should be larger than 30 sec",Toast.LENGTH_SHORT).show();
                    return;
                }*/
                    if (data.getData() != null) {
                        if (isValid(data.getData())) {
                            openTrimActivity(data.getData());
                        }
                    } else {
                        Toast.makeText(this, "video uri is null", Toast.LENGTH_SHORT).show();
                    }
                }
            });

    private boolean isValid(Uri uri) {
        if (VideoUtils.getDurationMillis(this, uri) < Double.parseDouble(editText.getText().toString())*60*1000) {
            Toast.makeText(this, "Enter Time is more then video time", Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    private void openTrimActivity(Uri uri) {
        try {
            VideoUtils.startTrim(this, VideoUtils.getRealPath(this, uri), getFileName(uri), 0,
                    Integer.parseInt(editText.getText().toString()) * 60 * 1000, true, true, new VideoUtils.Listener() {
                        @Override
                        public void onStart() {
                            findViewById(R.id.progress_bar).setVisibility(View.VISIBLE);

                        }

                        @Override
                        public void onProgress(Float value) {

                        }

                        @Override
                        public void onComplete(String path) {
                            if (videoView.isPlaying()) {
                                videoView.stopPlayback();
                            }
                            findViewById(R.id.progress_bar).setVisibility(View.GONE);
                            videoView.setMediaController(mediaController);
                            videoView.setVideoURI(Uri.parse(path));
                            videoView.requestFocus();
                            videoView.start();

                            videoView.setOnPreparedListener(mediaPlayer -> {
                                mediaController.setAnchorView(videoView);
                            });
                        }

                        @Override
                        public void onError(String msg) {
                            findViewById(R.id.progress_bar).setVisibility(View.GONE);
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String getFileName(Uri uri) {
        String path = getExternalFilesDir("TrimmedVideo").getPath();
        Calendar calender = Calendar.getInstance();
        String fileDateTime = calender.get(Calendar.YEAR) + "_" +
                calender.get(Calendar.MONTH) + "_" +
                calender.get(Calendar.DAY_OF_MONTH) + "_" +
                calender.get(Calendar.HOUR_OF_DAY) + "_" +
                calender.get(Calendar.MINUTE) + "_" +
                calender.get(Calendar.SECOND);
        String fName = "trimmed_video_";
        File newFile = new File(path + File.separator +
                (fName) + fileDateTime + "." + getFileExtension(this, uri));
        return String.valueOf(newFile);
    }

    public static String getFileExtension(Context context, Uri uri) {
        try {
            String extension;
            if (uri.getScheme() != null && uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
                final MimeTypeMap mime = MimeTypeMap.getSingleton();
                extension = mime.getExtensionFromMimeType(context.getContentResolver().getType(uri));
            } else
                extension = MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(new File(uri.getPath())).toString());
            return (extension == null || extension.isEmpty()) ? ".mp4" : extension;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "mp4";
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        videoView = findViewById(R.id.video_view);
        editText = findViewById(R.id.edit_time);
        mediaController = new MediaController(this);
        findViewById(R.id.btn_default_trim).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (editText.getText().length() != 0) {
                    if (checkCamStoragePer()) {
                        showVideoOptions();
                    }
                } else {
                    Toast.makeText(MainActivity.this, "Enter Time", Toast.LENGTH_LONG).show();
                }
            }
        });
    }

    public void showVideoOptions() {
        AlertDialog.Builder builder1 = new AlertDialog.Builder(this);
        builder1.setMessage("Options");
        builder1.setCancelable(true);

        builder1.setPositiveButton(
                "Take Video",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        captureVideo();
                        dialog.cancel();
                    }
                });

        builder1.setNegativeButton(
                "Open Gallery",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        openVideo();
                        dialog.cancel();
                    }
                });

        AlertDialog alert11 = builder1.create();
        alert11.show();
    }


    public void captureVideo() {
        try {
            Intent intent = new Intent("android.media.action.VIDEO_CAPTURE");
            intent.putExtra("android.intent.extra.durationLimit", 30);
            takeOrSelectVideoResultLauncher.launch(intent);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void openVideo() {
        try {
            Intent intent = new Intent();
            intent.setType("video/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            takeOrSelectVideoResultLauncher.launch(Intent.createChooser(intent, "Select Video"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (isPermissionOk(grantResults))
            showVideoOptions();
    }


    private boolean checkCamStoragePer() {
        return checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.CAMERA);
    }

    private boolean checkPermission(String... permissions) {
        boolean allPermitted = false;
        for (String permission : permissions) {
            allPermitted = (ContextCompat.checkSelfPermission(this, permission)
                    == PackageManager.PERMISSION_GRANTED);
            if (!allPermitted)
                break;
        }
        if (allPermitted)
            return true;
        ActivityCompat.requestPermissions(this, permissions,
                220);
        return false;
    }

    private boolean isPermissionOk(int... results) {
        boolean isAllGranted = true;
        for (int result : results) {
            if (PackageManager.PERMISSION_GRANTED != result) {
                isAllGranted = false;
                break;
            }
        }
        return isAllGranted;
    }


}