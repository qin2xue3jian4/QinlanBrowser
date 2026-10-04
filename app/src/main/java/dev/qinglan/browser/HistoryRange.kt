package dev.qinglan.browser

object HistoryRange {
    val names get()=arrayOf(tr("所有时间"),tr("最近 1 小时"),tr("今天"),tr("最近 7 天"))
    fun start(index:Int,now:Long=System.currentTimeMillis()):Long=when(index){
        1->now-60*60*1000L
        2->java.util.Calendar.getInstance().apply{timeInMillis=now;set(java.util.Calendar.HOUR_OF_DAY,0);set(java.util.Calendar.MINUTE,0);set(java.util.Calendar.SECOND,0);set(java.util.Calendar.MILLISECOND,0)}.timeInMillis
        3->now-7*24*60*60*1000L
        else->Long.MIN_VALUE
    }
}
