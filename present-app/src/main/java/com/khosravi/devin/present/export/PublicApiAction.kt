package com.khosravi.devin.present.export

sealed class PublicApiAction {
    /**
     * adb shell am start -n com.khosravi.devin.present/com.khosravi.devin.present.present.StarterActivity \
     * --es exportUrl http://10.0.2.2/mysite/devin_file_receiver.php \
     * --es clientId com.khosravi.sample.devin
     */
    class ExportOnlyHttp(val clientId: String, val exportUrl: String): PublicApiAction()
}