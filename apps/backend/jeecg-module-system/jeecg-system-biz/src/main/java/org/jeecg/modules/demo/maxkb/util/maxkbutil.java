package org.jeecg.modules.demo.maxkb.util;

import com.hikvision.artemis.sdk.ArtemisHttpUtil;
import com.hikvision.artemis.sdk.config.ArtemisConfig;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

/**
 * @author wggg
 * @date 2024/5/30 16:30
 */
@Slf4j
public class maxkbutil {
    static {
        ArtemisConfig.host = System.getenv().getOrDefault("WGAI_MAXKB_HOST", "");
        ArtemisConfig.appKey = System.getenv().getOrDefault("WGAI_MAXKB_APP_KEY", "");
        ArtemisConfig.appSecret = System.getenv().getOrDefault("WGAI_MAXKB_APP_SECRET", "");
    }

    /***
     * 获取当前模型内容
     */
    public static String getModelInfo(String pathUrl,String apikey){
        ArtemisConfig.host=pathUrl;
        final String getRegionsList =  "/api/application/profile";
        log.debug("Requesting MaxKB model information from a configured endpoint");
        Map<String, String> path = new HashMap<String, String>(2) {
            {
                put("http://", getRegionsList);
            }
        };
        //header
        Map<String, String> header = new HashMap<String, String>(2) {
            {
                put("Authorization", apikey);
            }
        };
        //定义提交类型
        String contentType = "application/json";
        //参数装换
        //参数装换
        Map<String, String> querys = new HashMap<String, String>(2) {
            {
            }
        };
        //传参调用方法
        String result = ArtemisHttpUtil.doGetArtemis(path, null, null, contentType, header);
        log.info("返回内容:{}",result);
        return  result;
    }

}
