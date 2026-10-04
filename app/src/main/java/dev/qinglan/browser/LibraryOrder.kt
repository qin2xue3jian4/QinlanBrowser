package dev.qinglan.browser

object LibraryOrder {
    fun home(items:MutableList<HomeItem>,id:String,parent:String,target:String?=null,after:Boolean=false){
        val entry=items.find{it.id==id}?:return
        require(parent.isEmpty()||(!entry.folder&&items.any{it.id==parent&&it.folder})){tr("不能嵌套文件夹")}
        if(target==id)return
        val at=target?.let{t->items.find{it.id==t&&it.parent==parent}}
        items.remove(entry);entry.parent=parent
        val index=if(at!=null)items.indexOf(at)+(if(after)1 else 0)else (items.indexOfLast{it.parent==parent}+1).let{if(it==0)items.size else it}
        items.add(index,entry)
    }
    fun bookmarks(items:MutableList<Visit>,ids:Set<String>,parent:String,target:String?=null,after:Boolean=false){
        val moving=items.filter{it.id in ids};if(moving.isEmpty()||target in ids)return
        require(parent.isEmpty()||(moving.none{it.folder}&&items.any{it.id==parent&&it.folder})){tr("文件夹只能位于根目录")}
        val at=target?.let{t->items.find{it.id==t&&it.parent==parent}}
        items.removeAll{it.id in ids}
        val index=if(at!=null)items.indexOf(at)+(if(after)1 else 0)else (items.indexOfLast{it.parent==parent}+1).let{if(it==0)items.size else it}
        items.addAll(index,moving.map{it.copy(parent=parent)})
    }
    fun deleteBookmarks(items:MutableList<Visit>,ids:Set<String>){val folders=items.filter{it.folder&&it.id in ids}.map{it.id}.toSet();items.removeAll{it.id in ids};items.indices.forEach{i->if(items[i].parent in folders)items[i]=items[i].copy(parent="")}}
}
