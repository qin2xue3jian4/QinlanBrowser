package dev.qinglan.browser

import org.json.JSONArray
import org.json.JSONObject

data class SavedPassword(val url:String,val username:String,val password:String,val name:String="") {
    val origin get()=PasswordCodec.origin(url)
    fun json()=JSONObject().put("url",url).put("username",username).put("password",password).put("name",name)
}
object PasswordCodec {
    fun origin(url:String):String=runCatching{val u=java.net.URI(url);require(u.scheme=="https"&&!u.host.isNullOrBlank()&&u.userInfo==null);"https://${u.host.lowercase()}${if(u.port in listOf(-1,443))""else ":${u.port}"}"}.getOrDefault("")
    fun validate(e:SavedPassword){require(origin(e.url).isNotEmpty()&&e.url.length<=8192){"密码仅支持 HTTPS 网站"};require(e.username.length<=4096&&e.password.isNotEmpty()&&e.password.length<=16384&&e.name.length<=1000){"账号或密码格式无效"}}
    fun json(entries:List<SavedPassword>)=JSONArray().apply{entries.forEach{put(it.json())}}
    fun parseJson(a:JSONArray):List<SavedPassword>{require(a.length()<=3000){"密码条目过多"};return List(a.length()){i->val o=a.getJSONObject(i);SavedPassword(o.getString("url"),o.getString("username"),o.getString("password"),o.optString("name")).also(::validate)}}
}
