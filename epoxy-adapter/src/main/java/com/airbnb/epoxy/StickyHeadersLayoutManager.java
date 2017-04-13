package com.airbnb.epoxy;

import android.content.Context;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.LayoutParams;
import android.support.v7.widget.RecyclerView.Recycler;
import android.support.v7.widget.RecyclerView.State;
import android.view.View;

public class StickyHeadersLayoutManager extends LinearLayoutManager {

  private RecyclerView recyclerView;
  private StickyRecyclerView parent;
  private Recycler recycler;
  private HeaderData currentHeader;

  public StickyHeadersLayoutManager(Context context) {
    super(context);
  }

  @Override
  public void onAttachedToWindow(RecyclerView view) {
    super.onAttachedToWindow(view);
    recyclerView = view;
    parent = (StickyRecyclerView) recyclerView.getParent();
  }

  class HeaderData {

    View headerView;
    EpoxyModel<?> model;
    LayoutParams layoutParams;

    public HeaderData(View headerView, EpoxyModel<?> model,
        LayoutParams layoutParams) {
      this.headerView = headerView;
      this.model = model;
      this.layoutParams = layoutParams;
    }
  }

  @Override
  public void onLayoutChildren(Recycler recycler, State state) {
    super.onLayoutChildren(recycler, state);
    this.recycler = recycler;

    addHeaderIfCan(recycler);
  }

  private void addHeaderIfCan(Recycler recycler) {

    if (recyclerView.getChildCount() == 0 || currentHeader != null) {
      return;
    }

    for (int i = 0; i < recyclerView.getChildCount(); i++) {

      View view = recyclerView.getChildAt(i);
      EpoxyViewHolder holder = (EpoxyViewHolder)
          recyclerView.getChildViewHolder(view);

      EpoxyModel<?> model = holder.getModel();

      //If model is sticky &&
      if (view.getTop() <= 0) {
        View headerView = recycler.getViewForPosition(holder.getAdapterPosition());
        currentHeader = new HeaderData(headerView, model, (LayoutParams) headerView.getLayoutParams());
        parent.setHeader(currentHeader.headerView);
        return;
      }
    }
  }

  private void removeCurrentHeader() {
    parent.removeView(currentHeader.headerView);

    currentHeader.headerView.setLayoutParams(currentHeader.layoutParams);
    recycler.recycleView(currentHeader.headerView);
    currentHeader = null;
  }

  @Override
  public int scrollVerticallyBy(int dy, Recycler recycler, State state) {
    int i = super.scrollVerticallyBy(dy, recycler, state);

    addHeaderIfCan(recycler);
    return i;
  }
}
