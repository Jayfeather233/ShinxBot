package function.mysd;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import httpconnect.HttpURLConnectionUtil;
import interfaces.Processable;
import main.Main;

import java.util.Objects;
import java.util.UnknownFormatConversionException;

import static main.Main.localPath;
import static main.Main.setNextLog;
import static utils.saveImg.saveBase64Img;

public class sdMain implements Processable {
    private final JSONArray pattern = JSONArray.parseArray("""
            [
            "",
            50,
            "k_euler_a",
            [
            "Normalize Prompt Weights (ensure sum of weights add up to 1.0)",
            "Save individual images",
            "Save grid",
            "Sort samples by prompt",
            "Write sample info files"
            ],
            "RealESRGAN_x4plus",
            0,
            2,
            2,
            7.5,
            "",
            448,320,
            null,
            0,
            "",
            "",
            false,false,false,3
            ]
            """);

    public sdMain() {
    }

    @Override
    public void process(String message_type, String message, long group_id, long user_id, int message_id) {
        message = message.replace("\r","");
        message = message.replace("\n","");
        if (message.equals("sd.help")) {
            Main.setNextSender(message_type, user_id, group_id,
                    """
                            Stable Diffusion:
                                sd prompt:xxx H:xxx W:xxx CF:xxx STEP:xxx
                            prompt: 提示词
                            H: 图片高度，默认256 [50,512]
                            W: 图片宽度，默认256 [50,512]
                            CF: 多大依赖提示词，默认7.5 [2,10]
                            STEP: 生成步骤数，默认50 [0,150]
                            """);
            return;
        }
        String[] ps = message.substring(2).split(" ");
        StringBuilder prompt = new StringBuilder();
        boolean flg = false;
        int H = 256, W = 256, step = 50;
        double CF = 7.5;
        try {
            for (String s : ps) {
                if (s.startsWith("prompt:")) {
                    prompt.append(s.substring(7));
                    flg = true;
                } else if (s.startsWith("H:")) {
                    H = Integer.parseInt(s.substring(2));
                } else if (s.startsWith("W:")) {
                    W = Integer.parseInt(s.substring(2));
                } else if (s.startsWith("STEP:")) {
                    step = Integer.parseInt(s.substring(5));
                } else if (s.startsWith("CF:")) {
                    CF = Double.parseDouble(s.substring(3));
                } else if (s.length() > 0) prompt.append(' ').append(s);
            }
        } catch (NumberFormatException e) {
            Main.setNextSender(message_type, user_id, group_id, "参数错误：不是数字");
            return;
        }
        if(!flg){
            return;
        }
        if (H < 50 || W < 10) {
            Main.setNextSender(message_type, user_id, group_id, "参数错误：图片太小");
            return;
        }
        if (H > 512 || W > 512) {
            Main.setNextSender(message_type, user_id, group_id, "参数错误：图片太大");
            return;
        }
        if (CF < 2 || CF > 10) {
            Main.setNextSender(message_type, user_id, group_id, "参数错误：CF");
            return;
        }
        if (step < 1 || step > 150) {
            Main.setNextSender(message_type, user_id, group_id, "参数错误：STEP");
            return;
        }
        JSONArray ja = new JSONArray(pattern);
        ja.set(0, prompt);
        ja.set(1, step);
        ja.set(8, CF);
        ja.set(10, H);
        ja.set(11, W);
        String hashCodex = String.valueOf(Math.abs(ja.hashCode()));
        ja.set(15, hashCodex);
        JSONArray hashCode = new JSONArray();
        hashCode.set(0, hashCodex);

        Main.setNextSender(message_type,user_id,group_id,"正在生成中...");
        JSONObject J = new JSONObject();
        J.put("data", ja);
        J.put("fn_index", 14);
        J.put("session_hash", String.valueOf(J.hashCode()));
        String rs = String.valueOf(HttpURLConnectionUtil.doPost("http://localhost:7860/api/txt2img/", J));
        if(Objects.equals(rs, "null")){
            Main.setNextSender(message_type,user_id,group_id,"程序在重启或已关闭。");
        }
        J.put("data", hashCode);
        J.put("fn_index", 13);
        HttpURLConnectionUtil.doPost("http://localhost:7860/api/txt2img/", J);
        J.put("fn_index", 12);
        JSONObject mid = JSONObject.parseObject(Objects.requireNonNull(HttpURLConnectionUtil.doPost("http://localhost:7860/api/txt2img/", J)).toString());
        try{
            Main.setNextSender(message_type,user_id,group_id,(mid.getJSONArray("data").getString(2)+"%").formatted());
        } catch (UnknownFormatConversionException e){
            Main.setNextSender(message_type,user_id,group_id,(mid.getJSONArray("data").getString(2)).formatted());
        }
        J.put("fn_index", 11);
        HttpURLConnectionUtil.doPost("http://localhost:7860/api/txt2img/", J);
        J.put("fn_index", 4);
        J = JSONObject.parseObject(
                Objects.requireNonNull(
                        HttpURLConnectionUtil.doPost("http://localhost:7860/api/txt2img", J)).toString());
        ja = J.getJSONArray("data");

        StringBuilder sb = new StringBuilder();

        for (JSONArray jaa : ja.toJavaList(JSONArray.class)) {
            for (String s : jaa.toJavaList(String.class)) {
                sb.append("[CQ:image,file=file:///").append(localPath).append(saveBase64Img(s)).append(",id=40000]\n");
            }
        }

        setNextLog("Stable Diffusion at group " + group_id + " by " + user_id + " input: " + message, 0);
        Main.setNextSender(message_type, user_id, group_id, sb.toString());
    }

    @Override
    public boolean check(String message_type, String message, long group_id, long user_id) {
        return message.startsWith("sd") /*&& (group_id == 1011383394 || user_id == 1826559889)*/;
    }

    @Override
    public String help() {
        return "Stable Diffusion: sd.help";
    }
}
