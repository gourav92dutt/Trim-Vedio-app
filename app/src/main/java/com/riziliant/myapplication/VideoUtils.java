package com.riziliant.myapplication;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.loader.content.CursorLoader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class VideoUtils {

    private static final int DEFAULT_BUFFER_SIZE = 1024;


    public static void startTrim(Activity activity, String srcPath, String dstPath,
                                 int startMs, int endMs, Boolean useAudio,
                                 Boolean useVideo, VideoUtils.Listener listener)
            throws IOException {

        activity.runOnUiThread(new Runnable() {

            @SuppressLint("WrongConstant")
            @Override
            public void run() {
                listener.onStart();
            }
        });

    new Thread()
    {
        @Override
        public void run() {
            super.run();
            startTrimNow( activity,  srcPath,  dstPath,
             startMs,  endMs,  useAudio,
                     useVideo,  listener);
        }
    }.start();
    }
    private static void startTrimNow(Activity activity, String srcPath, String dstPath,
                                     int startMs, int endMs, Boolean useAudio,
                                     Boolean useVideo, VideoUtils.Listener listener)
    {
        MediaExtractor mediaExtractor = new MediaExtractor();
        try {
            mediaExtractor.setDataSource(srcPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
        int trackCount = mediaExtractor.getTrackCount();
        MediaMuxer mediaMuxer = null;
        try {
            mediaMuxer = new MediaMuxer(dstPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            e.printStackTrace();
        }
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);

        int bufferSize = -1;

        for (int i = 0; i < trackCount; i++) {

            MediaFormat format = mediaExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            Boolean selectCurrentTrack = false;
            if (mime.startsWith("audio/") && useAudio) {
                selectCurrentTrack = true;
            } else if (mime.startsWith("video/") && useVideo) {
                selectCurrentTrack = true;
            }
            if (selectCurrentTrack) {
                mediaExtractor.selectTrack(i);
                int dstIndex = mediaMuxer.addTrack(format);
                indexMap.put(i, dstIndex);

                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                    int newSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);

                    if (newSize > bufferSize) {
                        bufferSize = newSize;
                    } else
                        bufferSize = bufferSize;
                }
            }


        }


        if (bufferSize < 0) {
            bufferSize = DEFAULT_BUFFER_SIZE;
        }
        // Set up the orientation and starting time for extractor.
        MediaMetadataRetriever retrieverSrc = new MediaMetadataRetriever();
        retrieverSrc.setDataSource(srcPath);
        String degreesString = retrieverSrc.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        if (degreesString != null) {
            int degrees = Integer.parseInt(degreesString);
            if (degrees >= 0) {
                mediaMuxer.setOrientationHint(degrees);
            }
        }
        if (startMs > 0) {
            mediaExtractor.seekTo((startMs * 1000L), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }
        // Copy the samples from MediaExtractor to MediaMuxer. We will loop
        // for copying each sample and stop when we get to the end of the source
        // file or exceed the end time of the trimming.
        int offset = 0;
        int trackIndex;
        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int totalTimeMs = endMs - startMs;


        try {
            mediaMuxer.start();
            while (true) {
                bufferInfo.offset = offset;
                bufferInfo.size = mediaExtractor.readSampleData(dstBuf, offset);
                if (bufferInfo.size < 0) {


                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            listener.onComplete(dstPath);
                        }
                    });
                    bufferInfo.size = 0;
                    break;
                } else {
                    bufferInfo.presentationTimeUs = mediaExtractor.getSampleTime();
                    if (endMs > 0 && bufferInfo.presentationTimeUs > endMs * 1000) {

                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                listener.onComplete(dstPath);
                            }
                        });

                        break;
                    } else {
                        bufferInfo.flags = mediaExtractor.getSampleFlags();
                        trackIndex = mediaExtractor.getSampleTrackIndex();
                        mediaMuxer.writeSampleData(indexMap.get(trackIndex), dstBuf, bufferInfo
                        );

                        activity.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                listener.onProgress((float) ((bufferInfo.presentationTimeUs / 1000 - startMs) / totalTimeMs));
                            }
                        });


                        mediaExtractor.advance();
                    }
                }
            }
            mediaMuxer.stop();
        } catch (IllegalStateException exception) {

            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listener.onError("The source video file is malformed");
                }
            });


        } finally {
            mediaMuxer.release();
        }


    }

    interface Listener {
        void onStart();

        void onProgress(Float value);

        void onComplete(String dstPath);

        void onError(String msg);
    }


    public static String getRealPath(Context context, Uri fileUri) {
        String realPath;
        // SDK < API11
        if (Build.VERSION.SDK_INT < 11) {
            realPath = getRealPathFromURI_BelowAPI11(context, fileUri);
        }
        // SDK >= 11 && SDK < 19
        else if (Build.VERSION.SDK_INT < 19) {
            realPath = getRealPathFromURI_API11to18(context, fileUri);
        }
        // SDK > 19 (Android 4.4) and up
        else {
            realPath = getRealPathFromURI_API19(context, fileUri);
        }
        return realPath;
    }


    @SuppressLint("NewApi")
    public static String getRealPathFromURI_API11to18(Context context, Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        String result = null;

        CursorLoader cursorLoader = new CursorLoader(context, contentUri, proj, null, null, null);
        Cursor cursor = cursorLoader.loadInBackground();

        if (cursor != null) {
            int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            result = cursor.getString(column_index);
            cursor.close();
        }
        return result;
    }

    public static String getRealPathFromURI_BelowAPI11(Context context, Uri contentUri) {
        String[] proj = {MediaStore.Images.Media.DATA};
        Cursor cursor = context.getContentResolver().query(contentUri, proj, null, null, null);
        int column_index = 0;
        String result = "";
        if (cursor != null) {
            column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();
            result = cursor.getString(column_index);
            cursor.close();
            return result;
        }
        return result;
    }

    /**
     * Get a file path from a Uri. This will get the the path for Storage Access
     * Framework Documents, as well as the _data field for the MediaStore and
     * other file-based ContentProviders.
     *
     * @param context The context.
     * @param uri     The Uri to query.
     * @author paulburke
     */
    @SuppressLint("NewApi")
    public static String getRealPathFromURI_API19(final Context context, final Uri uri) {

        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;

        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    return Environment.getExternalStorageDirectory() + "/" + split[1];
                }

                // TODO handle non-primary volumes
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {

                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));

                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }

                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{
                        split[1]
                };

                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {

            // Return the remote address
            if (isGooglePhotosUri(uri))
                return uri.getLastPathSegment();

            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }

        return null;
    }


    public static String getDataColumn(Context context, Uri uri, String selection,
                                       String[] selectionArgs) {

        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {
                column
        };

        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(index);
            }
        } finally {
            if (cursor != null)
                cursor.close();
        }
        return null;
    }


    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    public static long getDurationMillis(Activity context, Uri videoPath) {
        try {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(context, videoPath);
            String time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            long timeInMillisec = Long.parseLong(time);
            retriever.release();
            return timeInMillisec;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }
}
