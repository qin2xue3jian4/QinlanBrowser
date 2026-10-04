package dev.qinglan.browser

data class ClosedTab(val url:String,val title:String,val index:Int,val openerId:Long?,val searchOverride:String?,val accountId:String="")

/** URL metadata only: never keeps live WebViews, forms or screenshots. */
class ClosedTabs(private val clock:()->Long=System::currentTimeMillis) {
    private val entries=ArrayDeque<Pair<Long,ClosedTab>>()
    fun push(tab:ClosedTab){expire();entries.addLast(clock() to tab);while(entries.size>10)entries.removeFirst()}
    fun pop():ClosedTab?{expire();return if(entries.isEmpty())null else entries.removeLast().second}
    fun available():Boolean{expire();return entries.isNotEmpty()}
    fun removeAccount(id:String){entries.removeAll{it.second.accountId==id}}
    fun clear(){entries.clear()}
    private fun expire(){while(entries.isNotEmpty()&&clock()-entries.first().first>600_000)entries.removeFirst()}
}
