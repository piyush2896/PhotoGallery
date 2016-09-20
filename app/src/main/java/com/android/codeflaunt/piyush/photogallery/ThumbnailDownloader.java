package com.android.codeflaunt.piyush.photogallery;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.HandlerThread;
import android.os.Message;
import android.support.v4.util.LruCache;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import android.os.Handler;

/**
 * Created by Piyush on 17-Mar-16.
 */
public class ThumbnailDownloader<T> extends HandlerThread {

    private static final String TAG = "ThumbnailDownloader";
    private static final int MESSAGE_DOWNLOAD = 0;

    private Handler mRequestHandler;
    private ConcurrentHashMap<T, String> mRequestMap = new ConcurrentHashMap<>();
    private Handler mResponseHandler;
    private LruCache<String, Bitmap> mCache;
    private ThumbnailDownloaderListener<T> mTThumbnailDownloaderListener;
    private boolean mHasQuit;

    public interface ThumbnailDownloaderListener<T>{
        void onThumbnailDownloaded(T target, Bitmap thumbnail);
    }

    public void setThumbnailDownloaderListener(ThumbnailDownloaderListener<T> listener){
        mTThumbnailDownloaderListener = listener;
    }

    public ThumbnailDownloader(Handler responseHandler){
        super(TAG);
        mResponseHandler = responseHandler;

        final int maxMemorySize = (int) Runtime.getRuntime().maxMemory() / 1024;
        final int maxCacheSize = maxMemorySize / 5;

        mCache = new LruCache<String, Bitmap>(maxCacheSize){
            protected int sizeOf(String key, Bitmap value){
                return value.getByteCount()/1024;
            }
        };
    }

    @Override
    public boolean quit() {
        mHasQuit = true;
        return super.quit();
    }

    protected void onLooperPrepared(){
        mRequestHandler = new Handler(){

            public void handleMessage(Message msg){
                if(msg.what == MESSAGE_DOWNLOAD){
                    T target = (T) msg.obj;
                    Log.i(TAG, "Got a request for url : " + mRequestMap.get(target));
                    handleRequest(target);
                }
            }
        };
    }

    public void queueThumbnail(T target, String url){
        Log.i(TAG, "Got a url : " + url);

        if(url == null){
            mRequestMap.remove(target);
        }else {
            mRequestMap.put(target, url);
            mRequestHandler.obtainMessage(MESSAGE_DOWNLOAD, target)
                    .sendToTarget();
        }
    }


    public void clearQueue(){
        mRequestHandler.removeMessages(MESSAGE_DOWNLOAD);
        clearCache();
    }

    private void handleRequest(final T target){
        final String url = mRequestMap.get(target);
        if (url == null)
            return;
        final Bitmap bitmap = downloadImage(url);
        mResponseHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mRequestMap.get(target) != url || mHasQuit) {
                    return;
                }
                mRequestMap.remove(target);
                mTThumbnailDownloaderListener.onThumbnailDownloaded(target, bitmap);
            }
        });
    }

    private void setBitmapInCache(String key, Bitmap value){
        mCache.put(key, value);
    }

    private Bitmap getBitmapFromCache(String key){
        return mCache.get(key);
    }

    private Bitmap downloadImage(String key){
        Bitmap bitmap;
        bitmap = getBitmapFromCache(key);

        Log.i(TAG, "Bitmap " + bitmap);
        if(bitmap != null){
            Log.i(TAG, "Got a url in cache : " + key);
            return bitmap;
        }

        try{
            byte[] bitmapBytes = new FlickrFetchr().getUrlBytes(key);
            bitmap = BitmapFactory
                    .decodeByteArray(bitmapBytes, 0, bitmapBytes.length);
            setBitmapInCache(key, bitmap);
            return bitmap;
        }catch (IOException ioe){
            Log.e(TAG, "Error while downloading", ioe);
            return null;
        }
    }

    private void clearCache(){
        mCache.evictAll();
    }
}
