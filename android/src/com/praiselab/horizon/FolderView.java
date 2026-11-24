package com.praiselab.horizon;

import android.graphics.Rect;

import java.util.List;

/** An open folder (pop-up or in place) that apps can be dragged around inside. All points are in the drag layer's coordinates. */
interface FolderView {
    /** The folder's card, where a drop rearranges instead of leaving the folder. */
    Rect cardRect();
    /** The slot under the finger, or -1 between slots (the title, the padding). */
    int slotAt(float x, float y);
    /** Lays the apps out in this order, leaving the dragged one's spot empty (hidden = its id, or -1). */
    void showOrder(List<Item> order, long hidden);
    /** Where slot n sits, for the drop animation. */
    Rect slotRect(int n);
}
