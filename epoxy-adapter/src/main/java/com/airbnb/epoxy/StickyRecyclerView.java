package com.airbnb.epoxy;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.OnScrollListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class StickyRecyclerView extends FrameLayout{

  private RecyclerView recyclerView;

  public StickyRecyclerView(@NonNull Context context) {
    super(context);
    init();
  }

  private void init() {
    LayoutInflater.from(getContext()).inflate(1, this, true);
    recyclerView.setLayoutManager(new StickyHeadersLayoutManager(getContext()));

    recyclerView.addOnScrollListener(new OnScrollListener() {
      @Override
      public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
        super.onScrollStateChanged(recyclerView, newState);
      }

      @Override
      public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
        super.onScrolled(recyclerView, dx, dy);
      }
    });
  }

  public void setHeader(View headerView) {

    ViewGroup.LayoutParams newLayoutParams = generateLayoutParams(
        headerView.getLayoutParams());
    headerView.setLayoutParams(newLayoutParams);
    addView(headerView);
  }

}
