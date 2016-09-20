package com.android.codeflaunt.piyush.photogallery;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Piyush on 10-Mar-16.
 */
public class PhotoGalleryFragment extends Fragment {

    private static final String TAG = "PhotoGalleryFragment";

    //private int lastFetchedPage = 1;
    private RecyclerView mPhotoRecyclerView;
    private List<GalleryItem> mItems = new ArrayList<>();
    private ThumbnailDownloader<PhotoHolder> mThumbnailDownloader;
    private int[] mGridSizeArr;
    private int mNoOfGrids;
    private int mScreenWidthDp;
    private int mColumnWidth;
    private static int mStdColumns = 3;
    private GridLayoutExtended mManager;
    private ProgressBar mProgressBar;

    public static PhotoGalleryFragment newInstance(){
        return new PhotoGalleryFragment();
    }

    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
        updateItems();

        Handler responseHandler = new Handler();
        mThumbnailDownloader = new ThumbnailDownloader<>(responseHandler);
        mThumbnailDownloader.setThumbnailDownloaderListener(
                new ThumbnailDownloader.ThumbnailDownloaderListener<PhotoHolder>() {
                    @Override
                    public void onThumbnailDownloaded(PhotoHolder target, Bitmap thumbnail) {
                        if (!isAdded())
                            return;
                        Drawable drawable = new BitmapDrawable(getResources(), thumbnail);
                        target.bindGalleryItem(drawable);
                    }
                });
        mThumbnailDownloader.start();
        mThumbnailDownloader.getLooper();
        Log.i(TAG, "Background Thread Started");

    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                            Bundle savedInstanceState){
        setHasOptionsMenu(true);
        View view = inflater.inflate(R.layout.fragment_photo_gallery, container, false);

        mGridSizeArr = getActivity().getResources()
                .getIntArray(R.array.grid_integer_values);
        mStdColumns = mGridSizeArr[0];
        mPhotoRecyclerView = (RecyclerView) view
                .findViewById(R.id.fragment_photo_gallery_recycler_view);
        mManager = new GridLayoutExtended(getActivity(), mStdColumns);
        mPhotoRecyclerView.setLayoutManager(mManager);
        mPhotoRecyclerView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mPhotoRecyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        mScreenWidthDp = mPhotoRecyclerView.getMeasuredWidth();
                        mColumnWidth = getActivity().getResources()
                                .getInteger(R.integer.grid_width_value);
                        mNoOfGrids = mScreenWidthDp / mColumnWidth;
                        if (mNoOfGrids != mStdColumns) {
                            GridLayoutManager layoutManager = (GridLayoutManager)
                                    mPhotoRecyclerView.getLayoutManager();
                            if(mNoOfGrids > mGridSizeArr[2])
                                mNoOfGrids = mGridSizeArr[2];
                            layoutManager.setSpanCount(mNoOfGrids);
                        }
                    }
                });

        mProgressBar = (ProgressBar) view.findViewById(R.id.progressBar1);
        mProgressBar.setIndeterminate(true);

        showProgressBar(true);
        setUpAdapter();

        return view;
    }


    public void onDestroy(){
        super.onDestroy();
        mThumbnailDownloader.quit();
        Log.i(TAG, "Background thread destroyed");
    }



    public void onDestroyView(){
        super.onDestroyView();
        mThumbnailDownloader.clearQueue();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data){
        if(resultCode != Activity.RESULT_OK)
            return;

    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater menuInflater){
        super.onCreateOptionsMenu(menu, menuInflater);
        menuInflater.inflate(R.menu.fragment_photo_gallery, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_item_search);
        final android.support.v7.widget.SearchView searchView = (android.support.v7.widget.SearchView) searchItem.getActionView();

        searchView.setOnQueryTextListener(new android.support.v7.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.d(TAG, "QueryTextSubmitted: " + query);
                QueryPreferences.setStoredQuery(getActivity(), query);
                searchView.onActionViewCollapsed();
                updateItems();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.d(TAG, "QueryTextChanged: " + newText);
                return false;
            }
        });

        searchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String query = QueryPreferences.getStoredQuery(getActivity());
                searchView.setQuery(query, false);
            }
        });

        MenuItem toggleItem = menu.findItem(R.id.menu_item_toggle_polling);
        if(PollService.isServiceAlarmOn(getActivity())){
            toggleItem.setTitle(R.string.stop_polling);
        }else {
            toggleItem.setTitle(R.string.start_polling);
        }
    }

    public boolean onOptionsItemSelected(MenuItem item){
        switch (item.getItemId()){
            case R.id.menu_item_clear:
                QueryPreferences.setStoredQuery(getActivity(), null);
                updateItems();
                return true;
            case R.id.menu_item_toggle_polling:
                boolean shouldStartAlarm = !PollService.isServiceAlarmOn(getActivity());
                PollService.setServiceAlarm(getActivity(), shouldStartAlarm);
                getActivity().invalidateOptionsMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void updateItems() {
        String query = QueryPreferences.getStoredQuery(getActivity());
        if(mPhotoRecyclerView != null){
            mItems.clear();
            mPhotoRecyclerView.getAdapter().notifyDataSetChanged();
        }
        new FetchItemsTask(query, this).execute();
    }

    private void setUpAdapter() {
        if(isAdded()){
            mPhotoRecyclerView.setAdapter(new PhotoAdapter(mItems));
        }
    }

    private class PhotoHolder extends RecyclerView.ViewHolder{

        private ImageView mItemImageView;

        public PhotoHolder(View itemView) {
            super(itemView);

            mItemImageView = (ImageView) itemView
                    .findViewById(R.id.fragment_photo_gallery_image_view);
        }

        public void bindGalleryItem(Drawable drawable){
            mItemImageView.setImageDrawable(drawable);
        }
    }

    private class PhotoAdapter extends RecyclerView.Adapter<PhotoHolder>{

        private List<GalleryItem> mGalleryItems;
        //private int lastBoundViewPosition;

        //public int getLastBoundViewPosition() {
          //  return lastBoundViewPosition;
        //}

        public PhotoAdapter(List<GalleryItem> galleryItems){
            mGalleryItems = galleryItems;
        }
        @Override
        public PhotoHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(getActivity());
            View view = inflater.inflate(R.layout.gallery_item, parent, false);
            mManager.setExtraLayoutSpace(view.getHeight());
            return new PhotoHolder(view);
        }

        @Override
        public void onBindViewHolder(PhotoHolder holder, int position) {

            GalleryItem galleryItem = mGalleryItems.get(position);
            Drawable placeHolder = ContextCompat.getDrawable(getActivity(), R.drawable.bill_up_close);
            holder.bindGalleryItem(placeHolder);
            mThumbnailDownloader.queueThumbnail(holder, galleryItem.getUrl());
        }

        @Override
        public int getItemCount() {
            return mGalleryItems.size();
        }

    }

    private class FetchItemsTask extends AsyncTask</*Integer*/Void, Void, List<GalleryItem>> {

        private String mQuery;
        private PhotoGalleryFragment mGalleryFragment;

        public FetchItemsTask(String query, PhotoGalleryFragment fragment){
            mQuery = query;
            mGalleryFragment = fragment;
        }

        @Override
        protected List<GalleryItem> doInBackground(Void... params) {

            if(mQuery == null)
                return new FlickrFetchr().fetchRecentPhotos();
            return new FlickrFetchr().searchPhotos(mQuery);
        }

        protected void onPreExecute(){
            super.onPreExecute();
            if(mGalleryFragment.isResumed())
                mGalleryFragment.showProgressBar(true);
        }

        protected void onPostExecute(List<GalleryItem> items){

            mGalleryFragment.showProgressBar(false);
                mItems = items;
                setUpAdapter();
        }
    }

    public void showProgressBar(boolean isShow){
        if(isShow){
            mProgressBar.setVisibility(View.VISIBLE);
            mPhotoRecyclerView.setVisibility(View.INVISIBLE);
        }else {
            mProgressBar.setVisibility(View.INVISIBLE);
            mPhotoRecyclerView.setVisibility(View.VISIBLE);
        }
    }

}
