package com.android.codeflaunt.piyush.photogallery;

import android.content.Context;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;

/**
 * Created by Piyush on 23-Mar-16.
 */
public class GridLayoutExtended extends GridLayoutManager {

    private static final int DEFAULT_EXTRA_LAYOUT_SPACE = 300;
    private int mExtraLayoutSpace = -1;
    private Context mContext;

    public GridLayoutExtended(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, spanCount, orientation, reverseLayout);
        mContext = context;
    }

    public GridLayoutExtended(Context context, int spanCount) {
        super(context, spanCount);
        mContext = context;
    }

    public void setExtraLayoutSpace(int extraLayoutSpace){
        mExtraLayoutSpace = extraLayoutSpace;
    }

    @Override
    protected int getExtraLayoutSpace(RecyclerView.State state) {
        if(mExtraLayoutSpace > 0)
            return mExtraLayoutSpace;
        return DEFAULT_EXTRA_LAYOUT_SPACE;
    }
}
