package dev.qinglan.browser

import android.content.ClipData
import android.view.*
import android.widget.ScrollView
import kotlin.math.hypot

/** Local drags never export URLs or data to another app. */
object DragSupport {
    data class Item(val kind:String,val id:String)
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    fun source(view:View,item:Item,hold:Boolean=true,released:()->Unit={}) {
        var x=0f;var y=0f;var armed=false;var dragging=false;var moved=false
        val slop=ViewConfiguration.get(view.context).scaledTouchSlop
        val arm=Runnable {armed=true;view.isPressed=true;view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);view.parent?.requestDisallowInterceptTouchEvent(true)}
        view.setOnTouchListener {v,e->
            when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{x=e.x;y=e.y;armed=!hold;dragging=false;moved=false;if(hold)v.postDelayed(arm,ViewConfiguration.getLongPressTimeout().toLong())else v.parent?.requestDisallowInterceptTouchEvent(true);true}
                MotionEvent.ACTION_MOVE->{if(hypot(e.x-x,e.y-y)>slop){moved=true;if(armed&&!dragging){dragging=v.startDragAndDrop(ClipData.newPlainText(tr("清岚项目"),""),View.DragShadowBuilder(v),item,0);v.isPressed=false}else if(!armed)v.removeCallbacks(arm)};true}
                MotionEvent.ACTION_UP->{v.removeCallbacks(arm);v.isPressed=false;v.parent?.requestDisallowInterceptTouchEvent(false);if(!dragging){if(armed&&hold)released()else if(!moved)v.performClick()};armed=false;true}
                MotionEvent.ACTION_CANCEL->{v.removeCallbacks(arm);v.isPressed=false;armed=false;true}
                else->true
            }
        }
    }
    fun target(view:View,kind:String,accept:(Item)->Boolean={true},drop:(Item,Float,Float)->Unit){
        view.setOnDragListener{v,e->val item=e.localState as? Item
            if(item==null||item.kind!=kind||!accept(item))false else {
                when(e.action){
                    DragEvent.ACTION_DRAG_ENTERED->v.alpha=.55f
                    DragEvent.ACTION_DRAG_LOCATION->autoScroll(v,e.y)
                    DragEvent.ACTION_DRAG_EXITED,DragEvent.ACTION_DRAG_ENDED->v.alpha=1f
                    DragEvent.ACTION_DROP->{v.alpha=1f;drop(item,e.x,e.y)}
                };true
            }
        }
    }
    private fun autoScroll(v:View,y:Float){var parent=v.parent;while(parent is View){if(parent is ScrollView){val pos=IntArray(2);val outer=IntArray(2);v.getLocationOnScreen(pos);parent.getLocationOnScreen(outer);val local=pos[1]+y-outer[1];val edge=56*v.resources.displayMetrics.density;if(local<edge)parent.smoothScrollBy(0,-48)else if(local>parent.height-edge)parent.smoothScrollBy(0,48);return};parent=parent.parent}}
}
