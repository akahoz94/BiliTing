package com.tingbili.app.data.api

import org.junit.Test

/** 扫码登录绝不能动用户已粘贴 cookie 的非登录字段（用户明确要求保留） */
class LoginApiMergeTest {

    @Test
    fun 登录字段覆盖_其他字段原样保留() {
        val existing = "SESSDATA=old; bili_jct=old; DedeUserID=111; buvid3=ABCinfoc; myflag=1"
        val login = "SESSDATA=new; bili_jct=new; DedeUserID=222; DedeUserID__ckMd5=md5; sid=xyz"
        val merged = LoginApi.mergeCookies(existing, login)
        assert(merged.contains("SESSDATA=new")) { merged }
        assert(merged.contains("bili_jct=new")) { merged }
        assert(merged.contains("DedeUserID=222")) { merged }
        assert(merged.contains("DedeUserID__ckMd5=md5")) { merged }
        assert(merged.contains("sid=xyz")) { merged }
        assert(merged.contains("buvid3=ABCinfoc")) { merged }
        assert(merged.contains("myflag=1")) { merged }
        assert(!merged.contains("SESSDATA=old")) { merged }
    }

    @Test
    fun 已有为空时直接采用登录结果() {
        assert(LoginApi.mergeCookies("", "SESSDATA=n") == "SESSDATA=n")
    }

    @Test
    fun 大小写不敏感覆盖登录键() {
        val merged = LoginApi.mergeCookies("sessdata=old; keep=1", "SESSDATA=new")
        assert(merged.contains("SESSDATA=new")) { merged }
        assert(!merged.contains("old")) { merged }
        assert(merged.contains("keep=1")) { merged }
    }

    @Test
    fun 只挑登录字段_web端的buvid不混进来() {
        val jar = "buvid3=ABC; SESSDATA=s; bili_jct=j; DedeUserID=1; sid=x; buvid4=DEF"
        val only = LoginApi.loginFieldsOnly(jar)
        assert(only.contains("SESSDATA=s")) { only }
        assert(only.contains("DedeUserID=1")) { only }
        assert(!only.contains("buvid3")) { only }
        assert(!only.contains("buvid4")) { only }
    }
}
